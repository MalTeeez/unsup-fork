package com.unascribed.sup.agent;

import java.io.IOException;
import java.io.InputStream;

import org.brotli.dec.BrotliInputStream;

import okio.Okio;
import okio.Source;

public class OkHttpHooks {

	// Called by an ASM transformation done to OkHttp3
	// See buildSrc/src/main/java/com/unascribed/sup/build/transformer/PublicSuffixDatabaseTransformer.java
	public static Source wrapStream(InputStream in) throws IOException {
		return Okio.source(new BrotliInputStream(in));
	}
	
}
