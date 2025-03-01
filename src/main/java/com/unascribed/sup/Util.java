/*
 * This file is part of unsup.
 * Copyright © 2020-2023, 2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class Util {

	private static final String implVer = Util.class.getPackage().getImplementationVersion();
	
	public static final String VERSION = implVer == null ? "DEV" : implVer;
	public static final boolean DEVELOPMENT_ENVIRONMENT = implVer == null;

	/**
	 * Convert a string path into a URI, to perform proper escaping/etc.
	 * <p>
	 * Calling {@link URI#resolve(String)} will fail when the path contains characters that need
	 * escaping.
	 */
	public static URI uriOfPath(String path) throws URISyntaxException {
		return new URI(null, null, path, null);
	}
	
	public static void copy(InputStream from, OutputStream to) throws IOException {
		byte[] buf = new byte[16384];
		while (true) {
			int read = from.read(buf);
			if (read < 0) break;
			to.write(buf, 0, read);
		}
	}

}
