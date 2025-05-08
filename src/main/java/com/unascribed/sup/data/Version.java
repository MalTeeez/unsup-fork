/*
 * This file is part of unsup.
 * Copyright © 2023, 2025 Una Kearney
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

import com.github.bsideup.jabel.Desugar;
import com.grack.nanojson.JsonObject;

@Desugar
public record Version(String name, int code) {

	public JsonObject toJson() {
		var obj = new JsonObject();
		obj.put("name", name);
		obj.put("code", code);
		return obj;
	}

	public static Version fromJson(JsonObject obj) {
		if (obj == null) return null;
		return new Version(obj.getString("name"), obj.getInt("code"));
	}

	@Override
	public String toString() {
		return name + " (" + code + ")";
	}
}