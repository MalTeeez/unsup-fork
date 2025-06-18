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