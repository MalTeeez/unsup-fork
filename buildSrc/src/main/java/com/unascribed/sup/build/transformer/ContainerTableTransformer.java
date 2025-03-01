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
