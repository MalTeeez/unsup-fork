package com.unascribed.sup.build;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class TunableGZIPOutputStream extends GZIPOutputStream {

	public TunableGZIPOutputStream(OutputStream out) throws IOException {
		super(out);
	}

	public Deflater getDeflater() {
		return def;
	}
	
}
