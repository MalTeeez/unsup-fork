package com.unascribed.sup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LibBootstrap {

	public static final URL location = LibBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
	public static final InnerClassLoader multiverse, universe;
	public static final URLClassLoader galaxy; static {
		try {
			var p = LibBootstrap.class.getClassLoader();
			multiverse = InnerClassLoader.createMultiverse(p);
			universe = (InnerClassLoader)Class.forName(InnerClassLoader.class.getName(), true, multiverse)
					.getMethod("createUniverse", ClassLoader.class).invoke(null, multiverse);
			Class.forName("com.unascribed.sup.lib.okhttp3.Interceptor", true, universe);
			galaxy = new URLClassLoader(new URL[] { location }, universe) {
				@Override
				protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
					// check parent *last*
					var loaded = findLoadedClass(name);
					if (loaded == null) {
						try {
							var c = findClass(name);
							if (resolve) resolveClass(c);
							return c;
						} catch (ClassNotFoundException e) {
							return getParent().loadClass(name);
						}
					} else {
						return loaded;
					}
				}
			};
		} catch (ReflectiveOperationException | IOException e) {
			throw new AssertionError(e);
		}
	}
	
	public interface Invoker {
		void invoke(Class<?> c) throws ReflectiveOperationException;
	}
	
	public static void bootstrap(String mainclass, Invoker invoker) {
		try {
			invoker.invoke(Class.forName(mainclass, true, galaxy));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	public static class InnerClassLoader extends ClassLoader {
		static { registerAsParallelCapable(); }
		
		private final String loaderName;
		private Map<String, byte[]> files = new HashMap<>();

		public InnerClassLoader(String loaderName, ClassLoader parent, ZipInputStream data) {
			super(parent);
			this.loaderName = loaderName;
			try (data) {
				var baos = new ByteArrayOutputStream();
				ZipEntry en;
				while ((en = data.getNextEntry()) != null) {
					if (en.isDirectory()) continue;
					baos.reset();
					Util.copy(data, baos);
					files.put(en.getName(), baos.toByteArray());
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
		public void forget() {
			files = null;
		}
		
		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			String path = name.replace('.', '/').concat(".class");
			var d = files.get(path);
			if (d != null) {
				return defineClass(name, d, 0, d.length);
			}
			throw new ClassNotFoundException(name);
		}
		
		@Override
		protected URL findResource(String name) {
			var d = files.get(name);
			if (d != null) {
				try {
					return new URL("unsup-internal", loaderName, -1, name, new URLStreamHandler() {
						@Override
						protected URLConnection openConnection(URL u) throws IOException {
							var bais = new ByteArrayInputStream(d);
							return new URLConnection(u) {
								@Override
								public void connect() throws IOException {}
								@Override
								public InputStream getInputStream() throws IOException {
									return bais;
								}
							};
						}
					});
				} catch (MalformedURLException e) {
					throw new AssertionError(e);
				}
			}
			return null;
		}

		public static InnerClassLoader createMultiverse(ClassLoader parent) throws IOException {
			return new InnerClassLoader("multiverse", parent,
					new ZipInputStream(new GZIPInputStream(parent.getResourceAsStream("com/unascribed/sup/lib/libs-bootstrap.jar.gz"))));
		}

		public static InnerClassLoader createUniverse(ClassLoader parent) throws IOException, ReflectiveOperationException {
			return new InnerClassLoader("universe", parent,
					new ZipInputStream((InputStream)Class.forName("com.unascribed.sup.lib.brotli.BrotliInputStream", true, parent)
							.getConstructor(InputStream.class).newInstance(parent.getResourceAsStream("com/unascribed/sup/lib/libs.jar.br"))));
		}
		
	}

}
