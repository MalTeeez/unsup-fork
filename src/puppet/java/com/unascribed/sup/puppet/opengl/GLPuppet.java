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

package com.unascribed.sup.puppet.opengl;

import org.lwjgl.opengl.GL;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.util.freetype.FreeType;

import com.unascribed.sup.Util;
import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.SysPropDefs;
import com.unascribed.sup.pieces.Latch;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.PuppetDelegate;
import com.unascribed.sup.puppet.Translate;
import com.unascribed.sup.puppet.WindowIcons;
import com.unascribed.sup.puppet.opengl.util.CachedSDLEvent;
import com.unascribed.sup.puppet.opengl.util.QDPNG;
import com.unascribed.sup.puppet.opengl.window.FlavorDialogWindow;
import com.unascribed.sup.puppet.opengl.window.ProgressWindow;
import com.unascribed.sup.util.Resources;
import com.unascribed.sup.util.SuppressFBWarnings;
import com.unascribed.sup.puppet.opengl.window.MessageDialogWindow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.unascribed.sup.puppet.opengl.util.SDLUtil.check;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLError.*;
import static org.lwjgl.sdl.SDLStdinc.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLHints.*;

public class GLPuppet {
	
	private static ProgressWindow mainWindow;
	
	public static boolean scaleOverridden = false;
	
	private static final Latch buildLatch = new Latch();
	private static final Latch mainVisibleLatch = new Latch();
	
	private static final List<Predicate<CachedSDLEvent>> eventListeners = new ArrayList<>();
	
	public static PuppetDelegate start() {
		// just a transliteration of https://wiki.archlinux.org/title/HiDPI plus some unsup-specific extras
		OptionalDouble oDpiScale = scanScale("unsup.scale", "sun.java2d.uiScale", "glass.gtk.uiScale",
				"UNSUP_SCALE", "QT_SCALE_FACTOR", "GDK_DPI_SCALE×GDK_SCALE", "ELM_SCALE");
		scaleOverridden = oDpiScale.isPresent();
		double dpiScale = oDpiScale.orElse(1);
		
		if (System.getProperty(SysPropDefs.PUPPET_PLATFORM) != null) {
			Puppet.log("WARN", "-Dunsup.puppet.opengl.platform no longer does anything - use the SDL_VIDEO_DRIVER environment variable instead");
		}
		
		boolean maybeWayland = switch (Platform.get()) {
			case WINDOWS, MACOSX -> false;
			case LINUX, FREEBSD -> System.getenv("WAYLAND_DISPLAY") != null;
		};
		
		if (maybeWayland && System.getenv("SDL_VIDEODRIVER") == null && System.getenv("SDL_VIDEO_DRIVER") == null) {
			SDL_SetHint(SDL_HINT_VIDEO_DRIVER, "wayland,x11");
		}
		
		Configuration.HARFBUZZ_LIBRARY_NAME.set(FreeType.getLibrary());
		Configuration.OPENGL_EXPLICIT_INIT.set(true);
		
		SDL_SetMemoryFunctions(
				MemoryUtil::nmemAllocChecked,
				MemoryUtil::nmemCallocChecked,
				MemoryUtil::nmemReallocChecked,
				MemoryUtil::nmemFree);
		
		check(SDL_SetAppMetadata("unsup", Util.VERSION, "com.unascribed.sup"));
		check(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_URL_STRING, "https://git.sleeping.town/unascribed/unsup"));
		check(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_CREATOR_STRING, "Una Kearney"));
		check(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_COPYRIGHT_STRING, "Copyright (c) 2020 - 2025 Una Kearney and contributors. Released under the GNU LGPLv3"));
		check(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_TYPE_STRING, "application"));

		if (!SDL_Init(SDL_INIT_VIDEO)) {
			Puppet.log("ERROR", "Failed to initialize SDL: "+SDL_GetError());
			return null;
		}
		
		check(SDL_GL_LoadLibrary((ByteBuffer)null));
		GL.create(SDLVideo::SDL_GL_GetProcAddress);
		
		mainWindow = new ProgressWindow();
		
		if ("wayland".equals(SDL_GetCurrentVideoDriver())) {
			try {
				Files.createDirectories(new File(".unsup-tmp").toPath());
				File icon = new File(".unsup-tmp/icon.png");
				try (FileOutputStream fos = new FileOutputStream(icon)) {
					fos.write(QDPNG.write(Puppet.icon == null ? WindowIcons.highres : Puppet.icon));
				}
				File desktop = new File(getApplicationsDir(), "com.unascribed.sup.desktop");
				try (FileOutputStream fos = new FileOutputStream(desktop);
						InputStream is = Resources.open("assets/unsup.desktop")) {
					Util.copy(is, fos);
					// if your linux system isn't configured to use UTF-8 then i can't help you
					fos.write(icon.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
					fos.write('\n');
				}
				desktop.deleteOnExit();
				icon.deleteOnExit();
			} catch (Throwable t) {}
		}
		
		Puppet.runOnMainThread(() -> {
			var ev = SDL_Event.calloc();
			var cev = new CachedSDLEvent(ev);
			Puppet.sched.scheduleWithFixedDelay(() -> {
				Puppet.runOnMainThread(() -> {
					while (SDL_PollEvent(ev)) {
						var iter = eventListeners.iterator();
						while (iter.hasNext()) {
							if (!iter.next().test(cev)) {
								iter.remove();
							}
						}
					}
				});
			}, 0, 30, TimeUnit.MILLISECONDS);
		});
		
		return new PuppetDelegate() {
			
			@Override
			public void build() {
				Puppet.runOnMainThread(() -> {
					mainWindow.create(null, Translate.format("dialog.progress.title", Util.VERSION), 480, 80, dpiScale);
					buildLatch.release();
				});
			}
			
			@Override
			public void setVisible(boolean visible) {
				Puppet.slow.execute(() -> {
					buildLatch.awaitUninterruptibly();
					mainWindow.setVisible(visible);
					if (visible) {
						Puppet.runOnMainThread(mainVisibleLatch::release);
					}
				});
			}
			
			@Override
			public void setTitle(String title) {
				synchronized (mainWindow) {
					mainWindow.title = Translate.format(title);
					mainWindow.needsFullRedraw = true;
				}
			}
			
			@Override
			public void setSubtitle(String subtitle) {
				synchronized (mainWindow) {
					mainWindow.downloading = null;
					mainWindow.subtitle = Translate.format(subtitle);
					mainWindow.needsFullRedraw = true;
				}
			}
			
			@Override
			public void setDownloading(String[] files) {
				synchronized (mainWindow) {
					mainWindow.downloading = files;
					mainWindow.needsFullRedraw = true;
				}
			}
			
			@Override
			public void setProgressIndeterminate() {
				synchronized (mainWindow) {
					mainWindow.prog = -1;
				}
			}
			
			@Override
			public void setProgressDeterminate() {
				synchronized (mainWindow) {
					mainWindow.prog = 0;
				}
			}
			
			@Override
			public void setProgress(int permil) {
				synchronized (mainWindow) {
					mainWindow.prog = permil/1000f;
				}
			}
			
			@Override
			public void setDone() {
				if (!mainWindow.isVisible()) {
					Puppet.reportDone();
					return;
				}
				synchronized (mainWindow) {
					mainWindow.throbber.animateDone();
				}
			}
			
			@Override
			public void offerChangeFlavors(String name) {
				Puppet.exitOnDone = false;
				synchronized (mainWindow) {
					mainWindow.offerChangeFlavorsName = name;
					mainWindow.offerChangeFlavors = System.nanoTime();
					setTitle(Translate.format("title.done"));
					setSubtitle(Translate.format("subtitle.waiting_for_flavors"));
					setDone();
				}
			}
			
			@Override
			public void openMessageDialog(String name, String title, String body, AlertMessageType messageType, String[] options, String def) {
				Puppet.slow.execute(() -> {
					mainVisibleLatch.awaitUninterruptibly();
					MessageDialogWindow diag = new MessageDialogWindow(name, title, body, messageType, options, def);
					Puppet.runOnMainThread(() -> {
						diag.create(mainWindow, dpiScale);
						diag.setVisible(true);
					});
				});
			}
			
			@Override
			public void openFlavorDialog(String name, List<FlavorGroup> groups) {
				Puppet.slow.execute(() -> {
					mainVisibleLatch.awaitUninterruptibly();
					FlavorDialogWindow diag = new FlavorDialogWindow(name, groups);
					Puppet.runOnMainThread(() -> {
						diag.create(mainWindow, dpiScale);
						diag.setVisible(true);
					});
				});
			}
			
			@Override
			public void openChoiceDialog(String name, String title, String body, String[] options, String def) {
				openMessageDialog(name, title, body, AlertMessageType.NONE, options, def);
			}
		};
	}
	
	public static void listen(Predicate<CachedSDLEvent> listener) {
		Puppet.runOnMainThread(() -> {
			eventListeners.add(listener);
		});
	}

	private static double parseScale(String uiscale) throws NumberFormatException {
		if (uiscale.endsWith("dpi")) {
			return Double.parseDouble(uiscale.substring(0, uiscale.length()-3))/96;
		} else if (uiscale.endsWith("%")) {
			return Double.parseDouble(uiscale.substring(0, uiscale.length()-1))/100;
		} else {
			return Double.parseDouble(uiscale);
		}
	}
	
	private static OptionalDouble scanScale(String... keys) {
		for (String key : keys) {
			if (key.contains("×")) {
				String[] split = key.split("×");
				OptionalDouble left = scanScale(split[0]);
				OptionalDouble right = scanScale(split[1]);
				if (left.isPresent() && right.isPresent()) {
					return OptionalDouble.of(left.getAsDouble()*right.getAsDouble());
				} else if (left.isPresent()) {
					return left;
				} else if (right.isPresent()) {
					return right;
				}
			} else {
				String prop = System.getProperty(key);
				if (prop != null) {
					try {
						Puppet.log("DEBUG", "Discovered scale from sysprop "+key);
						return OptionalDouble.of(parseScale(prop));
					} catch (NumberFormatException e) {}
				}
				String env = System.getenv(key);
				if (env != null) {
					try {
						Puppet.log("DEBUG", "Discovered scale from envvar "+key);
						return OptionalDouble.of(parseScale(env));
					} catch (NumberFormatException e) {}
				}
			}
		}
		return OptionalDouble.empty();
	}

	@SuppressFBWarnings("ENV_USE_PROPERTY_INSTEAD_OF_ENV") // this is how the XDG spec tells you to do it
	private static File getApplicationsDir() {
		String home = System.getenv("HOME");
		if (home == null || home.trim().isEmpty()) {
			home = System.getProperty("user.home");
		}
		String dir = System.getenv("XDG_DATA_HOME");
		if (dir == null || dir.trim().isEmpty()) {
			dir = home+"/.local/share";
		}
		return new File(dir, "applications");
	}
	
}
