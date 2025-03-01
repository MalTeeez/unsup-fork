/*
 * This file is part of unsup.
 * Copyright © 2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup.puppet.opengl.pieces;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.KHRDebug.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLDebugMessageCallbackI;

import com.unascribed.sup.puppet.Puppet;

public class OpenGLDebug {
	@SuppressWarnings("unused")
	private static GLDebugMessageCallbackI callback;

	private static Map<Integer, String> strings = new HashMap<>();
	static {
		strings.put(GL_DEBUG_SOURCE_API, "API");
		strings.put(GL_DEBUG_SOURCE_WINDOW_SYSTEM, "Window System");
		strings.put(GL_DEBUG_SOURCE_SHADER_COMPILER, "Shader Compiler");
		strings.put(GL_DEBUG_SOURCE_THIRD_PARTY, "Third Party");
		strings.put(GL_DEBUG_SOURCE_APPLICATION, "Application");
		strings.put(GL_DEBUG_SOURCE_OTHER, "Other");
		strings.put(GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR, "Deprecated Behavior");
		strings.put(GL_DEBUG_TYPE_ERROR, "Error");
		strings.put(GL_DEBUG_TYPE_MARKER, "Marker");
		strings.put(GL_DEBUG_TYPE_OTHER, "Other");
		strings.put(GL_DEBUG_TYPE_PERFORMANCE, "Performance");
		strings.put(GL_DEBUG_TYPE_POP_GROUP, "Pop Group");
		strings.put(GL_DEBUG_TYPE_PORTABILITY, "Portability");
		strings.put(GL_DEBUG_TYPE_PUSH_GROUP, "Push Group");
		strings.put(GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR, "Undefined Behavior");
	}

	public static void install() {
		if (GL.getCapabilities().GL_KHR_debug) {
			glEnable(GL_DEBUG_OUTPUT);
			glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
			glDebugMessageCallback(callback = (sourceId, typeId, id, severity, length, messagePtr, userParam) -> {
				String message = memASCII(messagePtr);
				String source = strings.containsKey(sourceId) ? strings.get(sourceId) : "Unknown";
				String type = strings.containsKey(typeId) ? strings.get(typeId) : "Unknown";
				String flavor = "DEBUG";
				if (severity == GL_DEBUG_SEVERITY_NOTIFICATION) {
					if (sourceId == GL_DEBUG_SOURCE_SHADER_COMPILER && typeId == GL_DEBUG_TYPE_OTHER) {
						// Mesa's shader compiler emits a *lot* of garbage
						flavor = "TRACE";
					} else {
						flavor = "DEBUG";
					}
				} else if (severity == GL_DEBUG_SEVERITY_LOW) {
					flavor = "INFO";
				} else if (severity == GL_DEBUG_SEVERITY_MEDIUM) {
					flavor = "WARN";
				} else if (severity == GL_DEBUG_SEVERITY_HIGH) {
					flavor = "ERROR";
				}
				Puppet.log(flavor, source+" "+type+": "+message);
			}, NULL);
		}
	}

}
