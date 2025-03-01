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

package com.unascribed.sup.puppet.opengl.icons;

import com.unascribed.sup.data.ColorChoice;

/*
 * "Mom can we have SVG"
 * "We have SVG at home"
 */
public interface Icon {
	
	Icon FRAGILE = FragileIcon::draw;
	Icon ALERT = AlertIcon::draw;
	Icon QUESTION = QuestionIcon::draw;
	Icon INFO = InfoIcon::draw;
	Icon ERROR = ErrorIcon::draw;
	Icon UPDATE = UpdateIcon::draw;
	
	void draw(int bg, int fg);
	default void draw(ColorChoice bg, ColorChoice fg) {
		draw(bg.get(), fg.get());
	}
	
}
