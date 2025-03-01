package com.unascribed.sup.build.transformer;

import nilloader.api.lib.mini.MiniTransformer;
import nilloader.api.lib.mini.PatchContext;
import nilloader.api.lib.mini.annotation.Patch;

@Patch.Class("okhttp3.internal.publicsuffix.PublicSuffixDatabase")
public class PublicSuffixDatabaseTransformer extends MiniTransformer {

	@Patch.Method("readTheList()V")
	public void patchReadTheList(PatchContext ctx) {
		ctx.search(
			LDC("publicsuffixes.gz")
		).jumpAfter();
		ctx.add(
			POP(),
			LDC("publicsuffixes.br")
		);
		
		ctx.search(
			NEW("okio/GzipSource"),
			DUP(),
			ALOAD(3),
			INVOKESTATIC("okio/Okio", "source" ,"(Ljava/io/InputStream;)Lokio/Source;"),
			INVOKESPECIAL("okio/GzipSource", "<init>", "(Lokio/Source;)V")
		).erase();
		
		ctx.search(
			CHECKCAST("okio/Source")
		).jumpBefore();
		ctx.add(
			ALOAD(3),
			INVOKESTATIC("com/unascribed/sup/agent/OkHttpHooks", "wrapStream", "(Ljava/io/InputStream;)Lokio/Source;")
		);
	}
	
}
