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

package com.unascribed.sup.agent;

import java.io.IOException;
import java.io.InputStream;

import org.brotli.dec.BrotliInputStream;

import okio.Okio;
import okio.Source;

public class OkHttpHooks {

	// Called by an ASM transformation done to OkHttp3
	// See buildSrc/src/main/java/com/unascribed/sup/build/transformer/PublicSuffixDatabaseTransformer.java
	public static Source wrapStream(InputStream in) throws IOException {
		return Okio.source(new BrotliInputStream(in));
	}
	
}
