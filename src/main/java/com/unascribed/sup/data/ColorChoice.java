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

package com.unascribed.sup.data;

import java.util.function.ToIntFunction;

public enum ColorChoice {
	BACKGROUND(0x000000),
	TITLE(0xFFFFFF),
	SUBTITLE(0xAAAAAA),
	PROGRESS(0xFF0000),
	PROGRESSTRACK(0xAAAAAA),
	DIALOG(0xFFFFFF),
	BUTTON(0xFFFF00),
	BUTTONTEXT(0x000000),
	
	QUESTION(0xFF00FF),
	INFO(0x00FFFF),
	WARNING(0xFFFF00),
	ERROR(0xFF0000),
	;
	
	public static ToIntFunction<ColorChoice> delegate = c -> c.defaultValue;
	
	public final int defaultValue;

	ColorChoice(int defaultValue) {
		this.defaultValue = defaultValue;
	}

	public static int[] createLookup() {
		int[] rtrn = new int[values().length];
		for (ColorChoice choice : ColorChoice.values()) {
			rtrn[choice.ordinal()] = choice.defaultValue;
		}
		return rtrn;
	}
	
	public int get() {
		return delegate.applyAsInt(this);
	}
	
}
