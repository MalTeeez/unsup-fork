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
