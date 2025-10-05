package com.unascribed.sup.puppet;

import com.unascribed.sup.LibBootstrap;

public class Bootstrap {
	
	public static void main(String[] args) {
		LibBootstrap.bootstrap("com.unascribed.sup.puppet.Puppet",
				c -> c.getMethod("main", String[].class).invoke(null, (Object)args));
	}

}
