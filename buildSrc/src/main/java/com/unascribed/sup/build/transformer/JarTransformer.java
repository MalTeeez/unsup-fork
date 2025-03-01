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

package com.unascribed.sup.build.transformer;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import nilloader.api.ClassTransformer;

public class JarTransformer {

	public static void transform(File zipFile) throws IOException {
		System.setProperty("nil.debug.dumpMethodCodeOnSearchFailure", "true");
		
		Path zip = zipFile.toPath();
		
		List<ClassTransformer> trans = new ArrayList<>();
		trans.add(new PublicSuffixDatabaseTransformer());
		trans.add(new ContainerTableTransformer());
		trans.add(new TomlTransformer());
		trans.add(new JsonWriterBaseTransformer());
		trans.add(new JsonStringWriterTransformer());
		trans.add(new JsonAppendableWriterTransformer());
		
		FileSystem fs = FileSystems.newFileSystem(zip, (ClassLoader)null);
		try {
			for (Path d : fs.getRootDirectories()) {
				Files.walk(d).forEach(it -> {
					for (ClassTransformer ct : trans) {
						String p = it.toString();
						if (p.endsWith(".class")) {
							try {
								byte[] in = Files.readAllBytes(it);
								byte[] out = ct.transform(p.substring(1, p.length()-6), in);
								if (out != in) {
									BasicFileAttributes old = Files.getFileAttributeView(it, BasicFileAttributeView.class).readAttributes();
									Files.write(it, out);
									BasicFileAttributeView nw = Files.getFileAttributeView(it, BasicFileAttributeView.class);
									nw.setTimes(old.lastModifiedTime(), old.lastAccessTime(), old.creationTime());
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				});
			}
		} finally {
			fs.close();
		}
	}
	
}
