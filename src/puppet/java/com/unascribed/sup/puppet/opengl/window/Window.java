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

package com.unascribed.sup.puppet.opengl.window;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import com.unascribed.sup.data.ColorChoice;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.opengl.GLPuppet;
import com.unascribed.sup.puppet.opengl.pieces.FontManager;
import com.unascribed.sup.puppet.opengl.pieces.OpenGLDebug;

import static com.unascribed.sup.puppet.WindowIcons.*;
import static com.unascribed.sup.puppet.opengl.util.GL.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLProperties.*;
import static org.lwjgl.sdl.SDLError.*;
import static org.lwjgl.sdl.SDLMouse.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLSurface.*;
import static org.lwjgl.sdl.SDLPixels.*;
import static com.unascribed.sup.puppet.opengl.util.SDLUtil.*;
import static org.lwjgl.system.MemoryUtil.*;

public abstract class Window {
	
	private static final Map<Class<? extends Window>, AtomicInteger> threadNumbers = Collections.synchronizedMap(new HashMap<>());
	
	private static final boolean MACOS = Platform.get() == Platform.MACOSX;
	private static final boolean OS_HAS_BROKEN_BUFFER_SWAP = Platform.get() == Platform.WINDOWS || MACOS;
	
	protected Window parent;
	private boolean visible;
	
	protected long handle;
	protected int width, height;
	protected int fbWidth, fbHeight;
	protected double dpiScale;
	private long glContext;
	
	protected double mouseX, mouseY;
	protected boolean mouseClicked;
	
	protected final FontManager font = new FontManager();
	protected int scratchTex;
	
	public volatile boolean run = true;
	
	public boolean needsFullRedraw = true;
	
	private long timeShown;
	private boolean honorNeedsRender = false;
	
	private Thread renderThread;
	
	protected boolean enforceSize = true;
	protected boolean updateDpiScaleByFramebuffer = true;
	protected long defaultCursor, clickCursor;
	
	protected synchronized void customizeProperties(int props) {}
	protected synchronized void customizeWindow() {}
	
	protected synchronized void onWindowCloseRequest() {}
	protected synchronized void onKeyDown(int key, int scancode, int mod, boolean repeat) {}
	protected synchronized void onScroll(float dwheelX, float dwheelY) {}
	
	public synchronized void create(Window parent, String title, int width, int height, double dpiScale) {
		if (!Puppet.isMainThread()) throw new IllegalStateException("Must be on main thread");
		
		this.parent = parent;
		
		this.width = width;
		this.height = height;
		
		this.dpiScale = dpiScale;

		int physW = (int)(width*dpiScale);
		int physH = (int)(height*dpiScale);
		
		int props = SDL_CreateProperties();
		check(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_X_NUMBER, SDL_WINDOWPOS_CENTERED));
		check(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_Y_NUMBER, SDL_WINDOWPOS_CENTERED));
		check(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER, physW));
		check(SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER, physH));
		check(SDL_SetStringProperty(props, SDL_PROP_WINDOW_CREATE_TITLE_STRING, title));
		check(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_OPENGL_BOOLEAN, true));
		check(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_HIGH_PIXEL_DENSITY_BOOLEAN, true));
		check(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_HIDDEN_BOOLEAN, true));
		check(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN, true));
		if (parent != null) {
			synchronized (parent) {
				check(SDL_SetPointerProperty(props, SDL_PROP_WINDOW_CREATE_PARENT_POINTER, parent.handle));
			}
			check(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_MODAL_BOOLEAN, true));
		}
		customizeProperties(props);
		
		check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 2));
		check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 1));
		
		check(SDL_GL_SetAttribute(SDL_GL_RED_SIZE, 8));
		check(SDL_GL_SetAttribute(SDL_GL_GREEN_SIZE, 8));
		check(SDL_GL_SetAttribute(SDL_GL_BLUE_SIZE, 8));
		check(SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE, 0));
		check(SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, 0));
		check(SDL_GL_SetAttribute(SDL_GL_STENCIL_SIZE, 0));
		check(SDL_GL_SetAttribute(SDL_GL_MULTISAMPLEBUFFERS, 1));
		check(SDL_GL_SetAttribute(SDL_GL_MULTISAMPLESAMPLES, 4));
		check(SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 1));
		
		handle = SDL_CreateWindowWithProperties(props);
		if (handle == 0) {
			throw new RuntimeException("Failed to create SDL window: "+SDL_GetError());
		}
		SDL_DestroyProperties(props);
		if (enforceSize) {
			float aspect = physW/(float)physH;
			SDL_SetWindowAspectRatio(handle, aspect, aspect);
			SDL_SetWindowMinimumSize(handle, physW, physH);
			SDL_SetWindowMaximumSize(handle, physW, physH);
		}
		Puppet.log("DEBUG", "Created window of size "+physW+"x"+physH);
		
		glContext = check(SDL_GL_CreateContext(handle));
		
		defaultCursor = check(SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_DEFAULT));
		clickCursor = check(SDL_CreateSystemCursor(SDL_SYSTEM_CURSOR_POINTER));
		
		int windowId = SDL_GetWindowID(handle);
		GLPuppet.listen(evt -> {
			switch (evt.type()) {
				case SDL_EVENT_WINDOW_EXPOSED, SDL_EVENT_WINDOW_FOCUS_GAINED, SDL_EVENT_WINDOW_FOCUS_LOST -> {
					if (evt.window().windowID() == windowId) {
						synchronized (this) {
							needsFullRedraw = true;
						}
					}
				}
				case SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED -> {
					if (evt.window().windowID() == windowId) {
						synchronized (this) {
							needsFullRedraw = true;
							updateScale("content scale update", SDL_GetWindowDisplayScale(handle));
						}
					}
				}
				case SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED -> {
					if (evt.window().windowID() == windowId) {
						synchronized (this) {
							this.fbWidth = evt.window().data1();
							this.fbHeight = evt.window().data2();
							needsFullRedraw = true;
						}
					}
				}
				case SDL_EVENT_WINDOW_RESIZED -> {
					if (evt.window().windowID() == windowId) {
						synchronized (this) {
							int newWidth, newHeight;
							try (var ms = MemoryStack.stackPush()) {
								var w = ms.mallocInt(1);
								var h = ms.mallocInt(1);
								check(SDL_GetWindowSize(handle, w, h));
								newWidth = w.get(0);
								newHeight = h.get(0);
							}
							Puppet.log("DEBUG", "Window size updated - "+newWidth+"x"+newHeight+" / "+fbWidth+"x"+fbHeight);
							double effectiveScale = dpiScale;
							if (!updateDpiScaleByFramebuffer) {
								effectiveScale = this.dpiScale;
							}
							this.width = (int) (newWidth/effectiveScale);
							this.height = (int) (newHeight/effectiveScale);
							needsFullRedraw = true;
						}
					}
				}
				case SDL_EVENT_MOUSE_MOTION -> {
					if (evt.motion().windowID() == windowId) {
						synchronized (this) {
							mouseX = evt.motion().x();
							mouseY = evt.motion().y();
							onMouseMove(mouseX, mouseY);
						}
					}
				}
				case SDL_EVENT_MOUSE_WHEEL -> {
					if (evt.wheel().windowID() == windowId) {
						synchronized (this) {
							onScroll(evt.wheel().x(), evt.wheel().y());
						}
					}
				}
				case SDL_EVENT_MOUSE_BUTTON_DOWN -> {
					if (evt.button().windowID() == windowId && evt.button().button() == 1) {
						synchronized (this) {
							mouseClicked = true;
							onMouseClick();
						}
					}
				}
				case SDL_EVENT_WINDOW_CLOSE_REQUESTED -> {
					if (evt.window().windowID() == windowId) {
						synchronized (this) {
							onWindowCloseRequest();
						}
					}
				}
				case SDL_EVENT_KEY_DOWN -> {
					if (evt.key().windowID() == windowId) {
						synchronized (this) {
							onKeyDown(evt.key().key(), evt.key().scancode(), evt.key().mod(), evt.key().repeat());
						}
					}
				}
			}
			return run;
		});
		
		SDL_GL_MakeCurrent(handle, glContext);
		if (Puppet.icon != null) {
			ByteBuffer px = memAlloc(Puppet.icon.getPixelData().length);
			px.put(Puppet.icon.getPixelData());
			px.flip();

			var surface = SDL_CreateSurfaceFrom(Puppet.icon.getWidth(), Puppet.icon.getHeight(), SDL_PIXELFORMAT_ABGR8888, px, Puppet.icon.getWidth()*4);
			if (surface == null) {
				Puppet.log("ERROR", "Failed to create window icon: "+SDL_GetError());
			} else {
				check(SDL_SetWindowIcon(handle, surface));
			}
			memFree(px);
		} else {
			ByteBuffer lowresPx = memAlloc(lowres.getPixelData().length);
			ByteBuffer highresPx = memAlloc(highres.getPixelData().length);
			lowresPx.put(lowres.getPixelData());
			highresPx.put(highres.getPixelData());
			lowresPx.flip();
			highresPx.flip();


			var surface = SDL_CreateSurfaceFrom(lowres.getWidth(), lowres.getHeight(), SDL_PIXELFORMAT_ABGR8888, lowresPx, lowres.getWidth()*4);
			if (surface == null) {
				Puppet.log("ERROR", "Failed to create window icon: "+SDL_GetError());
			} else {
				check(SDL_AddSurfaceAlternateImage(surface,
						SDL_CreateSurfaceFrom(highres.getWidth(), highres.getHeight(), SDL_PIXELFORMAT_ABGR8888, highresPx, highres.getWidth()*4)));
				check(SDL_SetWindowIcon(handle, surface));
			}
			memFree(lowresPx);
			memFree(highresPx);
		}
		
		customizeWindow();
		
		if (!GLPuppet.scaleOverridden) {
			float s = SDL_GetWindowDisplayScale(handle);
			
			if (s != 1) {
				updateScale("initial content scale update", dpiScale*s);
			}
		}
		
		if (SDL_GL_ExtensionSupported("GLX_EXT_swap_control_tear") || SDL_GL_ExtensionSupported("WGL_EXT_swap_control_tear")) {
			check(SDL_GL_SetSwapInterval(-1));
		} else {
			check(SDL_GL_SetSwapInterval(1));
		}
		
		GL.createCapabilities(MemoryUtil::memCallocPointer);
		
		int bg = ColorChoice.BACKGROUND.get();
		glClearColor(((bg >> 16)&0xFF)/255f, ((bg >> 8)&0xFF)/255f, ((bg >> 0)&0xFF)/255f, 1);
		
		glShadeModel(GL_SMOOTH);
		glDisable(GL_CULL_FACE);
		glDisable(GL_LIGHTING);
		glEnable(GL_MULTISAMPLE);
		
		setupGL();
		
		SDL_GL_MakeCurrent(NULL, NULL);
	}
	
	private void updateScale(String why, double x, double y) {
		double min = Math.min(x, y);
		if (Math.abs(dpiScale-min) > 0.025) {
			Puppet.log("DEBUG", "Updating DPI scale to "+pct(min)+" (chosen from "+pct(x)+"x"+pct(y)+") from "+pct(dpiScale)+" because of "+why);
			dpiScale = min;
		}
	}
	
	private void updateScale(String why, double s) {
		if (Math.abs(dpiScale-s) > 0.025) {
			Puppet.log("DEBUG", "Updating DPI scale to "+pct(s)+" from "+pct(dpiScale)+" because of "+why);
			dpiScale = s;
		}
	}
	
	private String pct(double d) {
		return String.format("%.1f%%", d*100);
	}

	protected abstract void setupGL();
	
	protected abstract void onMouseMove(double x, double y);
	protected abstract void onMouseClick();
	
	protected synchronized boolean needsRerender() {
		return true;
	}
	
	public synchronized boolean isVisible() {
		return visible;
	}
	
	public void setVisible(boolean visible) {
		long handle;
		synchronized (this) {
			handle = this.handle;
		}
		Puppet.runOnMainThread(() -> {
			if (!run) return;
			if (visible) {
				SDL_ShowWindow(handle);
			} else {
				SDL_HideWindow(handle);
			}
			synchronized (this) {
				this.visible = visible;
			}
		});
		if (renderThread == null) {
			renderThread = new Thread(() -> {
				SDL_GL_MakeCurrent(handle, glContext);
				
				GL.createCapabilities(MemoryUtil::memCallocPointer);
				
				if (Platform.get() == Platform.WINDOWS && "NVIDIA Corporation".equals(glGetString(GL_VENDOR))) {
					if (GL.getCapabilities().GL_KHR_debug) {
						// force Windows nVidia to disable "Threaded Optimizations"
						// https://github.com/CaffeineMC/sodium/blob/fe5fe6cf2184741bbf85da8a183dc145ff06b288/common/src/workarounds/java/net/caffeinemc/mods/sodium/client/compatibility/workarounds/nvidia/NvidiaWorkarounds.java#L125
						Puppet.log("DEBUG", "Applying Windows nVidia Threaded Optimizations workaround");
						glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
					} else {
						Puppet.log("WARN", "Want to apply Windows nVidia Threaded Optimizations workaround, but KHR_debug is not available");
					}
				}
				
				scratchTex = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, scratchTex);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
				
				OpenGLDebug.install();
				
				while (run) {
					if (!render()) {
						try {
							Thread.sleep(30);
						} catch (InterruptedException e) {
						}
					}
				}
				
				SDL_GL_MakeCurrent(NULL, NULL);
				
				Puppet.runOnMainThread(() -> {
			        memFree(GL.getCapabilities().getAddressBuffer());
			        GL.setCapabilities(null);
					SDL_GL_DestroyContext(glContext);
					SDL_DestroyCursor(defaultCursor);
					SDL_DestroyCursor(clickCursor);
					SDL_DestroyWindow(handle);
				});
			}, getClass().getSimpleName().replace("Window", "")+"#"+threadNumbers.computeIfAbsent(getClass(), k -> new AtomicInteger(1)).getAndIncrement());
			renderThread.start();
		}
	}

	public boolean render() {
		boolean rendered;
		long handle;
		synchronized (this) {
			handle = this.handle;
			if (!honorNeedsRender) {
				if (timeShown == 0) timeShown = System.nanoTime();
				long time = System.nanoTime()-timeShown;
				if (time > TimeUnit.MILLISECONDS.toNanos(500)) {
					honorNeedsRender = true;
				} else {
					needsFullRedraw = true;
				}
			}
			if (OS_HAS_BROKEN_BUFFER_SWAP) {
				needsFullRedraw = true;
			}
			if (!honorNeedsRender || needsRerender() || OS_HAS_BROKEN_BUFFER_SWAP) {
				if (fbWidth == 0) {
					try (var ms = MemoryStack.stackPush()) {
						var w = ms.mallocInt(1);
						var h = ms.mallocInt(1);
						check(SDL_GetWindowSizeInPixels(handle, w, h));
						fbWidth = w.get(0);
						fbHeight = h.get(0);
					}
					Puppet.log("DEBUG", "Grabbing framebuffer size in render - "+fbWidth+"x"+fbHeight);
				}
				
				glMatrixMode(GL_PROJECTION);
				glViewport(0, 0, fbWidth, fbHeight);
				glLoadIdentity();
				glOrtho(0, fbWidth, fbHeight, 0, 100, 1000);
				glMatrixMode(GL_MODELVIEW);
				glLoadIdentity();
				glTranslatef(0, 0, -200);
				if (updateDpiScaleByFramebuffer) {
					updateScale("per-frame framebuffer size check", fbWidth/(double)width, fbHeight/(double)height);
				}
				glScaled(dpiScale, dpiScale, 1);
				font.dpiScale = dpiScale;
				
				glDisable(GL_TEXTURE_2D);
				
				glColor3f(1, 1, 1);
		
				renderInner();
				
				mouseClicked = false;
				rendered = true;
			} else {
				rendered = false;
			}
		}
		if (rendered) {
			check(SDL_GL_SwapWindow(handle));
		}
		return rendered;
	}

	public void close() {
		if (!run || handle == 0) return;
		run = false;
		// Here lies my sanity and $200 (used to buy an M1 Mac Mini for debugging this and other problems)
		// macOS crashes your program with an inscrutable error if you call UI methods off the main thread
		// Without stacktraces, it's nearly impossible to find the problem
		// With enough perseverance and trying every fucking JVM implementation known to man, you can eventually get a stacktrace
		// Only to find it's a simple-ass mistake
		Puppet.runOnMainThread(() -> {
			synchronized (this) {
				SDL_HideWindow(handle);
			}
		});
	}
	
	protected abstract void renderInner();

}
