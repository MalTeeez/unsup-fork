/*
 * This file is part of unsup.
 * Copyright © 2023, 2025 Exa Skye
 * https://git.sleeping.town/exa/unsup
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

import java.util.List;

import com.github.bsideup.jabel.Desugar;
import java.util.Collections;

@Desugar
public record FlavorGroup(
		String id, String name, String description,
		List<FlavorChoice> choices,
		String defChoice, String defChoiceName
	) {
	
	public boolean isBoolean() {
		if (choices.size() == 2) {
			String a = choices().get(0).id();
			String b = choices().get(1).id();
			String on = id()+"_on";
			String off = id()+"_off";
			return (a.equals(on) && b.equals(off))
					|| (a.equals(off) && b.equals(on));
		}
		return false;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String id;
		private String name;
		private String description;
		private List<FlavorChoice> choices = Collections.emptyList();
		private String defChoice;
		private String defChoiceName;

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

		public Builder choices(List<FlavorChoice> choices) {
			this.choices = Collections.unmodifiableList(choices);
			return this;
		}

		public Builder defChoice(String defChoice) {
			this.defChoice = defChoice;
			return this;
		}

		public Builder defChoiceName(String defChoiceName) {
			this.defChoiceName = defChoiceName;
			return this;
		}

		public FlavorGroup build() {
			return new FlavorGroup(id, name, description, choices, defChoice, defChoiceName);
		}
	}
}