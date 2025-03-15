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

import java.util.Locale;

public class SysProps {

	/**
	 * Enable verbose log output and disable some "friendly" output options.
	 */
	public static final boolean DEBUG = Boolean.getBoolean("unsup.debug");
	/**
	 * Use the Puppet even in standalone mode.
	 */
	public static final boolean GUI_IN_STANDALONE = Boolean.getBoolean("unsup.guiInStandalone");
	/**
	 * Assume yes to all overwrite/reconciliation queries.
	 */
	public static final boolean DISABLE_RECONCILIATION = Boolean.getBoolean("unsup.disableReconciliation");
	/**
	 * Pretend use_envs is set to false.
	 */
	public static final boolean IGNORE_ENVS = Boolean.getBoolean("unsup.ignoreEnvs");
	/**
	 * Exit the Agent if the Puppet crashes.
	 */
	public static final boolean ABORT_ON_PUPPET_CRASH = Boolean.getBoolean("unsup.abortOnPuppetCrash");
	
	/**
	 * Override the language rather than using the one detected by Java.
	 */
	public static final String LANGUAGE = System.getProperty("unsup.language");
	
	
	/**
	 * Force open the flavor selection dialog. Packwiz mode only.
	 */
	public static final boolean PACKWIZ_CHANGE_FLAVORS = Boolean.getBoolean("unsup.packwiz.changeFlavors");
	

	/**
	 * Wrap execution of the Puppet in this command.
	 */
	public static final String PUPPET_WRAPPER_COMMAND = System.getProperty("unsup.puppet.wrapperCommand");
	/**
	 * Pass all -Dorg.lwjgl.* arguments through to the Puppet. DO NOT USE TO APPLY GLFW-WAYLAND-MINECRAFT.
	 */
	public static final boolean PUPPET_PASS_ALL_LWJGL_ARGS = Boolean.getBoolean("unsup.puppet.passAllLwjglArgs");
	
	/**
	 * Set which mode the Puppet will use, or auto to automatically choose one.
	 */
	public static final PuppetMode PUPPET_MODE = PuppetMode.valueOf(System.getProperty("unsup.puppetMode", "auto").toUpperCase(Locale.ROOT));
	public enum PuppetMode {
		AUTO, SWING, OPENGL;
	}
	
	/**
	 * Set which platform the OpenGL Puppet will use, or auto to let GLFW choose.
	 */
	public static final PuppetPlatform PUPPET_PLATFORM = PuppetPlatform.valueOf(System.getProperty("unsup.puppet.opengl.platform", "auto").toUpperCase(Locale.ROOT));
	public enum PuppetPlatform {
		AUTO, WIN32, COCOA, WAYLAND, X11, NULL
	}
	
}
