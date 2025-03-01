package com.unascribed.sup.data;

import java.util.function.ToIntFunction;

public enum ColorChoice {
	BACKGROUND(0x000000),
	TITLE(0xFFFFFF),
	SUBTITLE(0xAAAAAA),
	PROGRESS(0xFF0000),
	PROGRESSTRACK(0xAAAAAA),
	DIALOG(0xFFFFFF),
	BUTTON(0xFFFF00),
	BUTTONTEXT(0x000000),
	
	QUESTION(0xFF00FF),
	INFO(0x00FFFF),
	WARNING(0xFFFF00),
	ERROR(0xFF0000),
	;
	
	public static ToIntFunction<ColorChoice> delegate = c -> c.defaultValue;
	
	public final int defaultValue;

	ColorChoice(int defaultValue) {
		this.defaultValue = defaultValue;
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
