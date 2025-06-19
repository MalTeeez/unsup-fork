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

package com.unascribed.sup.puppet.opengl.util;

import java.io.File;
import org.lwjgl.system.Platform;

import com.unascribed.sup.puppet.Puppet;

public class CJK {

	public static File getOSPreferredFont(String lang, boolean bold) {
		if (Platform.get() != Platform.WINDOWS) return null;
		File sysroot = new File(System.getenv("SystemRoot"));
		File fonts = new File(sysroot, "Fonts");
		File font = switch (lang) {
			case "zh-CN" -> new File(fonts, bold ? "msyhbd.ttc" : "msyh.ttc");
			case "zh-TW", "zh-HK" -> new File(fonts, bold ? "msjhbd.ttc" : "msjh.ttc");
			case "ja" -> new File(fonts, bold ? "meiryob.ttc" : "meiryo.ttc");
			case "ko" -> new File(fonts, bold ? "malgunbd.ttf" : "malgun.ttf");
			default -> {
				Puppet.log("WARN", "Couldn't find system preferred locale font for "+lang);
				yield null;
			}
		};
		if (font != null) {
			if (font.exists()) return font;
			Puppet.log("WARN", "Couldn't find system preferred locale font for "+lang+" - expected it to be at "+font+" (%SystemRoot%\\Fonts\\"+font.getName()+")");
		}
		return null;
	}
	
}
