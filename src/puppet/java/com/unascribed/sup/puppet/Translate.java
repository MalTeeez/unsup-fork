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

package com.unascribed.sup.puppet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Translate {

	private static final Map<String, BasicFormat> strings = new HashMap<>();
	private static final Set<String> brandedStrings = new HashSet<>();

	public static void addTranslation(String key, String value) {
		strings.put(key, BasicFormat.parse(value));
		if (key.endsWith(".branded")) {
			brandedStrings.add(key.substring(0, key.length()-8));
		}
	}

	@SuppressWarnings("unlikely-arg-type")
	public static String format(String key, Object... args) {
		if (key.isEmpty()) return "";
		String[] split = key.split("¤");
		if (split.length > 1) {
			int origLen = args.length;
			args = Arrays.copyOf(args, origLen+split.length-1);
			System.arraycopy(split, 1, args, origLen, split.length-1);
			for (int i = 1; i < args.length; i++) {
				if (strings.containsKey(args[i])) {
					args[i] = format((String)args[i], args);
				}
			}
		}
		String k = split[0];
		if (Puppet.modpackName != null && brandedStrings.contains(k)) {
			k = k+".branded";
			Object[] newArgs = new Object[args.length+1];
			System.arraycopy(args, 0, newArgs, 1, args.length);
			newArgs[0] = Puppet.modpackName;
			args = newArgs;
		}
		return strings.getOrDefault(k, BasicFormat.literal(key)).format(args);
	}

	public static String[] format(String[] keys) {
		String[] out = new String[keys.length];
		for (int i = 0; i < keys.length; i++) {
			out[i] = format(keys[i]);
		}
		return out;
	}

}
