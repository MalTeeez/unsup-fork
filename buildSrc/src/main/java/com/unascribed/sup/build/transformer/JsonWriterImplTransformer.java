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

import nilloader.api.lib.asm.tree.AbstractInsnNode;
import nilloader.api.lib.asm.tree.ClassNode;
import nilloader.api.lib.asm.tree.FieldInsnNode;
import nilloader.api.lib.asm.tree.MethodInsnNode;
import nilloader.api.lib.asm.tree.MethodNode;
import nilloader.api.lib.mini.MiniTransformer;

public abstract class JsonWriterImplTransformer extends MiniTransformer {

	@Override
	protected boolean modifyClassStructure(ClassNode clazz) {
		String b = "com/grack/nanojson/JsonWriterBase";
		String i = "com/grack/nanojson/ImprovedJsonWriterBase";
		clazz.signature = clazz.signature.replace(b, i);
		clazz.superName = i;
		
		for (MethodNode mn : clazz.methods) {
			for (AbstractInsnNode ain : mn.instructions) {
				if (ain instanceof MethodInsnNode) {
					MethodInsnNode min = (MethodInsnNode)ain;
					if (min.owner.equals(b)) {
						min.owner = i;
					}
				} else if (ain instanceof FieldInsnNode) {
					FieldInsnNode fin = (FieldInsnNode)ain;
					if (fin.owner.equals(b)) {
						fin.owner = i;
					}
				}
			}
		}
		
		return false;
	}
	
}
