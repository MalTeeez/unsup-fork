package com.unascribed.sup.build.transformer;

import nilloader.api.lib.asm.Opcodes;
import nilloader.api.lib.asm.tree.AbstractInsnNode;
import nilloader.api.lib.asm.tree.ClassNode;
import nilloader.api.lib.asm.tree.FieldNode;
import nilloader.api.lib.asm.tree.MethodInsnNode;
import nilloader.api.lib.asm.tree.MethodNode;
import nilloader.api.lib.mini.MiniTransformer;
import nilloader.api.lib.mini.annotation.Patch;

@Patch.Class("com/grack/nanojson/JsonWriterBase")
public class JsonWriterBaseTransformer extends MiniTransformer {

	@Override
	protected boolean modifyClassStructure(ClassNode clazz) {
		String b = "com/grack/nanojson/JsonWriterBase";
		for (FieldNode fn : clazz.fields) {
			if ((fn.access & Opcodes.ACC_PRIVATE) != 0) {
				fn.access = (fn.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
			}
		}
		for (MethodNode mn : clazz.methods) {
			if ((mn.access & Opcodes.ACC_PRIVATE) != 0) {
				mn.access = (mn.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
			}
			for (AbstractInsnNode ain : mn.instructions) {
				if (ain instanceof MethodInsnNode) {
					MethodInsnNode min = (MethodInsnNode)ain;
					if (min.owner.equals(b) && min.getOpcode() == Opcodes.INVOKESPECIAL) {
						min.setOpcode(Opcodes.INVOKEVIRTUAL);
					}
				}
			}
		}
		return false;
	}
	
}
