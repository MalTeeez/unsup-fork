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
