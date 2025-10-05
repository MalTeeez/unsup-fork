/*
 * This file is part of unsup.
 * Copyright © 2020-2021, 2023, 2025 Una Kearney
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

/**
 * Post-load API for accessing unsup data from within the launched program.
 */
public class Unsup {

	/**
	 * The version of unsup responsible for updating on this launch.
	 */
	public static final String UNSUP_VERSION = Util.VERSION;
	
	/**
	 * The last version of the source to be synced to the working directory by unsup.
	 */
	public static final String SOURCE_VERSION = retrieve("sourceVersion");
	
	/**
	 * {@code true} if unsup downloaded updates this launch.
	 */
	public static final boolean UPDATED = retrieve("updated");

	public static void poke() {}

	// deal with classloading disaster
	private static <T> T retrieve(String field) {
		try {
			return retrieve(Class.forName("com.unascribed.sup.agent.Agent", false, LibBootstrap.universe), field);
		} catch (ReflectiveOperationException | SecurityException e) {
			throw new AssertionError(e);
		}
	}

	private static <T> T retrieve(Class<?> clazz, String field) {
		try {
			return (T)clazz.getField(field).get(null);
		} catch (ReflectiveOperationException | SecurityException e) {
			throw new AssertionError(e);
		}
	}
	
}
