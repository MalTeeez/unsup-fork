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

package com.unascribed.sup.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FlavorGroup implements Serializable { // TODO serializable is temporary for easy debug
	public String id, name, description;
	public List<FlavorChoice> choices = new ArrayList<>();
	public /*transient*/ String defChoice, defChoiceName;
	
	public static class FlavorChoice implements Serializable { // TODO serializable is temporary for easy debug
		public String id;
		public String name;
		public String description;
		public boolean def;
	}
	
	public boolean isBoolean() {
		if (choices.size() == 2) {
			String a = choices.get(0).id;
			String b = choices.get(1).id;
			String on = id+"_on";
			String off = id+"_off";
			return (a.equals(on) && b.equals(off))
					|| (a.equals(off) && b.equals(on));
		}
		return false;
	}
}