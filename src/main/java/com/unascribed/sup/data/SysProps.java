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

import static java.lang.Boolean.getBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;

import java.util.Locale;

public class SysProps {

	public static final boolean DEBUG = getBoolean(SysPropDefs.DEBUG);
	public static final boolean DEBUG_REQUESTS = getBoolean(SysPropDefs.DEBUG_REQUESTS);
	public static final boolean DEBUG_PAUSE_BEFORE_UPDATE = getBoolean(SysPropDefs.DEBUG_PAUSE_BEFORE_UPDATE);
	public static final boolean GUI_IN_STANDALONE = getBoolean(SysPropDefs.GUI_IN_STANDALONE);
	public static final boolean IGNORE_ENVS = getBoolean(SysPropDefs.IGNORE_ENVS);
	public static final boolean ABORT_ON_PUPPET_CRASH = getBoolean(SysPropDefs.ABORT_ON_PUPPET_CRASH);
	public static final int DOWNLOAD_WORKERS = getInteger(SysPropDefs.DOWNLOAD_WORKERS, 6);
	
	@SuppressWarnings("deprecation")
	public static final Behavior BEHAVIOR = Behavior.valueOf(getProperty(SysPropDefs.BEHAVIOR, getBoolean(SysPropDefs.DISABLE_RECONCILIATION) ? "auto" : "manual").toUpperCase(Locale.ROOT));
	public enum Behavior {
		AUTO, SEMI, MANUAL;
		
		public boolean promptUpdates() { return this == MANUAL; }
		public boolean promptConflicts() { return this != AUTO; }
	}
	public static final String LANGUAGE = getProperty("unsup.language");
	public static final boolean DRY_RUN = getBoolean(SysPropDefs.DRY_RUN);
	public static final String BOOTSTRAP_URL = getProperty(SysPropDefs.BOOTSTRAP_URL);
	public static final String BOOTSTRAP_KEY = getProperty(SysPropDefs.BOOTSTRAP_KEY);
	
	
	public static final boolean PACKWIZ_CHANGE_FLAVORS = getBoolean("unsup.packwiz.changeFlavors");
	

	public static final String PUPPET_WRAPPER_COMMAND = getProperty("unsup.puppet.wrapperCommand");
	public static final boolean PUPPET_PASS_ALL_LWJGL_ARGS = getBoolean("unsup.puppet.passAllLwjglArgs");
	
	public static final PuppetMode PUPPET_MODE = PuppetMode.valueOf(getProperty("unsup.puppetMode", "auto").toUpperCase(Locale.ROOT));
	public enum PuppetMode {
		AUTO, SWING, OPENGL;
	}
	
}
