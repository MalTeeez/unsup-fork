package com.unascribed.sup.puppet.opengl.util;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.system.MemoryUtil.NULL;

public class SDLUtil {

	public static void check(boolean success) {
		if (!success) throw new IllegalStateException("SDL error: " + SDL_GetError());
	}

	public static long check(long resultPointer) {
		if (resultPointer == NULL) throw new IllegalStateException("SDL error: " + SDL_GetError());
		return resultPointer;
	}

}
