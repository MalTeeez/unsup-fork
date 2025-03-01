/*
 * This file is part of unsup.
 * Copyright © 2020-2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup.build.transformer;

import java.util.Iterator;

import nilloader.api.lib.asm.tree.ClassNode;
import nilloader.api.lib.asm.tree.FieldNode;
import nilloader.api.lib.asm.tree.MethodNode;
import nilloader.api.lib.mini.MiniTransformer;
import nilloader.api.lib.mini.PatchContext;
import nilloader.api.lib.mini.annotation.Patch;

@Patch.Class("com/moandjiezana/toml/Toml")
public class TomlTransformer extends MiniTransformer {

	@Override
	protected boolean modifyClassStructure(ClassNode clazz) {
		// remove Gson dependency
		Iterator<MethodNode> mi = clazz.methods.iterator();
		while (mi.hasNext()) {
			MethodNode n = mi.next();
			if (n.name.equals("to") || n.name.equals("<clinit>")) {
				mi.remove();
			}
		}
		Iterator<FieldNode> fi = clazz.fields.iterator();
		while (fi.hasNext()) {
			FieldNode n = fi.next();
			if (n.name.equals("DEFAULT_GSON")) {
				fi.remove();
				break;
			}
		}
		return false;
	}
	
	@Patch.Method("<init>(Lcom/moandjiezana/toml/Toml;)V")
	public void patchInit(PatchContext ctx) {
		// use LinkedHashMap as default storage
		ctx.search(
			NEW("java/util/HashMap"),
			DUP(),
			INVOKESPECIAL("java/util/HashMap", "<init>", "()V")
		).erase();
		
		ctx.search(
			INVOKESPECIAL("com/moandjiezana/toml/Toml", "<init>", "(Lcom/moandjiezana/toml/Toml;Ljava/util/Map;)V")
		).jumpBefore();
		
		ctx.add(
			NEW("java/util/LinkedHashMap"),
			DUP(),
			INVOKESPECIAL("java/util/LinkedHashMap", "<init>", "()V")
		);
	}
	
	@Patch.Method("toMap()Ljava/util/Map;")
	@Patch.Method("get(Ljava/lang/String;)Ljava/lang/Object;")
	public void patchToMapAndGet(PatchContext ctx) {
		// use LinkedHashMap to preserve order
		ctx.search(
			NEW("java/util/HashMap"),
			DUP(),
			ALOAD(0),
			GETFIELD("com/moandjiezana/toml/Toml", "values", "Ljava/util/Map;"),
			INVOKESPECIAL("java/util/HashMap", "<init>", "(Ljava/util/Map;)V")
		).erase();
		
		ctx.search(
			ASTORE(ctx.getMethodName().equals("toMap") ? 1 : 2)
		).jumpBefore();
		
		ctx.add(
			NEW("java/util/LinkedHashMap"),
			DUP(),
			ALOAD(0),
			GETFIELD("com/moandjiezana/toml/Toml", "values", "Ljava/util/Map;"),
			INVOKESPECIAL("java/util/LinkedHashMap", "<init>", "(Ljava/util/Map;)V")
		);
	}
	
	@Patch.Method("<init>(Lcom/moandjiezana/toml/Toml;Ljava/util/Map;)V")
	public void patchInternalInit(PatchContext ctx) {
		// unnecessary double-assignment
		ctx.search(
			ALOAD(0),
			NEW("java/util/HashMap"),
			DUP(),
			INVOKESPECIAL("java/util/HashMap", "<init>", "()V"),
			PUTFIELD("com/moandjiezana/toml/Toml", "values", "Ljava/util/Map;")
		).erase();
	}
	
}
