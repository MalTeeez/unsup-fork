/*
 * This file is part of unsup.
 * Copyright © 2025 Exa Skye
 * https://git.sleeping.town/exa/unsup
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.grack.nanojson.JsonObject;
import com.unascribed.sup.agent.PuppetHandler.AlertOption;
import com.unascribed.sup.agent.PuppetHandler.AlertOptionType;
import com.unascribed.sup.agent.handler.AbstractFormatHandler.CheckResult;
import com.unascribed.sup.agent.handler.AbstractFormatHandler.FilePlan;
import com.unascribed.sup.agent.handler.AbstractFormatHandler.FileState;
import com.unascribed.sup.agent.handler.AbstractFormatHandler.UpdatePlan;
import com.unascribed.sup.agent.handler.NativeHandler;
import com.unascribed.sup.agent.handler.PackwizHandler;
import com.unascribed.sup.agent.util.CRLFHell;
import com.unascribed.sup.agent.util.RequestHelper;
import com.unascribed.sup.agent.util.CRLFHell.CorruptionType;
import com.unascribed.sup.agent.util.RequestHelper.DownloadedFile;
import com.unascribed.sup.agent.util.RequestHelper.Retry;
import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.ConflictType;
import com.unascribed.sup.data.SourceFormat;
import com.unascribed.sup.data.SysProps;

public class UpdateHandler {

	private static final Pattern domainPattern = Pattern.compile("(^|\\.)([^\\.]+\\.[^\\.]+)$");

	public static boolean checkForUpdate(JsonObject baseState, SourceFormat fmt, URI src, boolean autoaccept, boolean dryRun, boolean forceFlavorDefaults, Consumer<CheckResult> modifier) {
		PuppetHandler.updateTitle("title.checking", false);
		try {
			CheckResult res = null;
			if ("merge".equals(src.getScheme())) {
				Log.warn("Using an experimental feature: Manifest merging");
				JsonObject mergeStates = baseState.getObject("mergeStates");
				if (mergeStates == null) {
					mergeStates = new JsonObject();
					baseState.put("mergeStates", mergeStates);
				}
				for (String s : src.getRawSchemeSpecificPart().split(";")) {
					JsonObject thisState = mergeStates.getObject(s, new JsonObject());
					if (checkForUpdate(thisState, fmt, new URI(s), autoaccept, dryRun, forceFlavorDefaults, modifier)) {
						// if the user has accepted an update, then accept the rest of them implicitly
						autoaccept = true;
					}
					mergeStates.put(s, thisState);
				}
				if (!dryRun) Agent.saveState();
				return true;
			} else {
				Log.debug("Retrieving from "+src+" in "+fmt+" format");
				if ("http".equals(src.getScheme()) && Agent.config().packSig() == null) {
					try {
						if (!InetAddress.getByName(src.getHost()).isAnyLocalAddress()) {
							Log.warn("Using unencrypted HTTP without manifest signing - this is a very bad idea!");
						}
					} catch (UnknownHostException e) {}
				}
				res = switch (fmt) {
					case NONE ->
						throw new AssertionError("Config must be initialized by this point");
					case UNSUP ->
						NativeHandler.check(src, autoaccept, forceFlavorDefaults, baseState);
					case PACKWIZ ->
						PackwizHandler.check(src, autoaccept, forceFlavorDefaults, baseState);
				};
			}
			if (res != null) {
				Agent.sourceVersion = res.ourVersion.name();
				modifier.accept(res);
				if (res.plan != null) {
					JsonObject newState = applyUpdate(res, dryRun);
					if (newState != null) {
						baseState.clear();
						baseState.putAll(newState);
					}
					if (!dryRun) Agent.saveState();
					return true;
				}
			}
			return false;
		} catch (Throwable t) {
			Log.warn("Error while updating", t);
			PuppetHandler.tellPuppet(":expedite=openTimeout");
			if (PuppetHandler.openAlert("dialog.error.title",
					"dialog.error."+(Agent.standalone ? "standalone" : "normal"),
					AlertMessageType.ERROR, Agent.standalone ? AlertOptionType.OK : AlertOptionType.OK_CANCEL, AlertOption.OK) == AlertOption.CANCEL) {
				Log.info("User cancelled error dialog! Exiting.");
				throw ExitCode.USER_REQUEST.exit();
			}
			return false;
		} finally {
			PuppetHandler.tellPuppet(":subtitle=");
		}
	}

	private static JsonObject applyUpdate(CheckResult res, boolean dryRun) throws IOException {
		UpdatePlan<?> plan = res.plan;
		boolean bootstrapping = plan.isBootstrap;
		Log.debug("Alright, so here's what I'm thinking:");
		Set<String> unchanged = new HashSet<>(plan.expectedState.keySet());
		for (var en : plan.files.entrySet()) {
			unchanged.remove(en.getKey());
			FileState from = plan.expectedState.get(en.getKey());
			FilePlan to = en.getValue();
			assert to != null;
			Log.debug("- "+en.getKey()+" is currently "+ponder(from));
			Log.debug("  It has been changed to "+ponder(to.state));
			if (to.url != null) {
				if (to.fallbackUrl != null) {
					Log.debug("  I'll be grabbing that from "+to.url+" (or "+to.fallbackUrl+" if that doesn't work)");
				} else {
					Log.debug("  I'll be grabbing that from "+to.url);
				}
			}
		}
		Log.debug(unchanged.size()+" other file"+(unchanged.size() == 1 ? "" : "s")+" have not been changed.");
		for (Map.Entry<String, String> en : res.componentVersions.entrySet()) {
			String ours = MMCUpdater.currentComponentVersions.get(en.getKey());
			if (ours != null && !ours.equals(en.getValue())) {
				Log.debug("Component "+en.getKey()+" will be updated from "+ours+" to "+en.getValue());
			}
		}
		if (SysProps.DEBUG_PAUSE_BEFORE_UPDATE.orBias()) {
			Log.debug("Sound good? You have 4 seconds to kill the process if not.");
			try {
				Thread.sleep(4000);
			} catch (InterruptedException e) {
			}
			Log.debug("Continuing.");
		}
		File wd = Agent.config().useParentDirectory()
				? new File("").getAbsoluteFile().getParentFile()
				: new File("").getAbsoluteFile();
		if (Agent.config().useParentDirectory())
			Log.info("use_parent_directory is set; working directory root is "+wd.getAbsolutePath());
		PuppetHandler.updateSubtitle("subtitle.verifying");
		Set<String> moveAside = new HashSet<>();
		Map<ConflictType, AlertOption> conflictPreload = new EnumMap<>(ConflictType.class);
		for (var en : plan.files.entrySet()) {
			String path = en.getKey();
			FileState from = plan.expectedState.getOrDefault(path, FileState.EMPTY);
			FilePlan f = en.getValue();
			assert f != null;
			FileState to = f.state;
			File dest = new File(wd, path);
			if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+File.separator))
				throw new IOException("Refusing to download to a file outside of working directory");
			ConflictType conflictType = ConflictType.NO_CONFLICT;
			if (dest.exists()) {
				boolean normalConflict = false;
				long size = dest.length();
				if (from.hash() == null) {
					if (to.sizeMatches(size) && to.hash().equals(RequestHelper.hash(to.func(), dest))) {
						Log.info(path+" was created in this update and locally, but the local version matches the update. Skipping");
						f.skip = true;
						continue;
					}
					conflictType = ConflictType.LOCAL_AND_REMOTE_CREATED;
				} else if (from.sizeMatches(size)) {
					String hash = RequestHelper.hash(from.func(), dest);
					if (from.hash().equals(hash)) {
						Log.debug(path+" matches the expected from hash");
					} else if (to.sizeMatches(size) && to.hash().equals(from.func() == to.func() ? hash : RequestHelper.hash(to.func(), dest))) {
						Log.info(path+" matches the expected to hash, so has already been updated locally. Skipping");
						f.skip = true;
						continue;
					} else {
						Log.info("CONFLICT: "+path+" doesn't match the expected from hash ("+hash+" != "+ from.hash() +")");
						normalConflict = true;
					}
				} else if (to.sizeMatches(size) && to.hash().equals(RequestHelper.hash(to.func(), dest))) {
					Log.info(path+" matches the expected to hash, so has already been updated locally. Skipping");
					f.skip = true;
					continue;
				} else {
					Log.info("CONFLICT: "+path+" doesn't match the expected from size ("+size+" != "+ from.size() +")");
					normalConflict = true;
				}
				if (normalConflict) {
					if (to.hash() == null) {
						conflictType = ConflictType.LOCAL_CHANGED_REMOTE_DELETED;
					} else {
						conflictType = ConflictType.LOCAL_AND_REMOTE_CHANGED;
					}
				}
			} else {
				if (to.hash() == null) {
					Log.info(path+" was deleted in this update, but it's already missing locally. Skipping");
					continue;
				} else if (from.hash() != null) {
					conflictType = ConflictType.LOCAL_DELETED_REMOTE_CHANGED;
				}
			}
			if (conflictType != ConflictType.NO_CONFLICT) {
				AlertOption resp;
				if (!Agent.config().behavior().promptConflicts()) {
					resp = AlertOption.YES;
				} else if (conflictPreload.containsKey(conflictType)) {
					resp = conflictPreload.get(conflictType);
				} else {
					resp = PuppetHandler.openAlert("dialog.conflict.title",
							"dialog.conflict.leadin."+conflictType.translationKey+"¤"+path+"¤dialog.conflict.body¤"+(dest.exists() ? "dialog.conflict.aside_trailer" : ""),
							AlertMessageType.QUESTION, AlertOptionType.YES_NO_TO_ALL_CANCEL, AlertOption.YES);
					if (resp == AlertOption.NOTOALL) {
						resp = AlertOption.NO;
						conflictPreload.put(conflictType, AlertOption.NO);
					} else if (resp == AlertOption.YESTOALL) {
						resp = AlertOption.YES;
						conflictPreload.put(conflictType, AlertOption.YES);
					}
				}
				if (resp == AlertOption.NO) {
					f.skip = true;
					continue;
				} else if (resp == AlertOption.CANCEL) {
					Log.info("User cancelled conflict dialog! Exiting.");
					throw ExitCode.USER_REQUEST.exit();
				}
				if (dest.exists() && Agent.config().behavior().promptConflicts()) {
					moveAside.add(path);
				}
			}
		}
		File tmp = dryRun ? null : new File(wd, ".unsup-tmp");
		if (tmp != null) {
			Files.createDirectories(tmp.toPath());
		}
		AtomicIntegerArray progresses = new AtomicIntegerArray(plan.files.size());
		long denom = plan.files.size()*1000L;
		Runnable updateProgress = () -> {
			long sum = 0;
			for (int i = 0; i < progresses.length(); i++) {
				sum += progresses.get(i);
			}
			PuppetHandler.updateProgress((int)((sum*1000)/denom));
		};
		String title = bootstrapping ? "title.bootstrapping" : res.theirVersion.code() > res.ourVersion.code() ? "title.updating" : "title.downgrading";
		PuppetHandler.updateTitle(title, true);
		int workers = SysProps.DOWNLOAD_WORKERS.orBias();
		Log.debug("Using "+workers+" download worker"+(workers == 1 ? "" : "s"));
		ExecutorService svc = Executors.newFixedThreadPool(workers);
		Set<String> files = new HashSet<>();
		List<Future<?>> futures = new ArrayList<>();
		Map<FilePlan, DownloadedFile> downloads = new IdentityHashMap<>();
		Runnable updateSubtitle = () -> {
			if (files.isEmpty()) {
				PuppetHandler.updateSubtitle("subtitle.downloading_indeterminate");
			} else if (files.size() == 1) {
				PuppetHandler.updateSubtitleDownloading(files.iterator().next());
			} else {
				List<String> dl = new ArrayList<>();
				for (String s : files) {
					dl.add(s.substring(s.lastIndexOf('/')+1));
				}
				PuppetHandler.updateSubtitleDownloading(dl.toArray(new String[dl.size()]));
			}
		};
		int i = 0;
		for (var en : plan.files.entrySet()) {
			String path = en.getKey();
			FilePlan f = en.getValue();
			assert f != null;
			if (f.skip) {
				Log.info("Skipping download of "+path);
				progresses.set(i, 1000);
				continue;
			}
			FileState to = f.state;
			if (to.size() == 0) {
				progresses.set(i, 1000);
				continue;
			}
			final int fi = i;
			futures.add(svc.submit(() -> {
				synchronized (files) {
					files.add(path);
					updateSubtitle.run();
				}
				try {
					if (f.primerUrl != null) {
						try (InputStream in = RequestHelper.get(f.primerUrl, f.hostile).stream()) {
							byte[] buf = new byte[8192];
							while (true) {
								if (in.read(buf) == -1) break;
							}
						}
						Thread.sleep(2000+ThreadLocalRandom.current().nextInt(1200));
					}
					DownloadedFile df;
					try {
						if ("file".equals(f.url.getScheme())) {
							Log.info("Copying "+path);
						} else {
							Log.info("Downloading "+path+" from "+describe(f.url));
						}
						df = downloadAndCheckHash(tmp, progresses, fi, updateProgress, path, f, f.url, to);
					} catch (Throwable t) {
						if (f.fallbackUrl != null) {
							Log.warn("Failed to download "+path+" from specified URL, trying again from "+describe(f.fallbackUrl), t);
							df = downloadAndCheckHash(tmp, progresses, fi, updateProgress, path, f, f.fallbackUrl, to);
						} else {
							throw t;
						}
					}
					synchronized (downloads) {
						downloads.put(f, df);
					}
					return null;
				} finally {
					synchronized (files) {
						files.remove(path);
						updateSubtitle.run();
					}
				}
			}));
			i++;
		}
		svc.shutdown();
		for (Future<?> future : futures) {
			while (true) {
				try {
					future.get();
					break;
				} catch (InterruptedException e) {
				} catch (ExecutionException e) {
					for (Future<?> f2 : futures) {
						try {
							f2.cancel(false);
						} catch (Throwable t) {}
					}
					if (e.getCause() instanceof IOException ioe) throw ioe;
					throw new RuntimeException(e);
				}
			}
		}
		PuppetHandler.updateTitle(title, false);
		if (!dryRun) {
			synchronized (Agent.dangerMutex) {
				PuppetHandler.updateSubtitle("subtitle.applying");
				for (int pass = 0; pass < 2; pass++) {
					for (var en : plan.files.entrySet()) {
						String path = en.getKey();
						FilePlan f = en.getValue();
						assert f != null;
						FileState to = f.state;
						DownloadedFile df = downloads.get(f);
						if (df == null && to.size() != 0) {
							// Conflict dialog was rejected, skip this file.
							continue;
						}

						File dest = new File(wd, path);
						if (!dest.getAbsolutePath().startsWith(wd.getAbsolutePath()+File.separator))
							throw new IOException("Refusing to download to a file outside of working directory");

						Path destPath;
						try {
							destPath = dest.toPath();
						} catch (InvalidPathException e) {
							if (pass == 0) Log.error("Destination file path "+dest+" is not valid on this OS/filesystem/charset combination!", e);
							continue;
						}
						if (pass == 1) {
							var parent = dest.getParentFile();
							if (parent != null) Files.createDirectories(parent.toPath());
						}
						if (pass == 0 && moveAside.contains(path)) {
							Log.debug("Displacing "+path);
							Files.move(destPath, destPath.resolveSibling(destPath.getFileName().toString()+".orig"), StandardCopyOption.REPLACE_EXISTING);
						}
						if (to.size() == 0) {
							if (to.hash() == null) {
								if (pass == 0 && Files.exists(destPath)) {
									Log.info("Deleting "+path);
									Files.delete(destPath);
								}
							} else if (dest.exists()) {
								if (pass == 1) {
									Log.debug("Blanking "+path);
									try (FileOutputStream fos = new FileOutputStream(dest)) {
										// open and then immediately close the file to overwrite it with nothing
									}
								}
							} else {
								if (pass == 1) {
									Log.debug("Touching "+path);
									if (!dest.createNewFile()) {
										Log.warn("Failed to create "+path);
									}
								}
							}
						} else if (pass == 1) {
							Log.debug("Applying "+path);
							assert df != null;
							Files.move(df.file().toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
						}
					}
				}
				if (!plan.skipStateApplication) {
					plan.newState.put("current_version", res.theirVersion.toJson());
				}
				try {
					Agent.updatedComponents = MMCUpdater.apply(res.componentVersions);
				} catch (Throwable t) {
					Log.warn("Failed to apply component updates", t);
				}
			}
		}
		Log.info("Update successful!");
		Agent.updated = true;
		return plan.skipStateApplication ? null : plan.newState;
	}

	static String ponder(FileState state) {
		if (state == null) return "[MISSING. STATE DATA IS INCOMPLETE OR CORRUPT]";
		if (state.hash() == null) {
			return "[deleted]";
		}
		return state.toString();
	}

	static DownloadedFile downloadAndCheckHash(File tmp, AtomicIntegerArray progresses, int progressIdx,
			Runnable updateProgress, String path, FilePlan f, URI url, FileState to) throws IOException {
		return RequestHelper.withRetries(3, () -> {
			DownloadedFile df = RequestHelper.downloadToFile(url, tmp, to.size(),
					(state, amt, size) -> {
						switch (state) {
							case DOWNLOADING -> {
								if (size.isPresent()) {
									progresses.set(progressIdx, (int)((amt*1000)/size.getAsLong()));
								}
							}
							case COMPLETE -> {
								progresses.set(progressIdx, 1000);
							}
							case FAILED -> {
								progresses.set(progressIdx, 0);
							}
						}
						updateProgress.run();
					}, to.func(), f.hostile);
			if (!df.hash().equals(to.hash())) {
				CorruptionType type = CRLFHell.checkForCorruption(to.func(), to.hash(), new FileInputStream(df.file()));
				String extra = "";
				switch (type) {
					case UNKNOWN: {
						// Can't provide any help here.
						break;
					}
					case EXPECTED_DOS_GOT_UNIX: {
						extra = " - it appears to have been corrupted by a DOS-to-Unix line-ending conversion. "
								+ "Ensure autocrlf is disabled in Git for anyone that uses Windows to work on this pack.";
						break;
					}
					case EXPECTED_UNIX_GOT_DOS: {
						extra = " - it appears to have been corrupted by a Unix-to-DOS line-ending conversion.";
						break;
					}
				}
				if (path.endsWith(".js")) {
					extra += " (Ensure JavaScript Minification is disabled on the host)";
				} else if (path.endsWith(".css")) {
					extra += " (Ensure CSS Minification is disabled on the host)";
				} else if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")) {
					extra += " (Ensure Image Optimization is disabled on the host)";
				}
				throw new Retry("Hash mismatch on downloaded file for "+path+" from "+url+" - expected "+ to.hash() +", got "+ df.hash()+extra,
						IOException::new);
			}
			return df;
		});
	}

	static String describe(URI url) {
		if (url == null) return "(null)";
		if (SysProps.DEBUG.orBias()) return url.toString();
		String host = url.getHost();
		if (host == null || host.isEmpty()) return url.toString();
		Matcher m = domainPattern.matcher(host);
		String domain;
		if (m.find()) {
			domain = m.group(2);
		} else {
			domain = host;
		}
		return switch (domain) {
			case "modrinth.com" -> "Modrinth";
			case "forgecdn.net", "curseforge.com" -> "CurseForge";
			case "github.com", "githubusercontent.com", "github.io" -> "GitHub";
			case "codeberg.org" -> "Codeberg";
			case "planetminecraft.com" -> "Planet Minecraft";
			case "mcarchive.net" -> "MCArchive";
			case "archive.org" -> "Internet Archive";
			case "prismlauncher.org" -> "PrismLauncher";
			case "maven.org" -> "Maven Central";
			default -> host;
		};
	}

}
