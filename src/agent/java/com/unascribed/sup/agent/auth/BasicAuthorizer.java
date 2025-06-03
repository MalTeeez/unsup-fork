/*
 * This file is part of unsup.
 * Copyright © 2025 Una Kearney
 * https://git.sleeping.town/unascribed/unsup
 *
 * unsup is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * unsup is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with unsup; if not, see <https://www.gnu.org/licenses/>.
 */

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
