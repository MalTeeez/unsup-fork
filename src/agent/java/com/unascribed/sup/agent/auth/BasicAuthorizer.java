package com.unascribed.sup.agent.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAuthorizer extends AbstractStaticHeaderAuthorizer {

	private BasicAuthorizer(String token) {
		super("Authorization", "Basic "+token);
	}
	
	public static BasicAuthorizer fromCredentials(String user, String pass) {
		return fromStapled(user+":"+pass);
	}
	
	public static BasicAuthorizer fromStapled(String stapled) {
		return fromToken(Base64.getEncoder().encodeToString(stapled.getBytes(StandardCharsets.UTF_8)));
	}
	
	public static BasicAuthorizer fromToken(String token) {
		return new BasicAuthorizer(token);
	}
	
	
}
