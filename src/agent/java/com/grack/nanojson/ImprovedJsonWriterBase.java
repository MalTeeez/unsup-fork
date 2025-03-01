/*
 * This file is part of unsup.
 * Copyright © 2020-2025 Una Kearney (unascribed) and contributors
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

package com.grack.nanojson;

import java.io.OutputStream;

/**
 * A version of JsonWriterBase with better formatting. The implementation classes are ASM
 * transformed to extend this one instead.
 */
class ImprovedJsonWriterBase<SELF extends ImprovedJsonWriterBase<SELF>> extends JsonWriterBase<SELF> {

	private boolean ignorePreValue = false;
	
	ImprovedJsonWriterBase(Appendable appendable, String indent) {
		super(appendable, indent);
	}
	
	ImprovedJsonWriterBase(OutputStream out, String indent) {
		super(out, indent);
	}
	
	@Override
	protected void preValue() {
		if (ignorePreValue) return;
		super.preValue();
	}

	@Override
	public SELF object() {
		preValue();
		if (indentString != null && indent > 0 && !first && !inObject) {
			appendNewLine();
			appendIndent();
		}
		try {
			ignorePreValue = true;
			super.object();
		} finally {
			ignorePreValue = false;
		}
		return castThis();
	}

	@Override
	public SELF array(String key) {
		super.array(key);
		if (indentString != null) indent++;
		return castThis();
	}

	@Override
	public SELF end() {
		if (stateIndex == 0)
			throw new JsonWriterException("Invalid call to end()");

		// if (inObject) { // UNSUP
			if (indentString != null) {
				indent--;
				appendNewLine();
				appendIndent();
			}
		if (inObject) { // UNSUP
			raw('}');
		} else {
			raw(']');
		}

		first = false;
		inObject = states.get(--stateIndex);
		return castThis();
	}

	@Override
	protected void preValue(String key) {
		super.preValue(key);
		if (indentString != null) raw(' ');
	}

}
