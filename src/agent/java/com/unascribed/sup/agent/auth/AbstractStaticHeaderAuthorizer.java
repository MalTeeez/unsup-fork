package com.unascribed.sup.agent.auth;

import okhttp3.Request;

public abstract class AbstractStaticHeaderAuthorizer implements Authorizer {

	private final String key, value;

	public AbstractStaticHeaderAuthorizer(String key, String value) {
		this.key = key;
		this.value = value;
	}
	
	@Override
	public void authorize(Request orig, Request.Builder req) {
		req.addHeader(key, value);
	}
	
}
