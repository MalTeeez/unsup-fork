package com.unascribed.sup.agent.auth;

import okhttp3.Request;

public interface Authorizer {

	void authorize(Request orig, Request.Builder req);
	
}
