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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.util.Resources;

public class FontResources {

	public static InputStream get(String name) throws IOException {
		InputStream in = null;
		if (name.contains("!")) {
			String[] spl = name.split("!", 2);
			ZipInputStream zin = new ZipInputStream(get(spl[0]));
			ZipEntry en;
			while ((en = zin.getNextEntry()) != null) {
				if (en.getName().equals(spl[1])) {
					in = zin;
					break;
				}
			}
			if (in == null) zin.close();
		} else {
			in = Resources.open("assets/fonts/"+name);
		}
		
		if (in == null) return null;
		
		if (name.endsWith(".br")) {
			return new BrotliInputStream(in);
		} else if (name.endsWith(".gz")) {
			return new GZIPInputStream(in);
		}
		return in;
	}
	
}
