/*
 * This file is part of unsup.
 * Copyright © 2023, 2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup.agent.pieces;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class MemoryCookieJar implements CookieJar {
	private final List<Cookie> cookies = new ArrayList<>();

	@Override
	public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
		Iterator<Cookie> iter = this.cookies.iterator();
		while (iter.hasNext()) {
			Cookie c1 = iter.next();
			if (c1.expiresAt() < System.currentTimeMillis()) {
				iter.remove();
			} else {
				for (Cookie c2 : cookies) {
					if (c2.expiresAt() < System.currentTimeMillis()) continue;
					if (Objects.equals(c1.name(), c2.name())
								&& Objects.equals(c1.domain(), c2.domain())
								&& Objects.equals(c1.path(), c2.path())
								&& c1.secure() == c2.secure()
								&& c1.hostOnly() == c2.hostOnly()
							) {
						iter.remove();
					}
				}
			}
		}
		this.cookies.addAll(cookies);
	}

	@Override
	public synchronized List<Cookie> loadForRequest(HttpUrl url) {
		List<Cookie> out = new ArrayList<>();
		Iterator<Cookie> iter = cookies.iterator();
		while (iter.hasNext()) {
			Cookie c = iter.next();
			if (c.expiresAt() < System.currentTimeMillis()) {
				iter.remove();
			} else if (c.matches(url)) {
				out.add(c);
			}
		}
		return out;
	}
}