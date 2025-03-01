package com.grack.nanojson;

import java.io.OutputStream;

/**
 * A version of JsonWriterBase with better formatting. The implementation classes are ASM
 * transformed to extend this one instead.
 */
class ImprovedJsonWriterBase<SELF extends ImprovedJsonWriterBase<SELF>> extends JsonWriterBase<SELF> {

	private boolean ignorePreValue = false;
	
	ImprovedJsonWriterBase(Appendable appendable, String indent) {
		super(appendable, indent);
	}
	
	ImprovedJsonWriterBase(OutputStream out, String indent) {
		super(out, indent);
	}
	
	@Override
	protected void preValue() {
		if (ignorePreValue) return;
		super.preValue();
	}

	@Override
	public SELF object() {
		preValue();
		if (indentString != null && indent > 0 && !first && !inObject) {
			appendNewLine();
			appendIndent();
		}
		try {
			ignorePreValue = true;
			super.object();
		} finally {
			ignorePreValue = false;
		}
		return castThis();
	}

	@Override
	public SELF array(String key) {
		super.array(key);
		if (indentString != null) indent++;
		return castThis();
	}

	@Override
	public SELF end() {
		if (stateIndex == 0)
			throw new JsonWriterException("Invalid call to end()");

		// if (inObject) { // UNSUP
			if (indentString != null) {
				indent--;
				appendNewLine();
				appendIndent();
			}
		if (inObject) { // UNSUP
			raw('}');
		} else {
			raw(']');
		}

		first = false;
		inObject = states.get(--stateIndex);
		return castThis();
	}

	@Override
	protected void preValue(String key) {
		super.preValue(key);
		if (indentString != null) raw(' ');
	}

}
