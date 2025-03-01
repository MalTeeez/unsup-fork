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

package com.unascribed.sup.build.transformer;

import nilloader.api.lib.mini.MiniTransformer;
import nilloader.api.lib.mini.PatchContext;
import nilloader.api.lib.mini.annotation.Patch;

@Patch.Class("okhttp3.internal.publicsuffix.PublicSuffixDatabase")
public class PublicSuffixDatabaseTransformer extends MiniTransformer {

	@Patch.Method("readTheList()V")
	public void patchReadTheList(PatchContext ctx) {
		ctx.search(
			LDC("publicsuffixes.gz")
		).jumpAfter();
		ctx.add(
			POP(),
			LDC("publicsuffixes.br")
		);
		
		ctx.search(
			NEW("okio/GzipSource"),
			DUP(),
			ALOAD(3),
			INVOKESTATIC("okio/Okio", "source" ,"(Ljava/io/InputStream;)Lokio/Source;"),
			INVOKESPECIAL("okio/GzipSource", "<init>", "(Lokio/Source;)V")
		).erase();
		
		ctx.search(
			CHECKCAST("okio/Source")
		).jumpBefore();
		ctx.add(
			ALOAD(3),
			INVOKESTATIC("com/unascribed/sup/agent/OkHttpHooks", "wrapStream", "(Ljava/io/InputStream;)Lokio/Source;")
		);
	}
	
}
