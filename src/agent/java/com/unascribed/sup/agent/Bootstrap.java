package com.unascribed.sup.agent;

import com.unascribed.sup.LibBootstrap;

public class Bootstrap {
	
	public static void main(String[] args) {
		LibBootstrap.bootstrap("com.unascribed.sup.agent.Agent",
				c -> c.getMethod("main", String[].class).invoke(null, (Object)args));
	}
	
	
	public static void premain(String arg) {
		LibBootstrap.bootstrap("com.unascribed.sup.agent.Agent",
				c -> c.getMethod("premain", String.class).invoke(null, arg));
	}

}
