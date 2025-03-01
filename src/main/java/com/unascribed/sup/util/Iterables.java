/*
 * This file is part of unsup.
 * Copyright © 2023-2025 Una Kearney (unascribed) and contributors
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

import java.util.Objects;

public class Iterables {

	public static boolean contains(Iterable<?> arr, Object obj) {
		if (arr == null) return false;
		boolean anyMatch = false;
		for (Object en : arr) {
			if (Objects.equals(en, obj)) {
				anyMatch = true;
				break;
			}
		}
		if (!anyMatch) {
			return false;
		}
		return true;
	}

	public static boolean intersects(Iterable<?> a, Iterable<?> b) {
		if (a == null) {
			if (b == null) return true;
			return false;
		}
		boolean anyMatch = false;
		for (Object en : a) {
			if (contains(b, en)) {
				anyMatch = true;
				break;
			}
		}
		if (!anyMatch) {
			return false;
		}
		return true;
	}

}
