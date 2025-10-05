package com.unascribed.sup.puppet;

import com.unascribed.sup.bootstrap.Bootstrapper;

public class Bootstrap {
	
	public static void main(String[] args) {
		Bootstrapper.bootstrap("com.unascribed.sup.puppet.Puppet",
				c -> c.getMethod("main", String[].class).invoke(null, (Object)args));
	}

}
