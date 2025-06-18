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

import com.github.bsideup.jabel.Desugar;

@Desugar
public record FlavorChoice(
	String id,
	String name,
	String description,
	boolean def
) {

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String id;
		private String name;
		private String description;
		private boolean def;

		private Builder() {
		}

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder def(boolean def) {
			this.def = def;
			return this;
		}

		public FlavorChoice build() {
			return new FlavorChoice(id, name, description, def);
		}
	}
	
	
	
}