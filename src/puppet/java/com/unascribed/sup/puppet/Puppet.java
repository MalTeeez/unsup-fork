/*
 * This file is part of unsup.
 * Copyright © 2020-2025 Una Kearney
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

package com.unascribed.sup.puppet;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.annotation.NotNull;

import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.ColorChoice;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.SysProps;
import com.unascribed.sup.data.FlavorGroup.FlavorChoice;
import com.unascribed.sup.data.SysProps.PuppetMode;
import com.unascribed.sup.puppet.opengl.GLPuppet;
import com.unascribed.sup.puppet.swing.SwingPuppet;

import me.saharnooby.qoi.QOIDecoder;
import me.saharnooby.qoi.QOIImage;

public class Puppet {

	public static final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Scheduler"));
	public static final ExecutorService slow = Executors.newCachedThreadPool(r -> new Thread(r, "Slow Lane"));
	private static final int[] colors = ColorChoice.createLookup();
	
	private static final BlockingQueue<Runnable> mainThreadWorkQueue = new LinkedBlockingQueue<>();
	private static final Thread mainThread = Thread.currentThread();
	
	public static String modpackName = null;
	public static QOIImage icon = null;
	
	public static int flavorDialogWidth = 600;
	public static int flavorDialogHeight = 400;
	public static double flavorDialogBias = 0.4;
	
	public static volatile boolean exitOnDone = true;
	
	public static void main(String[] args) {
		Thread.currentThread().setName("Main");
		
		ColorChoice.delegate = Puppet::getColor;
		
		PuppetMode mode = SysProps.PUPPET_MODE;
		boolean didOverride = false;
		PuppetDelegate delTmp = null;
		if (mode == PuppetMode.AUTO) {
			if (System.getProperty("javax.accessibility.assistive_technologies") != null
					|| System.getProperty("assistive_technologies") != null) {
				log("INFO", "Forcing Swing puppet as assistive technologies may be present");
				mode = PuppetMode.SWING;
				didOverride = true;
			}
		}
		if (didOverride) {
			log("INFO", "Pass -Dunsup.puppetMode=opengl to override");
		}
		if (mode == PuppetMode.AUTO || mode == PuppetMode.OPENGL) {
			Throwable error = null;
			try {
				delTmp = GLPuppet.start();
				if (delTmp != null) {
					log("DEBUG", "Initialized OpenGL puppet");
					log("DEBUG", "Pass -Dunsup.puppetMode=swing to override");
				}
			} catch (Throwable t) {
				error = t;
			}
			if (delTmp == null) {
				if (mode == PuppetMode.OPENGL) {
					log("ERROR", "Failed to initialize OpenGL puppet, which was our only option by user request!");
					System.exit(1);
					return;
				} else {
					log("WARN", "Failed to initialize OpenGL puppet, falling back to Swing", error);
				}
			}
		}
		if (delTmp == null) {
			delTmp = SwingPuppet.start();
			if (delTmp != null) {
				log("DEBUG", "Initialized Swing puppet");
			} else {
				log("ERROR", "Failed to initialize Swing puppet, giving up!");
				System.exit(1);
				return;
			}
		}
		@NotNull PuppetDelegate del = delTmp;
		
		System.out.println("unsup puppet ready");
		
		new Thread(() -> {
			Map<String, ScheduledFuture<?>> orders = new HashMap<>();
			Map<String, Runnable> orderRunnables = new HashMap<>();
			
			BufferedInputStream in = new BufferedInputStream(System.in, 512);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try {
				while (true) {
					int by = in.read();
					if (by == -1) break;
					String line;
					if (by == 0) {
						line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
						buffer.reset();
					} else {
						buffer.write(by);
						continue;
					}
					String name;
					if (line.startsWith("[")) {
						int close = line.indexOf(']');
						name = line.substring(1, close);
						line = line.substring(close+1);
					} else {
						name = null;
					}
					String timing = line.substring(0, line.indexOf(':'));
					int delay;
					if (timing.isEmpty()) {
						delay = 0;
					} else {
						delay = Integer.parseInt(timing);
					}
					int eq = line.indexOf('=');
					String order = line.substring(line.indexOf(':')+1, eq == -1 ? line.length() : eq);
					String arg = eq == -1 ? "" : line.substring(eq+1);
					Runnable r;
					switch (order) {
						case "build" -> {
							r = del::build;
						}
						case "color" -> {
							String[] spl = arg.split(":", 2);
							colors[ColorChoice.valueOf(spl[0]).ordinal()] = Integer.parseInt(spl[1], 16);
							continue;
						}
						case "string" -> {
							String[] spl = arg.split(":", 2);
							Translate.addTranslation(spl[0], spl[1]);
							continue;
						}
						case "icon" -> {
							byte[] data;
							try {
								data = Base64.getDecoder().decode(arg);
							} catch (Throwable t) {
								log("ERROR", "Failed to load branding image - invalid Base64", t);
								continue;
							}
							InputStreamWrapper[] codecs = {
								BrotliInputStream::new,
								GZIPInputStream::new,
								is -> is
							};
							List<Throwable> suppressed = new ArrayList<>();
							for (int i = 0; i < codecs.length; i++) {
								try {
									icon = QOIDecoder.decode(codecs[i].wrap(new ByteArrayInputStream(data)), 4);
									break;
								} catch (Throwable t) {
									if (i == codecs.length-1) {
										suppressed.forEach(t::addSuppressed);
										log("ERROR", "Failed to load branding image - are you sure it's in QOI{,.br,.gz} format?", t);
										continue;
									} else {
										suppressed.add(t);
									}
								}
							}
							continue;
						}
						case "modpackName" -> {
							modpackName = arg;
							continue;
						}
						case "flavorDialogGeom" -> {
							String[] spl = arg.split("x", 2);
							flavorDialogWidth = Integer.parseInt(spl[0]);
							flavorDialogHeight = Integer.parseInt(spl[1]);
							continue;
						}
						case "flavorDialogBias" -> {
							flavorDialogBias = Math.max(0.15, Math.min(0.85, Double.parseDouble(arg)));
							continue;
						}
						case "belay" -> {
							r = () -> {
								synchronized (orders) {
									if (orders.containsKey(arg)) {
										orders.remove(arg).cancel(false);
										orderRunnables.remove(arg);
									}
								}
							};
						}
						case "expedite" -> {
							r = () -> {
								Runnable inner = null;
								synchronized (orders) {
									if (orders.containsKey(arg)) {
										if (orders.remove(arg).cancel(false)) {
											// we don't want to be holding the orders mutex while we run
											// the original order
											inner = orderRunnables.remove(arg);
										} else {
											orderRunnables.remove(arg);
										}
									}
								}
								if (inner != null) inner.run();
							};
						}
						case "visible" -> {
							boolean b = Boolean.parseBoolean(arg);
							r = () -> del.setVisible(b);
						}
						case "exit" -> {
							r = () -> {
								System.exit(0);
							};
						}
						case "mode" -> {
                            switch (arg) {
                                case "ind" -> r = del::setProgressIndeterminate;
                                case "det" -> r = del::setProgressDeterminate;
                                case "done" -> r = del::setDone;
                                default -> {
                                    Puppet.log("WARN", "Unknown mode " + arg + ", expected ind, det, or done");
                                    continue;
                                }
                            }
						}
						case "prog" -> {
							int i = Integer.parseInt(arg);
							r = () -> del.setProgress(i);
						}
						case "title" -> {
							r = () -> del.setTitle(arg);
						}
						case "subtitle" -> {
							r = () -> del.setSubtitle(arg);
						}
						case "downloading" -> {
							r = () -> del.setDownloading(arg.split("\u001C"));
						}
						case "alert" -> {
							String[] split = arg.split(":");
							String title = split[0];
							if ("$$changeFlavorsOffer".equals(title)) {
								r = () -> del.offerChangeFlavors(name);
							} else {
								String body = split[1];
								String messageTypeStr = split[2];
								String optionTypeStr = split[3];
								String def = split.length < 5 ? null : split[4];
								if (messageTypeStr.startsWith("choice=")) {
									String[] options = messageTypeStr.substring(7).split("\u001C");
									for (int i = 0; i < options.length; i++) {
										options[i] = Translate.format(options[i].replace('\u001B', ':'));
									}
									r = () -> del.openChoiceDialog(name, title, body, options, optionTypeStr);
								} else {
									AlertMessageType messageType = AlertMessageType.valueOf(messageTypeStr.toUpperCase(Locale.ROOT));
									String[] options;
									switch (optionTypeStr) {
										case "yesno":
											options = new String[]{"option.yes", "option.no"};
											break;
										case "yesnocancel":
											options = new String[]{"option.yes", "option.no", "option.cancel"};
											break;
										case "okcancel":
											options = new String[]{"option.ok", "option.cancel"};
											break;
										case "yesnotoallcancel":
											options = new String[]{"option.yes_to_all", "option.yes", "option.no_to_all", "option.no", "option.cancel"};
											break;
										default:
											Puppet.log("WARN", "Unknown dialog option type "+optionTypeStr+", defaulting to ok");
											// fallthru
										case "ok":
											options = new String[]{"option.ok"};
											break;
									}
									r = () -> del.openMessageDialog(name, title, body, messageType, options, Translate.format(def));
								}
							}
						}
						case "pickFlavor" -> {
							String[] split = arg.split(":");
							List<FlavorGroup> groups = new ArrayList<>();
							for (String s : split[0].replace('\u001B', ':').split("\u001D")) {
								String[] fields = s.split("\u001C");
								FlavorGroup grp = new FlavorGroup();
								grp.id = fields[0];
								grp.name = fields[1];
								grp.description = Translate.format(fields[2]);
								for (int i = 3; i < fields.length; i += 4) {
									FlavorChoice c = new FlavorChoice();
									c.id = fields[i];
									c.name = fields[i+1];
									c.description = Translate.format(fields[i+2]);
									c.def = Boolean.parseBoolean(fields[i+3]);
									grp.choices.add(c);
								}
								groups.add(grp);
							}
							r = () -> del.openFlavorDialog(name, groups);
						}
						default -> {
							Puppet.log("WARN", "Unknown order "+order);
							continue;
						}
					}
					Runnable fr = r;
					if (name != null) {
						fr = () -> {
							r.run();
							synchronized (orders) {
								orders.remove(name);
								orderRunnables.remove(name);
							}
						};
					}
					if (delay > 0) {
						ScheduledFuture<?> future = sched.schedule(fr, delay, TimeUnit.MILLISECONDS);
						if (name != null) {
							synchronized (orders) {
								orders.put(name, future);
								orderRunnables.put(name, fr);
							}
						}
					} else {
						sched.execute(fr);
					}
				}
			} catch (IOException e) {
				log("ERROR", "Failed to listen for orders", e);
			} finally {
				System.exit(0);
			}
		}, "Reader").start();
		
		startMainThreadRunner();
	}

	public static void startMainThreadRunner() {
		while (true) {
			try {
				mainThreadWorkQueue.take().run();
			} catch (InterruptedException e) {
			}
		}
	}

	public static void log(String flavor, String msg) {
		System.err.println(flavor+"|("+Thread.currentThread().getName()+")"+msg);
	}

	public static void log(String flavor, String msg, Throwable t) {
		if (t != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			t.printStackTrace(pw);
			pw.flush();
			for (String line : sw.toString().split(System.lineSeparator())) {
				log(flavor, line);
			}
		}
		log(flavor, msg);
	}
	
	public static String maybeBranded(String langPrefix) {
		return modpackName == null ? langPrefix+".title" : langPrefix+".branded.title";
	}
	
	public static void reportCloseRequest() {
		System.out.println("closeRequested");
	}

	public static void reportChoice(String name, String opt) {
		System.out.println("alert:"+name+":"+opt);
	}

	public static void reportDone() {
		if (exitOnDone) System.exit(0);
	}
	
	public static boolean isMainThread() {
		return Thread.currentThread() == mainThread;
	}
	
	public static void runOnMainThread(Runnable r) {
		if (isMainThread()) {
			r.run();
		} else {
			mainThreadWorkQueue.add(r);
		}
	}

	public static int getColor(ColorChoice choice) {
		return colors[choice.ordinal()];
	}
	
	public interface InputStreamWrapper {
		InputStream wrap(InputStream is) throws IOException;
	}

}
