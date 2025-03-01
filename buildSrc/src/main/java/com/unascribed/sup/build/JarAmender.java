/*
 * This file is part of unsup.
 * Copyright © 2020-2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.TimeZone;

public class JarAmender {

	public static void amend(File zipFile, long timestamp) throws IOException {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Path zip = zipFile.toPath();
	
		FileSystem fs = FileSystems.newFileSystem(zip, (ClassLoader)null);
		try {
			FileTime time = FileTime.fromMillis(timestamp);
			for (Path d : fs.getRootDirectories()) {
				Files.walk(d).forEach(it -> {
					try {
						Files.getFileAttributeView(it, BasicFileAttributeView.class).setTimes(time, time, time);
					} catch (Throwable e) {}
				});
			}
		} finally {
			fs.close();
		}
	}
	
}
