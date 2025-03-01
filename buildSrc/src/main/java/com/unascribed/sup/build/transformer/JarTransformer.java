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
