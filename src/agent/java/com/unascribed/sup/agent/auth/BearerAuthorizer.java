package com.unascribed.sup.agent.auth;

public class BearerAuthorizer extends AbstractStaticHeaderAuthorizer {

	public BearerAuthorizer(String token) {
		super("Authorization", "Bearer "+token);
	}
	
}
