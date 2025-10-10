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
import java.util.Optional;
import com.unascribed.sup.util.BiasedOptional;

public class SysProps {

	public static final BiasedOptional<Boolean> DEBUG = getBoolean(SysPropDefs.DEBUG, false);
	public static final BiasedOptional<Boolean> DEBUG_REQUESTS = getBoolean(SysPropDefs.DEBUG_REQUESTS, false);
	public static final BiasedOptional<Boolean> DEBUG_PAUSE_BEFORE_UPDATE = getBoolean(SysPropDefs.DEBUG_PAUSE_BEFORE_UPDATE, false);
	public static final BiasedOptional<Boolean> GUI_IN_STANDALONE = getBoolean(SysPropDefs.GUI_IN_STANDALONE, false);
	public static final BiasedOptional<Boolean> IGNORE_ENVS = getBoolean(SysPropDefs.IGNORE_ENVS, false);
	public static final BiasedOptional<Boolean> ABORT_ON_PUPPET_CRASH = getBoolean(SysPropDefs.ABORT_ON_PUPPET_CRASH, false);
	public static final BiasedOptional<Integer> DOWNLOAD_WORKERS = getInteger(SysPropDefs.DOWNLOAD_WORKERS, 6);
	
	@SuppressWarnings("deprecation")
	// todo else
	public static final BiasedOptional<Behavior> BEHAVIOR = getEnum(SysPropDefs.BEHAVIOR, Behavior.class,
			getBoolean(SysPropDefs.DISABLE_RECONCILIATION, false).orBias() ? Behavior.AUTO : Behavior.MANUAL);
	public enum Behavior {
		AUTO, SEMI, MANUAL;
		
		public boolean promptUpdates() { return this == MANUAL; }
		public boolean promptConflicts() { return this != AUTO; }
	}
	public static final BiasedOptional<String> LANGUAGE = getProperty(SysPropDefs.LANGUAGE, Locale.getDefault().toLanguageTag());
	public static final BiasedOptional<Boolean> DRY_RUN = getBoolean(SysPropDefs.DRY_RUN, false);
	public static final Optional<String> BOOTSTRAP_URL = getProperty(SysPropDefs.BOOTSTRAP_URL);
	public static final Optional<String> BOOTSTRAP_KEY = getProperty(SysPropDefs.BOOTSTRAP_KEY);
	
	
	public static final BiasedOptional<Boolean> PACKWIZ_CHANGE_FLAVORS = getBoolean(SysPropDefs.PACKWIZ_CHANGE_FLAVORS, false);
	

	public static final Optional<String> PUPPET_WRAPPER_COMMAND = getProperty(SysPropDefs.PUPPET_WRAPPER_COMMAND);
	public static final BiasedOptional<Boolean> PUPPET_PASS_ALL_LWJGL_ARGS = getBoolean(SysPropDefs.PUPPET_PASS_ALL_LWJGL_ARGS, false);
	
	public static final BiasedOptional<PuppetMode> PUPPET_MODE = getEnum(SysPropDefs.PUPPET_MODE, PuppetMode.class, PuppetMode.AUTO);
	public enum PuppetMode {
		AUTO, SWING, OPENGL;
	}
	
	

	private static Optional<String> getProperty(String prop) {
		return Optional.ofNullable(System.getProperty(prop));
	}
	
	private static BiasedOptional<String> getProperty(String prop, String bias) {
		return new BiasedOptional<>(getProperty(prop), bias);
	}
	
	private static BiasedOptional<Boolean> getBoolean(String prop, boolean bias) {
		return new BiasedOptional<>(getProperty(prop).map(Boolean::parseBoolean), bias);
	}
	
	private static BiasedOptional<Integer> getInteger(String prop, int bias) {
		return new BiasedOptional<>(getProperty(prop).map(Integer::parseInt), bias);
	}
	
	private static <E extends Enum<E>> BiasedOptional<E> getEnum(String prop, Class<E> clazz, E bias) {
		return new BiasedOptional<>(getProperty(prop).map(s -> Enum.valueOf(clazz, s.toUpperCase(Locale.ROOT))), bias);
	}
	
}
