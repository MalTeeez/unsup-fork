/*
 * This file is part of unsup.
 * Copyright © 2023, 2025 Una Kearney
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Bases {

	private static final String hex = "0123456789abcdef";

	public static String bytesToHex(byte[] bys) {
		return bytesToHex(bys, 0, bys.length);
	}
	
	public static String bytesToHex(byte[] bys, int ofs, int len) {
		StringBuilder sb = new StringBuilder(bys.length*2);
		for (int i = ofs; i < ofs+len; i++) {
			int hi = ((bys[i]&0xF0)>>4);
			int lo = bys[i]&0xF;
			sb.append(hex.charAt(hi));
			sb.append(hex.charAt(lo));
		}
		return sb.toString();
	}

	public static String b64ToString(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	public static String longToHex(long l) {
		return intToHex((l>>32L)&0xFFFFFFFF)+intToHex(l&0xFFFFFFFF);
	}

	public static String intToHex(long i) {
		// bad
		return Long.toHexString((i&0xFFFFFFFFL)|0xF00000000L).substring(1);
	}

}
