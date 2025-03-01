package com.unascribed.sup.util;

import java.io.InputStream;
import java.net.URL;

import javax.annotation.Nullable;

public class Resources {
	
	private static String path(String name) {
		return "com/unascribed/sup/"+name;
	}

	public static @Nullable InputStream open(String name) {
		return Resources.class.getClassLoader().getResourceAsStream(path(name));
	}
	
	public static @Nullable URL get(String name) {
		return Resources.class.getClassLoader().getResource(path(name));
	}
	
}
