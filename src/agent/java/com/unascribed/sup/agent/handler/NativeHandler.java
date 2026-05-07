/*
 * This file is part of unsup.
 * Copyright © 2023-2025 Exa Skye
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

package com.unascribed.sup.agent.handler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParserException;
import com.unascribed.sup.agent.Agent;
import com.unascribed.sup.agent.ExitCode;
import com.unascribed.sup.agent.Log;
import com.unascribed.sup.agent.PuppetHandler;
import com.unascribed.sup.agent.PuppetHandler.AlertOption;
import com.unascribed.sup.agent.PuppetHandler.AlertOptionType;
import com.unascribed.sup.agent.data.HashFunction;
import com.unascribed.sup.agent.util.RequestHelper;
import com.unascribed.sup.bootstrap.Util;
import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.FlavorChoice;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.SysProps;
import com.unascribed.sup.data.Version;
import com.unascribed.sup.util.Iterables;

public class NativeHandler extends AbstractFormatHandler {
	
	protected static final String DEFAULT_HASH_FUNCTION = HashFunction.SHA2_256.name;

	private static class FileToDownloadWithCode extends FilePlan {
		int code;
	}

	private static final class PathAndHash {
		final String path, hash;
		PathAndHash(String path, String hash) { this.path = path; this.hash = hash; }
		@Override public boolean equals(Object o) {
			return o instanceof PathAndHash p && Objects.equals(path, p.path) && Objects.equals(hash, p.hash);
		}
		@Override public int hashCode() { return Objects.hash(path, hash); }
	}
	
	public static CheckResult check(URI src, boolean autoaccept, boolean forceFlavorDefaults, JsonObject baseState) throws IOException, JsonParserException, URISyntaxException {
		Log.info("Loading unsup-format manifest from "+src);
		JsonObject manifest = RequestHelper.loadJson(src, 1*M, src.resolve("manifest.sig"));
		checkManifestFlavor(manifest, "root", it -> it == 1);
		Version ourVersion = Version.fromJson(baseState.getObject("current_version"));
		if (!manifest.containsKey("versions")) throw new IOException("Manifest is missing versions field");
		Version theirVersion = Version.fromJson(manifest.getObject("versions").getObject("current"));
		if (theirVersion == null) throw new IOException("Manifest is missing current version field");
		if (SysProps.DEBUG_OVERRIDE_REMOTE_VERSION_CODE.isPresent()) {
			theirVersion = new Version(theirVersion.name(), SysProps.DEBUG_OVERRIDE_REMOTE_VERSION_CODE.get());
		}
		JsonObject newState = new JsonObject(baseState);
		// Accumulate version history so the selector has past versions to offer
		if (ourVersion != null) {
			JsonArray history = newState.getArray("version_history");
			if (history == null) history = new JsonArray(Collections.emptyList());
			boolean alreadyTracked = false;
			for (Object o : history) {
				if (o instanceof JsonObject jo) {
					Version v = Version.fromJson(jo);
					if (v != null && v.code() == ourVersion.code()) { alreadyTracked = true; break; }
				}
			}
			if (!alreadyTracked) history.add(ourVersion.toJson());
			newState.put("version_history", history);
		}
		JsonArray ourFlavors = baseState.getArray("flavors");
		if (ourFlavors == null && baseState.containsKey("flavor")) {
			ourFlavors = new JsonArray();
			ourFlavors.add(baseState.get("flavor"));
			newState.put("flavors", ourFlavors);
			newState.remove("flavor");
		}
		JsonArray theirFlavorGroups = manifest.getArray("flavor_groups");
		List<FlavorGroup> unpickedGroups = new ArrayList<>();
		if (theirFlavorGroups != null) {
			flavors: for (Object ele : theirFlavorGroups) {
				if (ele instanceof JsonObject obj) {
					JsonArray envs = obj.getArray("envs");
					if (envs != null && Agent.config().useEnvs() && !Iterables.contains(envs, Agent.config().detectedEnv())) {
						continue;
					}
					var id = obj.getString("id");
					if (id == null)
						throw new IOException("A flavor group is missing an ID");
					var name = obj.getString("name", id);
					var description = obj.getString("description", "flavor.default_description");
					String defChoice = Agent.config().defaultFlavors().get(id);
					var choicesJson = obj.getArray("choices");
					var choices = new ArrayList<FlavorChoice>();
					var grp = FlavorGroup.builder()
						.id(id)
						.name(name)
						.description(description)
						.choices(choices);
					for (Object cele : choicesJson) {
						String choiceId;
						var cb = FlavorChoice.builder();
						if (cele instanceof JsonObject cobj) {
							choiceId = cobj.getString("id");
							if (choiceId == null)
								throw new IOException("A flavor choice in group "+id+" is missing an ID");
							cb.id(choiceId);
							cb.name(cobj.getString("name", choiceId));
							cb.description(cobj.getString("description", ""));
						} else {
							choiceId = String.valueOf(cele);
							cb.id(choiceId);
							cb.name(choiceId);
							cb.description("");
						}
						if (Iterables.contains(ourFlavors, choiceId)) {
							// a choice has already been made for this flavor
							continue flavors;
						}
						boolean def = choiceId.equals(defChoice);
						cb.def(def);
						var c = cb.build();
						if (def) {
							grp.defChoice(c.id());
							grp.defChoiceName(c.name());
						}
						choices.add(c);
					}
					grp.choices(choices);
					unpickedGroups.add(grp.build());
				}
			}
			ourFlavors = handleFlavorSelection(ourFlavors, unpickedGroups, newState, forceFlavorDefaults);
		} else {
			JsonArray theirFlavors = manifest.getArray("flavors");
			if (theirFlavors != null) {
				var choices = new ArrayList<FlavorChoice>();
				var grp = FlavorGroup.builder()
						.id("default")
						.name("Flavor")
						.choices(choices);
				for (Object ele : theirFlavors) {
					if (ele instanceof JsonObject obj) {
						JsonArray envs = obj.getArray("envs");
						if (envs != null && Agent.config().useEnvs() && !Iterables.contains(envs, Agent.config().detectedEnv())) {
							continue;
						}
						String id = obj.getString("id");
						if (id == null)
							throw new IOException("A flavor group is missing an ID");
						String name = obj.getString("name", id);
						String description = "flavor.default_description";
						int firstOParen = name.indexOf('(');
						int lastCParen = name.lastIndexOf(')');
						if (firstOParen != -1 && lastCParen > firstOParen) {
							String newName = name.substring(0, firstOParen).trim();
							description = name.substring(firstOParen+1, lastCParen);
							name = newName;
						}
						choices.add(FlavorChoice.builder()
								.id(id)
								.name(name)
								.description(description)
								.build());
					}
				}
				unpickedGroups.add(grp.build());
				ourFlavors = handleFlavorSelection(ourFlavors, unpickedGroups, newState, forceFlavorDefaults);
			}
		}
		boolean selectorChosen = false;
		if (SysProps.VERSION_SELECTOR_ON_LAUNCH.orBias() && ourVersion != null) {
			List<Version> available = new ArrayList<>();
			available.add(theirVersion);
			// Include versions advertised in the manifest's history array
			JsonObject versionsObj = manifest.getObject("versions");
			if (versionsObj != null) {
				JsonArray manifestHistory = versionsObj.getArray("history");
				if (manifestHistory != null) {
					for (Object o : manifestHistory) {
						if (o instanceof JsonObject jo) {
							Version v = Version.fromJson(jo);
							if (v != null && available.stream().noneMatch(x -> x.code() == v.code()))
								available.add(v);
						}
					}
				}
			}
			// Also include versions from accumulated state history
			JsonArray stateHistory = newState.getArray("version_history");
			if (stateHistory != null) {
				for (Object o : stateHistory) {
					if (o instanceof JsonObject jo) {
						Version v = Version.fromJson(jo);
						if (v != null && available.stream().noneMatch(x -> x.code() == v.code()))
							available.add(v);
					}
				}
			}
			available.sort(Comparator.comparingInt(Version::code).reversed());
			if (available.size() <= 1) {
				Log.info("No version history available, skipping version selector.");
			} else {
				// Returns empty on Skip; throws ExitCode.USER_REQUEST on Cancel/close (GUI only)
				Optional<Integer> selected = PuppetHandler.openVersionSelectDialog(available, ourVersion.code());
				if (selected.isPresent()) {
					int code = selected.get();
					theirVersion = available.stream().filter(v -> v.code() == code).findFirst().get();
					selectorChosen = true;
				} else {
					Log.info("User skipped version selector.");
				}
			}
		}
		UpdatePlan<FileToDownloadWithCode> bootstrapPlan = null;
		boolean bootstrapping = false;
		if (ourVersion == null) {
			bootstrapping = true;
			Log.info("Update available! We have nothing, they have "+theirVersion);
			JsonObject bootstrap = null;
			try {
				bootstrap = RequestHelper.loadJson(src.resolve("bootstrap.json"), 16*M, src.resolve("bootstrap.sig"));
			} catch (FileNotFoundException e) {
				Log.info("Bootstrap manifest missing, will have to retrieve and collapse every update");
			}
			if (bootstrap != null) {
				checkManifestFlavor(bootstrap, "bootstrap", it -> it == 1);
				Version bootstrapVersion = Version.fromJson(bootstrap.getObject("version"));
				if (bootstrapVersion == null) throw new IOException("Bootstrap manifest is missing version field");
				if (bootstrapVersion.code() < theirVersion.code()) {
					Log.warn("Bootstrap manifest version "+bootstrapVersion+" is older than root manifest version "+theirVersion+", will have to perform extra updates");
				}
				HashFunction func = HashFunction.byName(bootstrap.getString("hash_function", DEFAULT_HASH_FUNCTION));
				PuppetHandler.updateTitle("title.bootstrapping", false);
				bootstrapPlan = new UpdatePlan<>(true, newState);
				for (Object o : bootstrap.getArray("files")) {
					if (!(o instanceof JsonObject file)) throw new IOException("Entry "+o+" in files array is not an object");
					var path = file.getString("path");
					if (path == null) throw new IOException("Entry in files array is missing path");
					var hash = file.getString("hash");
					if (hash == null) throw new IOException(path+" in files array is missing hash");
					if (hash.length() != func.sizeInHexChars())  throw new IOException(path+" in files array hash "+hash+" is wrong length ("+hash.length()+" != "+func.sizeInHexChars()+")");
					long size = file.getLong("size", -1);
					if (size < 0) throw new IOException(path+" in files array has invalid or missing size");
					if (size == 0 && !hash.equals(func.emptyHash())) throw new IOException(path+" in files array is empty file, but hash isn't the empty hash ("+hash+" != "+func.emptyHash()+")");
				String urlStr = RequestHelper.checkSchemeMismatch(src, file.getString("url"));
				String mirrorUrlStr = RequestHelper.checkSchemeMismatch(src, file.getString("mirror_url"));
				JsonArray envs = file.getArray("envs");
				if (Agent.config().useEnvs() && !Iterables.contains(envs, Agent.config().detectedEnv())) {
					Log.info("Skipping "+path+" as it's not eligible for env "+Agent.config().detectedEnv());
					continue;
				}
				JsonArray flavors = file.getArray("flavors");
				if (flavors != null && !Iterables.intersects(flavors, ourFlavors)) {
					Log.info("Skipping "+path+" as it's not eligible for our selected flavors");
					continue;
				}
				URI fallbackUrl = src.resolve(Util.uriOfPath(blobPath(hash)));
				URI url;
				URI mirrorUrl = null;
				if (urlStr == null) {
					url = fallbackUrl;
				} else {
					url = new URI(urlStr);
					if (mirrorUrlStr != null) mirrorUrl = new URI(mirrorUrlStr);
				}
				FileToDownloadWithCode ftd = new FileToDownloadWithCode();
				ftd.state = new FileState(func, hash, size);
				ftd.url = url;
				ftd.mirrorUrl = mirrorUrl;
				ftd.fallbackUrl = fallbackUrl;
					ftd.code = bootstrapVersion.code();
					bootstrapPlan.files.put(path, ftd);
					bootstrapPlan.expectedState.put(path, FileState.EMPTY);
				}
				ourVersion = bootstrapVersion;
			} else {
				ourVersion = new Version("null", 0);
			}
		}
		if (theirVersion.code() > ourVersion.code()) {
			if (!bootstrapping) {
				Log.info("Update available! We have "+ourVersion+", they have "+theirVersion);
				if (!autoaccept && !selectorChosen) {
					AlertOption updateResp = PuppetHandler.openAlert("dialog.update.title",
							"dialog.update.named¤"+ ourVersion.name() +"¤"+ theirVersion.name(),
							AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
					if (updateResp == AlertOption.CLOSED) {
						Log.info("User closed update dialog! Exiting...");
						throw ExitCode.USER_REQUEST.exit();
					}
					if (updateResp == AlertOption.NO) {
						Log.info("Ignoring update by user choice.");
						return new CheckResult(ourVersion, theirVersion, null, Collections.emptyMap());
					}
				}
			}
			UpdatePlan<FileToDownloadWithCode> plan = new UpdatePlan<>(bootstrapping, newState);
			if (bootstrapPlan != null) {
				plan.files.putAll(bootstrapPlan.files);
				plan.expectedState.putAll(bootstrapPlan.expectedState);
			}
			PuppetHandler.updateTitle(bootstrapping ? "title.bootstrapping" : "title.updating", false);
			PuppetHandler.updateSubtitle("subtitle.calculating");
			boolean yappedAboutConsistency = false;
			int updates = theirVersion.code() - ourVersion.code();
			for (int i = 0; i < updates; i++) {
				int code = ourVersion.code() +(i+1);
				JsonObject ver = RequestHelper.loadJson(src.resolve(Util.uriOfPath("versions/"+code+".json")), 16*M,
						src.resolve(Util.uriOfPath("versions/"+code+".sig")));
				checkManifestFlavor(ver, "update", it -> it == 1);
				HashFunction func = HashFunction.byName(ver.getString("hash_function", DEFAULT_HASH_FUNCTION));
				for (Object o : ver.getArray("changes")) {
					if (!(o instanceof JsonObject file)) throw new IOException("Entry "+o+" in changes array is not an object");
					var path = file.getString("path");
					if (path == null) throw new IOException("Entry in changes array is missing path");
					var fromHash = file.getString("from_hash");
					if (fromHash != null && fromHash.length() != func.sizeInHexChars())  throw new IOException(path+" in changes array from_hash "+fromHash+" is wrong length ("+fromHash.length()+" != "+func.sizeInHexChars()+")");
					long fromSize = file.getLong("from_size", -1);
					if (fromSize < 0) throw new IOException(path+" in changes array has invalid or missing from_size");
					if (fromSize == 0 && (fromHash != null && !fromHash.equals(func.emptyHash()))) throw new IOException(path+" from in changes array is empty file, but hash isn't the empty hash or null ("+fromHash+" != "+func.emptyHash()+")");
					var toHash = file.getString("to_hash");
					if (toHash != null && toHash.length() != func.sizeInHexChars())  throw new IOException(path+" in changes array to_hash "+toHash+" is wrong length ("+toHash.length()+" != "+func.sizeInHexChars()+")");
					long toSize = file.getLong("to_size", -1);
					if (toSize < 0) throw new IOException(path+" in changes array has invalid or missing toSize");
					if (toSize == 0 && (toHash != null && !toHash.equals(func.emptyHash()))) throw new IOException(path+" to in changes array is empty file, but hash isn't the empty hash or null ("+toHash+" != "+func.emptyHash()+")");
					if (fromSize == toSize && Objects.equals(fromHash, toHash)) {
						Log.warn(path+" in changes array has same from and to hash/size? Ignoring");
						continue;
					}
				String urlStr = RequestHelper.checkSchemeMismatch(src, file.getString("url"));
				String mirrorUrlStr = RequestHelper.checkSchemeMismatch(src, file.getString("mirror_url"));
				JsonArray envs = file.getArray("envs");
				if (Agent.config().useEnvs() && !Iterables.contains(envs, Agent.config().detectedEnv())) {
					Log.info("Skipping "+path+" as it's not eligible for env "+Agent.config().detectedEnv());
					continue;
				}
				var flavors = file.getArray("flavors");
				if (flavors != null && !Iterables.intersects(flavors, ourFlavors)) {
					Log.info("Skipping "+path+" as it's not eligible for our selected flavors");
					continue;
				}
				URI fallbackUrl = toHash == null ? null : src.resolve(Util.uriOfPath(blobPath(toHash)));
				URI url;
				URI mirrorUrl = null;
				if (urlStr == null) {
					url = fallbackUrl;
				} else {
					url = new URI(urlStr);
					if (mirrorUrlStr != null) mirrorUrl = new URI(mirrorUrlStr);
				}
				FileToDownloadWithCode to = plan.files.get(path);
				if (to != null) {
					if (to.state.func() == func) {
						if (!Objects.equals(to.state.hash(), fromHash) || to.state.size() != fromSize) {
							throw new IOException("Bad update: "+path+" in "+to.code+" specified to become "+to.state+
									", but "+code+" expects it to have been "+func+"("+fromHash+") size "+fromSize);
						}
					} else if (!yappedAboutConsistency) {
						yappedAboutConsistency = true;
						Log.warn("Cannot perform consistency check on multi-update due to mismatched hash functions");
					}
					to.state = new FileState(func, toHash, toSize);
					to.code = code;
					to.fallbackUrl = fallbackUrl;
					to.mirrorUrl = mirrorUrl;
					to.url = url;
				} else {
					to = new FileToDownloadWithCode();
					to.code = code;
					to.state = new FileState(func, toHash, toSize);
					to.fallbackUrl = fallbackUrl;
					to.mirrorUrl = mirrorUrl;
					to.url = url;
					plan.expectedState.put(path, new FileState(func, fromHash, fromSize));
					plan.files.put(path, to);
				}
				}
			}
			return new CheckResult(ourVersion, theirVersion, plan, Collections.emptyMap());
		} else if (bootstrapPlan != null) {
			return new CheckResult(ourVersion, theirVersion, bootstrapPlan, Collections.emptyMap());
		} else if (ourVersion.code() > theirVersion.code()) {
			Log.info("Downgrade available! We have "+ourVersion+", they have "+theirVersion);
			if (!autoaccept && !selectorChosen) {
				AlertOption downgradeResp = PuppetHandler.openAlert("dialog.downgrade.title",
						"dialog.downgrade.named¤"+ ourVersion.name() +"¤"+ theirVersion.name(),
						AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
				if (downgradeResp == AlertOption.CLOSED) {
					Log.info("User closed downgrade dialog! Exiting...");
					throw ExitCode.USER_REQUEST.exit();
				}
				if (downgradeResp == AlertOption.NO) {
					Log.info("Ignoring downgrade by user choice.");
					return new CheckResult(ourVersion, theirVersion, null, Collections.emptyMap());
				}
			}
			Log.debug("User accepted downgrade (or autoaccept/selectorChosen); building downgrade plan");
			UpdatePlan<FileToDownloadWithCode> plan = new UpdatePlan<>(false, newState);
			PuppetHandler.updateTitle("title.downgrading", false);
			PuppetHandler.updateSubtitle("subtitle.calculating");
			boolean yappedAboutConsistency = false;
			int downgrades = ourVersion.code() - theirVersion.code();
			Log.debug("Downgrade spans "+downgrades+" version step(s): "+ourVersion.code()+" -> "+theirVersion.code());
			// Walk patches backwards: from ourVersion down to theirVersion+1.
			// Each patch N describes the forward change; reversing it means the desired
			// state is from_hash/from_size and the expected current state is to_hash/to_size.
			for (int i = 0; i < downgrades; i++) {
				int code = ourVersion.code() - i;
				Log.debug("Processing patch "+code+" (step "+(i+1)+" of "+downgrades+")");
				JsonObject ver = RequestHelper.loadJson(src.resolve(Util.uriOfPath("versions/"+code+".json")), 16*M,
						src.resolve(Util.uriOfPath("versions/"+code+".sig")));
				checkManifestFlavor(ver, "update", it -> it == 1);
				HashFunction func = HashFunction.byName(ver.getString("hash_function", DEFAULT_HASH_FUNCTION));
				Log.debug("Patch "+code+" uses hash function: "+func);
				var changes = ver.getArray("changes");
				Log.debug("Patch "+code+" contains "+changes.size()+" change(s)");
				for (Object o : changes) {
					if (!(o instanceof JsonObject file)) throw new IOException("Entry "+o+" in changes array is not an object");
					var path = file.getString("path");
					if (path == null) throw new IOException("Entry in changes array is missing path");
					var fromHash = file.getString("from_hash");
					if (fromHash != null && fromHash.length() != func.sizeInHexChars()) throw new IOException(path+" in changes array from_hash "+fromHash+" is wrong length ("+fromHash.length()+" != "+func.sizeInHexChars()+")");
					long fromSize = file.getLong("from_size", -1);
					if (fromSize < 0) throw new IOException(path+" in changes array has invalid or missing from_size");
					var toHash = file.getString("to_hash");
					if (toHash != null && toHash.length() != func.sizeInHexChars()) throw new IOException(path+" in changes array to_hash "+toHash+" is wrong length ("+toHash.length()+" != "+func.sizeInHexChars()+")");
					long toSize = file.getLong("to_size", -1);
					if (toSize < 0) throw new IOException(path+" in changes array has invalid or missing to_size");
					Log.debug("Considering "+path+": forward patch was ["+func+"("+toHash+") size "+toSize+"] <- ["+func+"("+fromHash+") size "+fromSize+"]");
					if (fromSize == toSize && Objects.equals(fromHash, toHash)) {
						Log.warn(path+" in changes array has same from and to hash/size? Ignoring");
						continue;
					}
					JsonArray envs = file.getArray("envs");
					if (Agent.config().useEnvs() && !Iterables.contains(envs, Agent.config().detectedEnv())) {
						Log.info("Skipping "+path+" as it's not eligible for env "+Agent.config().detectedEnv());
						continue;
					}
					var flavors = file.getArray("flavors");
					if (flavors != null && !Iterables.intersects(flavors, ourFlavors)) {
						Log.info("Skipping "+path+" as it's not eligible for our selected flavors");
						continue;
					}
					// For a downgrade, the desired state is from_* and the blob URL uses from_hash.
					URI fallbackUrl = fromHash == null ? null : src.resolve(Util.uriOfPath(blobPath(fromHash)));
					URI url = fallbackUrl;
					Log.debug("Downgrade target for "+path+": desired state "+func+"("+fromHash+") size "+fromSize+(fromHash == null ? " (file deletion)" : " blob: "+fallbackUrl));
					FileToDownloadWithCode to = plan.files.get(path);
					if (to != null) {
						// This file was already seen in a later (higher-numbered) patch.
						// Consistency check: its expected current state (to_hash from that later patch)
						// should match to_hash from this patch (what the forward pass put there).
						Log.debug(path+" already in plan from patch "+to.code+"; performing multi-step consistency check");
						if (to.state.func() == func) {
							if (!Objects.equals(to.state.hash(), toHash) || to.state.size() != toSize) {
								throw new IOException("Bad downgrade: "+path+" in "+to.code+" expected current state "+to.state+
										", but "+code+" says forward result was "+func+"("+toHash+") size "+toSize);
							}
							Log.debug("Consistency check passed for "+path);
						} else if (!yappedAboutConsistency) {
							yappedAboutConsistency = true;
							Log.warn("Cannot perform consistency check on multi-downgrade due to mismatched hash functions");
						}
						// Update to the earlier (lower-numbered) desired state.
						Log.debug("Updating "+path+" desired state to earlier patch "+code+": "+func+"("+fromHash+") size "+fromSize);
						to.state = new FileState(func, fromHash, fromSize);
						to.code = code;
						to.fallbackUrl = fallbackUrl;
						to.url = url;
					} else {
						Log.debug("Adding "+path+" to downgrade plan: expected on disk "+func+"("+toHash+") size "+toSize+", target "+func+"("+fromHash+") size "+fromSize);
						to = new FileToDownloadWithCode();
						to.code = code;
						to.state = new FileState(func, fromHash, fromSize);
						to.fallbackUrl = fallbackUrl;
						to.url = url;
						// Expected current state on disk is what the forward pass wrote: to_hash/to_size.
						plan.expectedState.put(path, new FileState(func, toHash, toSize));
						plan.files.put(path, to);
					}
				}
			}
			// Second pass: for any file whose desired content was never the result of a forward patch in the range we
			// walked (e.g. it existed since before any of these versions), we need to try and use any set "url" fields.
			// Scan all versions up to the target downgrade version to find an explicit "url" field from any patch
			// entry where to_hash matches the desired hash, and use that instead.
			var needsUrlLookup = plan.files.entrySet().stream()
					.filter(e -> e.getValue().state.hash() != null
							&& Objects.equals(e.getValue().url, e.getValue().fallbackUrl))
					.collect(Collectors.toList());
			if (!needsUrlLookup.isEmpty()) {
				Log.debug("Scanning prior versions for explicit URLs for "+needsUrlLookup.size()+" file(s) that may not have hosted blobs");
				// cant just key by hash, because duplicate file contents. Also not just by path because of same naming across different versions
				var byPathAndHash = new java.util.HashMap<PathAndHash, FileToDownloadWithCode>();
				for (var e : needsUrlLookup) byPathAndHash.put(new PathAndHash(e.getKey(), e.getValue().state.hash()), e.getValue());
				for (int code = theirVersion.code(); code >= 1 && !byPathAndHash.isEmpty(); code--) {
					Log.debug("Scanning version "+code+" for explicit URLs");
					JsonObject ver;
					try {
						ver = RequestHelper.loadJson(src.resolve(Util.uriOfPath("versions/"+code+".json")), 16*M,
								src.resolve(Util.uriOfPath("versions/"+code+".sig")));
					} catch (FileNotFoundException e) {
						Log.debug("Version "+code+" not found, stopping URL scan");
						break;
					}
					for (Object o : ver.getArray("changes")) {
						if (!(o instanceof JsonObject file)) continue;
						var path = file.getString("path");
						var toHash = file.getString("to_hash");
						if (path == null || toHash == null) continue;
						var to = byPathAndHash.get(new PathAndHash(path, toHash));
						if (to == null) continue;
					String urlStr = RequestHelper.checkSchemeMismatch(src, file.getString("url"));
					String mirrorUrlStr = RequestHelper.checkSchemeMismatch(src, file.getString("mirror_url"));
					if (urlStr != null) {
						Log.debug("Found explicit URL for "+path+" (hash "+toHash+") in version "+code+": "+urlStr);
						to.url = new URI(urlStr);
						if (mirrorUrlStr != null) {
							to.mirrorUrl = new URI(mirrorUrlStr);
						}
						byPathAndHash.remove(new PathAndHash(path, toHash));
					}
					}
				}
				if (!byPathAndHash.isEmpty()) {
					Log.debug(byPathAndHash.size()+" file(s) have no explicit URL in any prior version; will rely on blob fallback URL");
				}
			}
			Log.debug("Downgrade plan complete: "+plan.files.size()+" file(s) to restore");
			return new CheckResult(ourVersion, theirVersion, plan, Collections.emptyMap());
		} else {
			Log.info("We appear to be up-to-date. Nothing to do");
			return new CheckResult(ourVersion, theirVersion, null, Collections.emptyMap());
		}
	}

	private static String blobPath(String hash) {
		return "blobs/"+hash.substring(0, 2)+"/"+hash;
	}

	private static int checkManifestFlavor(JsonObject manifest, String flavor, IntPredicate versionPredicate) throws IOException {
		if (!manifest.containsKey("unsup_manifest")) throw new IOException("unsup_manifest key is missing");
		String str = manifest.getString("unsup_manifest");
		if (str == null) throw new IOException("unsup_manifest key is not a string");
		int dash = str.indexOf('-');
		if (dash == -1) throw new IOException("unsup_manifest value does not contain a dash");
		String lhs = str.substring(0, dash);
		if (!(flavor.equals(lhs))) throw new IOException("Manifest is of flavor "+lhs+", but we expected "+flavor);
		String rhs = str.substring(dash+1);
		int rhsI;
		try {
			rhsI = Integer.parseInt(rhs);
		} catch (IllegalArgumentException e) {
			throw new IOException("unsup_manifest value right-hand side is not a number: "+rhs);
		}
		if (!versionPredicate.test(rhsI)) throw new IOException("Don't know how to parse "+str+" manifest (version too new)");
		return rhsI;
	}
	
}
