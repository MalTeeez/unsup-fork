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

package com.unascribed.sup;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.unascribed.sup.util.SuppressFBWarnings;

public class PlatDetect {

	public static final OSType OS;
	public static final ArchType ARCH;
	
	public enum OSType {
		FREEBSD("freebsd", "FreeBSD", PUtil::getXDGCacheDir,
				ArchType.AMD64),
		LINUX("linux", "Linux", PUtil::getXDGCacheDir,
				ArchType.AMD64, ArchType.AARCH64, ArchType.ARM, ArchType.PPC64LE, ArchType.RISCV64),
		MACOS("macos", "macOS", PUtil::getMacCacheDir,
				ArchType.AMD64, ArchType.AARCH64),
		WINDOWS("windows", "Windows", PUtil::getWinCacheDir,
				ArchType.IA32, ArchType.AMD64, ArchType.AARCH64),
		UNSUPPORTED("unsupported", "Unsupported", PUtil::getDefaultCacheDir),
		;
		
		public final String lwjglName;
		public final String friendlyName;
		public final Supplier<File> cacheDirGetter;
		public final Set<ArchType> supportedArchitectures;
		
		OSType(String lwjglName, String friendlyName, Supplier<File> cacheDirGetter, ArchType... supportedArchitectures) {
			this.lwjglName = lwjglName;
			this.friendlyName = friendlyName;
			this.cacheDirGetter = cacheDirGetter;
			this.supportedArchitectures = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(supportedArchitectures)));
		}
		
	}
	
	public enum ArchType {
		AARCH64("arm64", "-arm64"),
		ARM("arm32", "-arm32"),
		PPC64LE("ppc64le", "-ppc64le"),
		RISCV64("riscv64", "-riscv64"),
		AMD64("amd64", ""),
		IA32("x86", "-x86"),
		UNSUPPORTED("", ""),
		;
		
		public final String apiName;
		public final String lwjglSuffix;
		ArchType(String apiName, String lwjglSuffix) {
			this.apiName = apiName;
			this.lwjglSuffix = lwjglSuffix;
		}
		
	}
	
	private static class PUtil {

		@SuppressFBWarnings("ENV_USE_PROPERTY_INSTEAD_OF_ENV") // this is how the XDG spec tells you to do it
		private static File getXDGCacheDir() {
			String home = System.getenv("HOME");
			if (home == null || home.trim().isEmpty()) {
				home = System.getProperty("user.home");
			}
			String dir = System.getenv("XDG_CACHE_HOME");
			if (dir == null || dir.trim().isEmpty()) {
				dir = home+"/.cache";
			}
			return new File(dir+"/unsup");
		}
		
		private static File getMacCacheDir() {
			return new File(new File(System.getProperty("user.home")), "Library/Caches/unsup");
		}
		
		private static File getWinCacheDir() {
			return new File(new File(System.getenv("APPDATA")), "Local/unsup");
		}
		
		private static File getDefaultCacheDir() {
			return new File(new File(System.getProperty("user.home")), ".unsup");
		}
		
	}
	
	static {
		String osName = System.getProperty("os.name");
		String osArch = System.getProperty("os.arch");
		OSType ourOs = OSType.UNSUPPORTED;
		ArchType ourArch = ArchType.UNSUPPORTED;
		// adapted from LWJGL3 Platform
		if (osName.startsWith("Windows")) {
			ourOs = OSType.WINDOWS;
		} else if (osName.startsWith("FreeBSD")) {
			ourOs = OSType.FREEBSD;
		} else if (osName.startsWith("Linux") || osName.startsWith("SunOS") || osName.startsWith("Unix")) {
			ourOs = OSType.LINUX;
		} else if (osName.startsWith("Mac OS X") || osName.startsWith("Darwin")) {
			ourOs = OSType.MACOS;
		}
		boolean is64Bit = osArch.contains("64") || osArch.startsWith("armv8");
		if (osArch.startsWith("arm") || osArch.startsWith("aarch")) {
			if (is64Bit) {
				ourArch = ArchType.AARCH64;
			} else {
				ourArch = ArchType.ARM;
			}
		} else if (osArch.startsWith("ppc")) {
			if ("ppc64le".equals(osArch)) {
				ourArch = ArchType.PPC64LE;
			}
		} else if (osArch.startsWith("riscv")) {
			if ("riscv64".equals(osArch)) {
				ourArch = ArchType.RISCV64;
			}
		} else {
			if (is64Bit) {
				ourArch = ArchType.AMD64;
			} else {
				ourArch = ArchType.IA32;
			}
		}
		OS = ourOs;
		ARCH = ourArch;
	}
	
}
