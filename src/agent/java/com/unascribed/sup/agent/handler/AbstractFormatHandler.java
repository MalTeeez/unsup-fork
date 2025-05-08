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

package com.unascribed.sup.agent.handler;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.github.bsideup.jabel.Desugar;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.unascribed.sup.agent.Agent;
import com.unascribed.sup.agent.Log;
import com.unascribed.sup.agent.PuppetHandler;
import com.unascribed.sup.agent.data.HashFunction;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.Version;
import com.unascribed.sup.pieces.NullRejectingMap;

public abstract class AbstractFormatHandler {
	
	public static final int K = 1024;
	public static final int M = K*1024;
	
	public static class FilePlan {
		public FileState state;
		public URI url;
		public URI fallbackUrl;
		public URI primerUrl;
		public boolean hostile;
		public boolean skip = false;
	}

	@Desugar
	public record FileState(HashFunction func, String hash, long size) {
		public static final FileState EMPTY = new FileState(null, null, 0);

		public boolean sizeMatches(long size) {
			if (this.size == -1) return true;
			return size == this.size;
		}

		@Override
		public String toString() {
			String s = func + "(" + hash + ")";
			if (size == -1) return s;
			return s + " size " + size;
		}

	}
	
	public static class UpdatePlan<F extends FilePlan> {
		public final boolean isBootstrap;
		public final Map<String, F> files = NullRejectingMap.create();
		public final Map<String, FileState> expectedState = NullRejectingMap.create();
		public final JsonObject newState;
		public boolean skipStateApplication = false;
		
		public UpdatePlan(boolean isBootstrap, JsonObject newState) {
			this.isBootstrap = isBootstrap;
			this.newState = newState;
		}
	}
	
	public static class CheckResult {
		public final Version ourVersion;
		public final Version theirVersion;
		public UpdatePlan<?> plan;
		public final Map<String, String> componentVersions;
		
		public CheckResult(Version ourVersion, Version theirVersion, UpdatePlan<?> plan, Map<String, String> componentVersions) {
			this.ourVersion = ourVersion;
			this.theirVersion = theirVersion;
			this.plan = plan;
			this.componentVersions = componentVersions;
		}
	}
	
	protected static JsonArray handleFlavorSelection(JsonArray ourFlavors, List<FlavorGroup> unpickedGroups, JsonObject newState, boolean forceDefault) {
		if (!unpickedGroups.isEmpty()) {
			ourFlavors = new JsonArray(ourFlavors == null ? Collections.emptyList() : ourFlavors);
			if (PuppetHandler.puppetOut != null) {
				PuppetHandler.tellPuppet(":expedite=openTimeout");
				ourFlavors.addAll(PuppetHandler.openFlavorSelectDialog("dialog.flavors.title", "", unpickedGroups));
			} else {
				for (FlavorGroup grp : unpickedGroups) {
					if (grp.defChoice != null) {
						Log.info("Selecting default choice "+grp.defChoiceName+" for flavor group "+grp.name);
						ourFlavors.add(grp.defChoice);
					} else if (forceDefault) {
						Log.debug("Forced to select first choice "+grp.choices.get(0).name+" as default for flavor group "+grp.name);
						ourFlavors.add(grp.choices.get(0).id);
					} else {
						Log.error("No choice provided for flavor group "+grp.name+" ("+grp.id+")");
						Agent.exit(Agent.EXIT_CONFIG_ERROR);
						return null;
					}
				}
			}
			newState.put("flavors", ourFlavors);
		}
		return ourFlavors;
	}

}
