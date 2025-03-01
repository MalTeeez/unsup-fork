/*
 * This file is part of unsup.
 * Copyright © 2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup.util;

import java.io.InputStream;
import java.net.URL;

import javax.annotation.Nullable;

public class Resources {
	
	private static String path(String name) {
		return "com/unascribed/sup/"+name;
	}

	public static @Nullable InputStream open(String name) {
		return Resources.class.getClassLoader().getResourceAsStream(path(name));
	}
	
	public static @Nullable URL get(String name) {
		return Resources.class.getClassLoader().getResource(path(name));
	}
	
}
