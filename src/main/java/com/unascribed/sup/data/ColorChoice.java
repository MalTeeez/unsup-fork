/*
 * This file is part of unsup.
 * Copyright © 2025 Una Kearney
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
	BACKGROUND(0x000000, "background"),
	TITLE(0xFFFFFF, "title"),
	SUBTITLE(0xAAAAAA, "subtitle"),
	PROGRESS(0xFF0000, "progress"),
	PROGRESSTRACK(0xAAAAAA, "progress_track"),
	DIALOG(0xFFFFFF, "dialog"),
	BUTTON(0xFFFF00, "button"),
	BUTTONTEXT(0x000000, "button_text"),
	
	QUESTION(0xFF00FF, "question"),
	INFO(0x00FFFF, "info"),
	WARNING(0xFFFF00, "warning"),
	ERROR(0xFF0000, "error"),
	;
	
	public static ToIntFunction<ColorChoice> delegate = c -> c.defaultValue;
	
	public final int defaultValue;
	public final String configName;

	ColorChoice(int defaultValue, String configName) {
		this.defaultValue = defaultValue;
		this.configName = configName;
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
