package com.unascribed.sup.puppet;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.brotli.dec.BrotliInputStream;

public class FontResources {

	public static InputStream get(String name) throws IOException {
		InputStream in = null;
		if (name.contains("!")) {
			String[] spl = name.split("!", 2);
			ZipInputStream zin = new ZipInputStream(get(spl[0]));
			ZipEntry en;
			while ((en = zin.getNextEntry()) != null) {
				if (en.getName().equals(spl[1])) {
					in = zin;
					break;
				}
			}
		} else {
			in = FontResources.class.getClassLoader().getResourceAsStream("com/unascribed/sup/assets/fonts/"+name);
		}
		
		if (in == null) return null;
		
		if (name.endsWith(".br")) {
			return new BrotliInputStream(in);
		} else if (name.endsWith(".gz")) {
			return new GZIPInputStream(in);
		}
		return in;
	}
	
}
