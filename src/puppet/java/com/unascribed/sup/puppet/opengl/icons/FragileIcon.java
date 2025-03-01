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

// https://pictogrammers.com/library/mdi/icon/glass-fragile/
class FragileIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		glPushMatrix();
			glTranslatef(0, -3, 0);
			glScalef(12, 10, 1);
			drawCircle(0, 0, 1);
		glPopMatrix();
		glBegin(GL_QUADS);
			glVertex2f(-5, -10);
			glVertex2f(5, -10);
			glVertex2f(5.931f, -3.755f);
			glVertex2f(-5.931f, -3.755f);
			
			glVertex2f(-1, 1);
			glVertex2f(1, 1);
			glVertex2f(1, 8);
			glVertex2f(-1, 8);
			
			glVertex2f(-6, 8);
			glVertex2f(6, 8);
			glVertex2f(6, 10);
			glVertex2f(-6, 10);
			
			glColorPacked3i(bg);
			glVertex2f(1.540f, -10);
			glVertex2f(3.210f, -10);
			glVertex2f(1.790f, -6.5f);
			glVertex2f(-0.210f, -6.5f);
			
			glVertex2f(2.210f, -8);
			glVertex2f(4.210f, -8);
			glVertex2f(2.101f, -2.5f);
			glVertex2f(-0.210f, -2.5f);
		glEnd();
		glBegin(GL_TRIANGLES);
			glVertex2f(2.451f, -4);
			glVertex2f(4.460f, -4);
			glVertex2f(1, 0.75f);
		glEnd();
	}
	
}
