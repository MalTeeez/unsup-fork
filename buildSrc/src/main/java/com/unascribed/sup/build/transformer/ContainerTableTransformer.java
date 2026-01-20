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

package com.unascribed.sup.build.transformer;

import nilloader.api.lib.mini.MiniTransformer;
import nilloader.api.lib.mini.PatchContext;
import nilloader.api.lib.mini.annotation.Patch;

@Patch.Class("com/moandjiezana/toml/Container$Table")
public class ContainerTableTransformer extends MiniTransformer {

	@Patch.Method("<init>(Ljava/lang/String;Z)V")
	public void patchInit(PatchContext ctx) {
		ctx.search(
			NEW("java/util/HashMap"),
			DUP(),
			INVOKESPECIAL("java/util/HashMap", "<init>", "()V")
		).erase();
		
		ctx.search(
			PUTFIELD("com/moandjiezana/toml/Container$Table", "values", "Ljava/util/Map;")
		).jumpBefore();
		
		ctx.add(
			NEW("java/util/LinkedHashMap"),
			DUP(),
			INVOKESPECIAL("java/util/LinkedHashMap", "<init>", "()V")
		);
	}
	
}
