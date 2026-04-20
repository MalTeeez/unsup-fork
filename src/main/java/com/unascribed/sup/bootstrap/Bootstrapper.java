/*
 * This file is part of unsup.
 * Copyright © 2025 Exa Skye
 * https://git.sleeping.town/exa/unsup
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

package com.unascribed.sup.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.brotli.dec.BrotliInputStream;

public class Bootstrapper {

	public static final URL location = Bootstrapper.class.getProtectionDomain().getCodeSource().getLocation();
	public static final InnerClassLoader universe; static {
		try {
			var p = Bootstrapper.class.getClassLoader();
			assert p != null;
			universe = Util.DEVELOPMENT_ENVIRONMENT ? null : new InnerClassLoader(p,
					new ZipInputStream(new BrotliInputStream(p.getResourceAsStream("com/unascribed/sup/data.jar.br"))));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
	
	public interface Invoker {
		void invoke(Class<?> c) throws ReflectiveOperationException;
	}
	
	public static void bootstrap(String mainclass, Invoker invoker) {
		try {
			invoker.invoke(Class.forName(mainclass, true, universe));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	public static class InnerClassLoader extends ClassLoader {
		static { registerAsParallelCapable(); }
		
		private Map<String, byte[]> files = new HashMap<>();

		public InnerClassLoader(ClassLoader parent, ZipInputStream data) {
			super(parent);
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
			if (files == null) throw new ClassNotFoundException(name+" - unsup agent phase has ended, this classloader is no longer available");
			if (name == null) throw new ClassNotFoundException("null");
			String path = name.replace('.', '/').concat(".class");
			var d = files.get(path);
			if (d != null) {
				return defineClass(name, d, 0, d.length);
			}
			throw new ClassNotFoundException(name);
		}
		
		@Override
		protected URL findResource(String name) {
			var d = files == null ? null : files.get(name);
			if (d != null) {
				try {
					return new URL("unsup-internal", null, -1, name, new URLStreamHandler() {
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

	}

}
