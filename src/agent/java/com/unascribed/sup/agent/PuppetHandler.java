/*
 * This file is part of unsup.
 * Copyright © 2023-2025 Una Kearney
 * https://git.sleeping.town/unascribed/unsup
 *
 * unsup is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * unsup is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with unsup; if not, see <https://www.gnu.org/licenses/>.
 */

package com.unascribed.sup.agent;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.Character.UnicodeScript;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.PlatDetect;
import com.unascribed.sup.PlatDetect.ArchType;
import com.unascribed.sup.PlatDetect.OSType;
import com.unascribed.sup.Util;
import com.unascribed.sup.agent.util.RequestHelper;
import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.SysProps;
import com.unascribed.sup.data.SysProps.PuppetMode;
import com.unascribed.sup.pieces.Latch;

public class PuppetHandler {
	
	public static Process puppet;
	public static OutputStream puppetOut;
	
	private static String title;
	private static int lastReportedProgress = 0;
	private static long lastReportedProgressTime = 0;
	
	private static final Map<String, String> alertResults = new HashMap<>();
	private static final Map<String, Latch> alertWaiters = new HashMap<>();
	
	private static final int crashId = ThreadLocalRandom.current().nextInt()&Integer.MAX_VALUE;
	
	private static final Pattern IMK_CLIENT = Pattern.compile(" \\+\\[IMKClient subclass\\]: chose IMKClient_(Modern|Legacy)$");
	
	public enum AlertOptionType { OK, OK_CANCEL, YES_NO, YES_NO_CANCEL, YES_NO_TO_ALL_CANCEL }
	public enum AlertOption { CLOSED, OK, YES, NO, CANCEL, YESTOALL, NOTOALL }

	private static final String bundleVersion = "3.4.0+7";
	
	private static final String[] copyableProps = {
		"javax.accessibility.assistive_technologies",
		"assistive_technologies",
		"unsup.puppetMode",
		"unsup.puppet.opengl.platform",
		"sun.java2d.uiScale",
		"unsup.scale"
	};
	
	public static void destroy() {
		puppet.destroy();
	}
	
	public static void create() {
		out: {
			URI uri;
			try {
				uri = Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			} catch (URISyntaxException e) {
				Log.warn("Cannot summon Puppet: Failed to find our own JAR file or directory.");
				puppet = null;
				puppetCrashed();
				break out;
			}
			File ourPath;
			try {
				ourPath = new File(uri);
			} catch (IllegalArgumentException e) {
				Log.warn("Cannot summon Puppet: Failed to find our own JAR file or directory.");
				puppet = null;
				puppetCrashed();
				break out;
			}
			File javaBin = new File(System.getProperty("java.home")+File.separator+"bin");
			String[] executableNames = {
					"javaw.exe",
					"java.exe",
					"javaw",
					"java"
			};
			String java = null;
			for (String exe : executableNames) {
				File f = new File(javaBin, exe);
				if (f.exists()) {
					java = f.getAbsolutePath();
					break;
				}
			}
			if (java == null) {
				Log.warn("Cannot summon Puppet: Failed to find Java. Looked in "+javaBin+", but can't find a known executable.");
				puppet = null;
				puppetCrashed();
			} else {
				Process p;
				try {
					List<String> args = new ArrayList<>();
					if (SysProps.PUPPET_WRAPPER_COMMAND != null) {
						args.add(SysProps.PUPPET_WRAPPER_COMMAND);
					}
					args.add(java);
					args.add("-XX:+UseG1GC");
					args.add("-Xms1M");
					args.add("-Xmx128M");
					args.add("-XX:+IgnoreUnrecognizedVMOptions");
					args.add("-XX:+UnlockDiagnosticVMOptions");
					args.add("-Djbr.catch.SIGABRT=true");
					for (String prop : copyableProps) {
						String v = System.getProperty(prop);
						if ("unsup.puppetMode".equals(prop) && v == null) {
							v = Agent.config().puppetMode().name();
						}
						if (v != null) {
							args.add("-D"+prop+"="+v);
						}
					}
					if (SysProps.PUPPET_PASS_ALL_LWJGL_ARGS) {
						for (String k : System.getProperties().stringPropertyNames()) {
							if (k.startsWith("org.lwjgl.")) {
								args.add("-D"+k+"="+System.getProperty(k));
							}
						}
					}
					List<String> cp = new ArrayList<>();
					cp.add(ourPath.getAbsolutePath());
					if (SysProps.PUPPET_MODE != PuppetMode.SWING) {
						if (PlatDetect.OS == OSType.UNSUPPORTED || PlatDetect.ARCH == ArchType.UNSUPPORTED
								|| !PlatDetect.OS.supportedArchitectures.contains(PlatDetect.ARCH)) {
							Log.error("Unrecognized platform, falling back to Swing puppet (use -Dunsup.puppetMode=swing to enforce this behavior)");
							args.add("-Dunsup.puppetMode=swing");
						} else {
							if (PlatDetect.OS == OSType.MACOS) {
								args.add("-XstartOnFirstThread");
							}
							if (!Util.DEVELOPMENT_ENVIRONMENT) {
								String os = PlatDetect.OS.lwjglName;
								String arch = PlatDetect.ARCH.apiName;
								Log.debug("Retrieving assets for "+os+"-"+arch+"...");
								File cacheDir = PlatDetect.OS.cacheDirGetter.get();
								ExecutorService svc = Executors.newFixedThreadPool(6);
								List<Future<File>> futures = new ArrayList<>();
								try {
									futures.add(svc.submit(() -> {
										return obtainAsset(cacheDir, "bundles/"+bundleVersion+"/"+os+"-"+arch);
									}));
									boolean needCjk = false;
									for (var v : Agent.config().strings().values()) {
										if (v.codePoints().anyMatch(codepoint -> {
											UnicodeScript sc = UnicodeScript.of(codepoint);
											// I think this is all of them??
											return sc == UnicodeScript.HANGUL || sc == UnicodeScript.HAN || sc == UnicodeScript.KATAKANA
													|| sc == UnicodeScript.HIRAGANA || sc == UnicodeScript.BOPOMOFO;
										})) {
											needCjk = true;
											break;
										}
									}
									if (needCjk) {
										Log.debug("Retrieving CJK support...");
										futures.add(svc.submit(() -> {
											return obtainAsset(cacheDir, "CJKSupport");
										}));
									}
									svc.shutdown();
									List<String> addnCp = new ArrayList<>();
									for (Future<File> f : futures) {
										addnCp.add(f.get().getAbsolutePath());
									}
									cp.addAll(addnCp);
								} catch (ExecutionException e) {
									Log.error("Failed to load assets for OpenGL puppet, falling back to Swing puppet (use -Dunsup.puppetMode=swing to enforce this behavior)", e);
									args.add("-Dunsup.puppetMode=swing");
								}
							}
						}
					}
					if (Util.DEVELOPMENT_ENVIRONMENT) {
						for (String s : System.getProperty("java.class.path").split(File.pathSeparator)) {
							cp.add(s);
						}
					}
					StringJoiner cpJ = new StringJoiner(File.pathSeparator);
					cp.forEach(cpJ::add);
					args.add("-cp");
					args.add(cpJ.toString());
					File errorFile = determineErrorFilePath();
					args.add("-XX:ErrorFile="+errorFile.getAbsolutePath());
					args.add("-XX:+ErrorLogSecondaryErrorDetails");
					args.add("com.unascribed.sup.puppet.Puppet");
					
					StringJoiner printJ = new StringJoiner("' '", "'", "'");
					for (String s : args) {
						printJ.add(s.replace("'", "\\'"));
					}
					
					Log.debug("unsup location detected as "+ourPath);
					Log.debug("Java location detected as "+java);
					Log.debug("Puppet command: "+printJ);
					
					ProcessBuilder bldr = new ProcessBuilder(args);
					bldr.environment().put("_JAVA_AWT_WM_NONREPARENTING", "1");
					bldr.environment().put("NO_AWT_MITSHM", "1");
					bldr.environment().put("__GL_THREADED_OPTIMIZATIONS", "0");
					p = bldr.start();
				} catch (Throwable t) {
					Log.warn("Failed to summon a puppet.", t);
					puppet = null;
					puppetCrashed();
					break out;
				}
				Log.debug("Dark spell successful. Puppet summoned.");
				puppet = p;
			}
		}
		if (puppet == null) {
			Log.warn("Failed to summon a Puppet. Continuing without a GUI.");
		} else {
			Thread puppetErr = new Thread(() -> {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(puppet.getErrorStream(), StandardCharsets.UTF_8))) {
					while (true) {
						String line = br.readLine();
						if (line == null) return;
						Matcher imk = IMK_CLIENT.matcher(line);
						if (imk.find()) {
							// sigh
							Log.log("DEBUG", "puppet", "macOS chose the "+imk.group(1)+" IMKClient implementation");
						} else if (line.contains("|")) {
							int idx = line.indexOf('|');
							Log.log(line.substring(0, idx), "puppet", line.substring(idx+1));
						} else {
							Log.puppetStderr(line);
						}
					}
				} catch (IOException e) {}
			}, "unsup puppet error printer");
			puppetErr.setDaemon(true);
			puppetErr.start();
			Log.debug("Waiting for the Puppet to come to life...");
			BufferedReader br = new BufferedReader(new InputStreamReader(puppet.getInputStream(), StandardCharsets.UTF_8));
			Throwable t = null;
			String firstLine = null;
			try {
				firstLine = br.readLine();
			} catch (IOException e) {
				t = e;
			}
			if (firstLine == null) {
				Log.warn("Puppet failed to come alive. Continuing without a GUI.", t);
				puppet.destroy();
				puppet = null;
				puppetCrashed();
			} else if (!"unsup puppet ready".equals(firstLine)) {
				Log.warn("Puppet sent unexpected hello line \""+firstLine+"\". (Expected \"unsup puppet ready\") Continuing without a GUI.");
				puppet.destroy();
				puppet = null;
				puppetCrashed();
			} else {
				Log.debug("Puppet is alive! Continuing.");
				puppetOut = new BufferedOutputStream(puppet.getOutputStream(), 512);
				Thread puppetThread = new Thread(() -> {
					try (BufferedReader br2 = br) {
						String eatenLine = null;
						int crashState = 0;
						while (true) {
							String line = br.readLine();
							if (line == null) return;
							if (line.equals("closeRequested")) {
								Log.info("User closed puppet window! Exiting...");
								exit();
							} else if (line.startsWith("alert:")) {
								String[] split = line.split(":", 3);
								String name = split[1];
								String opt = split.length > 2 ? split[2] : "";
								synchronized (alertResults) {
									alertResults.put(name, opt);
									Latch latch = alertWaiters.remove(name);
									if (latch != null) {
										latch.release();
									}
								}
							} else if (crashState == 0 && line.startsWith("#")) {
								crashState = 1;
								eatenLine = line;
							} else if (crashState == 1) {
								if (line.startsWith("# A fatal error has been detected by the Java Runtime Environment:")) {
									crashState = 2;
									puppetCrashed();
								} else {
									Log.warn("Unknown line from puppet: "+eatenLine);
									eatenLine = null;
									crashState = 0;
								}
							} else if (crashState != 2) {
								if (crashState == 1) crashState = 0;
								Log.warn("Unknown line from puppet: "+line);
							}
						}
					} catch (IOException | InterruptedException e) {}
				}, "unsup puppet out parser");
				puppetThread.setDaemon(true);
				puppetThread.start();
			}
		}
	}
	
	private static String determineErrorFileName() {
		return "unsup-puppet-native-crash-"+crashId+".log";
	}
	
	private static File determineErrorFilePath() {
		File logs = new File("logs");
		String n = determineErrorFileName();
		return logs.isDirectory() ? new File(logs, n) : new File(n);
	}

	private static void puppetCrashed() {
		File nativeCrash = determineErrorFilePath();
		if (nativeCrash.exists()) {
			Log.error("The Puppet crashed in native code. Please report this issue, including the full unsup.log and "+determineErrorFileName());
		}
		
		if (SysProps.ABORT_ON_PUPPET_CRASH) {
			Log.error("Puppet crashed! Exiting, as requested by -Dunsup.abortOnPuppetCrash=true!");
			try {
				exit();
			} catch (InterruptedException e) {
			}
		}
	}
	
	private static void exit() throws InterruptedException {
		Agent.awaitingExit = true;
		long start = System.nanoTime();
		synchronized (Agent.dangerMutex) {
			long diff = System.nanoTime()-start;
			if (diff > TimeUnit.MILLISECONDS.toNanos(500)) {
				Log.info("Uninterruptible operations finished, exiting!");
			}
			Process p = puppet;
			if (p != null) {
				p.destroy();
				if (!p.waitFor(1, TimeUnit.SECONDS)) {
					p.destroyForcibly();
				}
			}
			Agent.exit(Agent.EXIT_USER_REQUEST);
		}
	}

	private static File obtainAsset(File cacheDir, String url) throws IOException {
		final int M = 1024*1024;
		
		String fname = url.replace("/", "-");
		File cacheFile = new File(cacheDir, fname+".jar");
		File cacheFileTmp = new File(cacheDir, fname+".jar.tmp");
		boolean needsDownload = true;
		if (cacheFile.exists() && cacheFile.length() > 32) {
			needsDownload = false;
			Log.debug("Got "+fname+" from cache");
		}
		if (needsDownload) {
			Log.info("Downloading "+fname+" from unsup.y2k.diy...");
			String dlBase = "https://unsup.y2k.diy/assets/v1/"+url;
			URI dl = URI.create(dlBase+".jar.br");
			URI sig = URI.create(dlBase+".sig");
			Files.createDirectories(cacheDir.toPath());
			byte[] data = RequestHelper.loadAndVerify(dl, 64*M, sig, Agent.unsupSig);
			try (FileOutputStream fos = new FileOutputStream(cacheFileTmp);
					InputStream is = new BrotliInputStream(new ByteArrayInputStream(data))) {
				Util.copy(is, fos);
			}
			Files.deleteIfExists(cacheFile.toPath());
			Files.move(cacheFileTmp.toPath(), cacheFile.toPath());
			Log.debug(fname+" downloaded and saved to cache");
		}
		return cacheFile;
	}

	public static void sendConfig() {
		for (var en : Agent.config().colorChoices().entrySet()) {
			tellPuppet(":color="+en.getKey().name()+":"+en.getValue());
		}
		for (var en : Agent.config().strings().entrySet()) {
			tellPuppet(":string="+en.getKey()+":"+en.getValue());
		}
		Agent.config().modpackName().ifPresent(name -> {
			tellPuppet(":modpackName="+name);
		});
		Agent.config().brandingIcon().ifPresent(icon -> {
			tellPuppet(":icon="+icon);
		});
		tellPuppet(":flavorDialogGeom="+Agent.config().flavorDialogGeom());
		tellPuppet(":flavorDialogBias="+Agent.config().flavorDialogBias());
	}

	public static void tellPuppet(String order) {
		if (puppetOut == null) return;
		synchronized (puppetOut) {
			try {
				byte[] utf = order.getBytes(StandardCharsets.UTF_8);
				for (byte b : utf) {
					if (b == 0) {
						// replace NUL with overlong NUL
						puppetOut.write(0xC0);
						puppetOut.write(0x80);
					} else {
						puppetOut.write(b);
					}
				}
				puppetOut.write(0);
				puppetOut.flush();
			} catch (IOException e) {
				if (!Agent.awaitingExit) {
					Log.warn("IO error while talking to puppet. Killing and continuing without GUI.", e);
					puppetCrashed();
				}
				puppet.destroyForcibly();
				puppet = null;
				puppetOut = null;
				for (Latch l : alertWaiters.values()) {
					l.release();
				}
			}
		}
	}

	public static void updateTitle(String title, boolean determinate) {
		PuppetHandler.title = title;
		tellPuppet(":prog=0");
		tellPuppet(":mode="+(determinate ? "det" : "ind"));
		tellPuppet(":title="+title);
	}

	public static void updateSubtitle(String subtitle) {
		tellPuppet(":subtitle="+subtitle);
	}

	public static void updateSubtitleDownloading(String... files) {
		StringJoiner joiner = new StringJoiner("\u001C");
		for (String s : files) joiner.add(s);
		tellPuppet(":downloading="+joiner);
	}

	public static void updateProgress(int prog) {
		tellPuppet(":prog="+prog);
		if (Math.abs(lastReportedProgress-prog) >= 100 || System.nanoTime()-lastReportedProgressTime > TimeUnit.SECONDS.toNanos(3)) {
			lastReportedProgress = prog;
			lastReportedProgressTime = System.nanoTime();
			Log.info(Agent.config().strings().get(title)+" "+(prog/10)+"%");
		}
	}

	public static AlertOption openAlert(String title, String body, AlertMessageType messageType, AlertOptionType optionType, AlertOption def) {
		if (puppetOut == null) {
			return def;
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertResults.put(name, def.name().toLowerCase(Locale.ROOT));
			alertWaiters.put(name, latch);
			tellPuppet("["+name+"]:alert="+title+":"+body+":"+messageType.name().toLowerCase(Locale.ROOT)+":"+optionType.name().toLowerCase(Locale.ROOT).replace("_", "")+":"+def.name().toLowerCase(Locale.ROOT).replace("_", ""));
			latch.awaitUninterruptibly();
			return AlertOption.valueOf(alertResults.remove(name).replace("option.", "").replace("_", "").toUpperCase(Locale.ROOT));
		}
	}

	public static String openChoiceAlert(String title, String body, Collection<String> choices, String def) {
		if (puppetOut == null) {
			return def;
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			alertResults.put(name, def);
			alertWaiters.put(name, latch);
			StringJoiner joiner = new StringJoiner("\u001C");
			choices.forEach(c -> joiner.add(c.replace(':', '\u001B')));
			tellPuppet("["+name+"]:alert="+title+":"+body+":choice="+joiner+":"+def);
			latch.awaitUninterruptibly();
			return alertResults.remove(name);
		}
	}
	
	public static List<String> openFlavorSelectDialog(String title, String body, List<FlavorGroup> groups) {
		if (puppetOut == null) {
			return new ArrayList<>();
		} else {
			String name = Long.toString(ThreadLocalRandom.current().nextLong()&Long.MAX_VALUE, 36);
			Latch latch = new Latch();
			StringJoiner defaultJoiner = new StringJoiner("\u001C");
			StringJoiner groupJoiner = new StringJoiner("\u001D");
			for (FlavorGroup group : groups) {
				StringJoiner joiner = new StringJoiner("\u001C");
				joiner.add(group.id());
				joiner.add(group.name());
				joiner.add(group.description());
				for (var choice : group.choices()) {
					joiner.add(choice.id()).add(choice.name()).add(choice.description()).add(Boolean.toString(choice.def()));
					if (choice.def()) defaultJoiner.add(choice.id());
				}
				groupJoiner.add(joiner.toString());
			}
			alertResults.put(name, defaultJoiner.toString());
			alertWaiters.put(name, latch);
			tellPuppet("["+name+"]:pickFlavor="+groupJoiner.toString().replace(':', '\u001B'));
			latch.awaitUninterruptibly();
			return Arrays.asList(alertResults.remove(name).split("\u001C"));
		}
	}

}
