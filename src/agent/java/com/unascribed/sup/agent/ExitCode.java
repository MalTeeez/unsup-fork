package com.unascribed.sup.agent;

public enum ExitCode {
	SUCCESS,
	CONFIG_ERROR,
	CONSISTENCY_ERROR,
	BUG,
	USER_REQUEST,
	;

	public AssertionError exit() {
		Agent.cleanup();
		System.exit(ordinal());
		throw new AssertionError("unreachable");
	}
}
