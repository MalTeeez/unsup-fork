package com.unascribed.sup.agent.util;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class SimpleProxySelector extends ProxySelector {

	private final List<Proxy> proxies;
	public SimpleProxySelector(Proxy proxy) {
		this.proxies = Collections.singletonList(proxy);
	}

	@Override
	public List<Proxy> select(URI uri) {
		return proxies;
	}

	@Override
	public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}

}
