/*
 * This file is part of unsup.
 * Copyright © 2025 Exa Skye
 * https://git.sleeping.town/exa/unsup
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

import java.util.Optional;

import com.github.bsideup.jabel.Desugar;

import okhttp3.Request;

public interface Authorizer {

	void authorize(Request orig, Request.Builder req);

	static Optional<Authorizer> parse(String v) {
		String[] vspl = v.split(" ", 2);
		switch (vspl[0]) {
			case "Basic" -> {
				if (vspl[1].contains(":")) {
					return Optional.of(BasicAuthorizer.fromStapled(vspl[1]));
				}
				return Optional.of(BasicAuthorizer.fromToken(vspl[1]));
			}
			case "Bearer" -> {
				return Optional.of(new BearerAuthorizer(vspl[1]));
			}
			case "AWS4-HMAC-SHA256" -> {
				String[] pieces = vspl[1].split(":", 3);
				return Optional.of(new AWS4Authorizer(pieces[0], pieces[1], pieces.length >= 3 ? pieces[2] : "us-east-1"));
			}
			default -> {
				return Optional.empty();
			}
		}
	}

	static Optional<AuthorizerSpec> parseSpec(String prefix, String v) {
		return parse(v).map(a -> new AuthorizerSpec(prefix, a));
	}
	
	@Desugar
	public record AuthorizerSpec(String urlPrefix, Authorizer auth) {}
	
}
