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

package com.unascribed.sup.puppet;

import java.io.IOException;
import java.io.InputStream;

import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.util.Resources;

import me.saharnooby.qoi.QOIDecoder;
import me.saharnooby.qoi.QOIImage;
import me.saharnooby.qoi.QOIUtil;

public class WindowIcons {

	public static final QOIImage lowres = load("unsup-16");
	public static final QOIImage highres = load("unsup");

	private static QOIImage load(String name) {
		try (InputStream in = new BrotliInputStream(Resources.open("assets/"+name+".qoi.br"))) {
			return QOIDecoder.decode(in, 4);
		} catch (IOException | NullPointerException e) {
			Puppet.log("ERROR", "Failed to load "+name+".qoi", e);
			return QOIUtil.createFromPixelData(new byte[4], 1, 1);
		}
	}
	
}
