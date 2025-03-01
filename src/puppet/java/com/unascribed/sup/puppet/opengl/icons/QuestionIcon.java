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

import static com.unascribed.sup.puppet.opengl.util.GL.*;

// custom, but inspired by https://pictogrammers.com/library/mdi/icon/help-circle-outline/
class QuestionIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		drawCircle(0, 0, 24);
		glColorPacked3i(bg);
		drawCircle(0, 0, 20);
		glColorPacked3i(fg);
		drawCircleArc(0, -2, 8, 0, (Math.PI/2)*3);
		glBegin(GL_QUADS);
			glVertex2f(-1, 0);
			glVertex2f(1, 0);
			glVertex2f(1, 3);
			glVertex2f(-1, 3);
			
			glVertex2f(-1, 4);
			glVertex2f(1, 4);
			glVertex2f(1, 6);
			glVertex2f(-1, 6);
		glEnd();
		glColorPacked3i(bg);
		drawCircle(0, -2, 4);
	}
	
}
