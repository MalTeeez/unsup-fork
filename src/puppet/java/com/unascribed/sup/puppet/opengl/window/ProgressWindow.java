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

package com.unascribed.sup.puppet.opengl.window;

import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.ColorChoice;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.Translate;
import com.unascribed.sup.puppet.opengl.pieces.GLThrobber;
import com.unascribed.sup.puppet.opengl.pieces.FontManager.Face;

import static org.lwjgl.sdl.SDLProperties.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLKeycode.*;
import static com.unascribed.sup.puppet.opengl.util.SDLUtil.*;
import static com.unascribed.sup.puppet.opengl.util.GL.*;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public class ProgressWindow extends Window {

	private static final long OFFER_DELAY = TimeUnit.SECONDS.toNanos(10);
	private static final float OFFER_DELAYf = OFFER_DELAY;
	
	private static final long TRANS_DELAY = TimeUnit.MILLISECONDS.toNanos(500);
	private static final float TRANS_DELAYf = TRANS_DELAY;
	
	public GLThrobber throbber = new GLThrobber();
	
	public String title = Translate.format("title.default");
	public String subtitle = "";
	public String[] downloading;
	public float prog = 0.5f;
	
	public boolean closeRequested = false;
	public String offerChangeFlavorsName;
	public long offerChangeFlavors;
	
	private long throbberTransition;
	
	private boolean enterPressed;
	
	@Override
	protected synchronized void customizeProperties(int props) {
		check(SDL_SetBooleanProperty(props, SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN, false));
	}
	
	@Override
	protected synchronized void onKeyDown(int key, int scancode, int mod, boolean repeat) {
		if (key == SDLK_RETURN || key == SDLK_SPACE || key == SDLK_KP_ENTER) {
			synchronized (this) {
				enterPressed = true;
			}
		}
	}

	@Override
	protected synchronized void onMouseMove(double x, double y) {}
	
	@Override
	protected synchronized void onMouseClick() {}
	
	@Override
	protected synchronized void onWindowCloseRequest() {
		if (offerChangeFlavors != 0) {
			offerChangeFlavors = System.nanoTime()-OFFER_DELAY;
		} else if (closeRequested) {
			MessageDialogWindow diag = new MessageDialogWindow("puppet_busy_notice", "dialog.busy.title",
					Translate.format("dialog.busy"), AlertMessageType.WARN, new String[] {"option.ok"}, "option.ok");
			double dpiScale;
			synchronized (this) {
				dpiScale = this.dpiScale;
			}
			Puppet.runOnMainThread(() -> {
				if (!run) return;
				diag.create(this, dpiScale);
				diag.setVisible(true);
			});
		} else {
			Puppet.reportCloseRequest();
			closeRequested = true;
		}
	}
	
	@Override
	protected void setupGL() {
		Puppet.log("DEBUG", "SDL Video Driver: "+SDL_GetCurrentVideoDriver());
		Puppet.log("DEBUG", "OpenGL Version: "+glGetString(GL_VERSION));
		Puppet.log("DEBUG", "OpenGL Renderer: "+glGetString(GL_RENDERER));
		Puppet.log("DEBUG", "OpenGL Vendor: "+glGetString(GL_VENDOR));
		Puppet.log("DEBUG", "Framebuffer Bits: r"+glGetInteger(GL_RED_BITS)+" g"+glGetInteger(GL_GREEN_BITS)+
				" b"+glGetInteger(GL_BLUE_BITS)+" a"+glGetInteger(GL_ALPHA_BITS)+" d"+glGetInteger(GL_DEPTH_BITS)+
				" s"+glGetInteger(GL_STENCIL_BITS)+" x"+glGetInteger(GL_SAMPLES));
	}
	
	@Override
	protected synchronized void renderInner() {
		boolean nfr = needsFullRedraw;
		if (nfr) {
			glClear(GL_COLOR_BUFFER_BIT);
		}
		throbber.render(40, 32, 40);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		if (throbberTransition != 0) {
			float x = (System.nanoTime()-throbberTransition)/TRANS_DELAYf;
			if (x > 1) {
				throbberTransition = 0;
			} else {
				if (x <= 0.5f) {
					x *= 2;
				} else if (x > 0.5f) {
					x = 1-((x-0.5f)*2);
					if (throbber.isDone()) {
						throbber = new GLThrobber();
						Puppet.exitOnDone = true;
					}
				}
				// https://easings.net/#easeInOutCubic
				x = (float)(x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
				glColor(ColorChoice.BACKGROUND, x);
				drawCircle(32, 40, 46);
			}
		}
		if (nfr) {
			glPushMatrix();
				glColor(ColorChoice.TITLE);
				font.drawString(Face.BOLD, 64, 31, 24, title);
			glPopMatrix();
			glPushMatrix();
				glColor(ColorChoice.SUBTITLE);
				String subtitle = this.subtitle;
				if (downloading != null) {
					List<String> downloadingTmp = new ArrayList<>();
					for (String s : downloading) downloadingTmp.add(s);
					int elided = 0;
					do {
						if (downloadingTmp.isEmpty()) {
							subtitle = Translate.format("subtitle.downloading_indeterminate");
							break;
						} else if (downloadingTmp.size() == 1 && elided == 0) {
							subtitle = Translate.format("subtitle.downloading", downloadingTmp.get(0));
							break;
						} else {
							StringJoiner sj = new StringJoiner(", ");
							downloadingTmp.forEach(sj::add);
							if (elided == 0) {
								subtitle = Translate.format("subtitle.downloading", sj);
							} else {
								subtitle = Translate.format("subtitle.downloading.elided", sj, elided);
							}
							elided++;
							downloadingTmp.remove(downloadingTmp.size()-1);
						}
					} while (font.measureString(Face.REGULAR, 14, subtitle) > width-64);
				}
				font.drawString(Face.REGULAR, 64, 52, 14, subtitle);
			glPopMatrix();
			needsFullRedraw = false;
		}
		
		float prog = this.prog;
		if (offerChangeFlavors != 0) {
			prog = (System.nanoTime()-offerChangeFlavors)/OFFER_DELAYf;
			
			if (prog > 1) {
				Puppet.reportChoice(offerChangeFlavorsName, "option.no");
				offerChangeFlavors = 0;
				subtitle = "";
				this.prog = -1;
				needsFullRedraw = true;
			}
			

			
			String text = Translate.format("option.change_flavors");
			float textW = font.measureString(Face.REGULAR, 12, text);
			float btnW = textW+24;
			float btnX = width-(btnW+8);
			float btnY = 8;
			float btnH = 27;
			boolean hover = mouseX >= btnX && mouseX <= btnX+btnW &&
					mouseY >= btnY && mouseY <= btnY+btnH;
			glColor(ColorChoice.BUTTON);
			drawRectWH(btnX, btnY, btnW, btnH);
			
			glColor(ColorChoice.BUTTONTEXT, 0.5f);
			drawRectWHII(btnX, btnY+20,
					btnW, 2,
					6, 0);
			
			if (hover) {
				glColor(ColorChoice.BUTTONTEXT, 0.25f);
				drawRectWH(btnX, btnY, btnW, btnH);
			}
			glColor(ColorChoice.BUTTONTEXT);
			font.drawString(Face.REGULAR, btnX+(btnW-textW)/2, btnY+18, 12, text);
			
			
			if (enterPressed || (mouseClicked && hover)) {
				Puppet.reportChoice(offerChangeFlavorsName, "option.yes");
				offerChangeFlavors = 0;
				throbberTransition = System.nanoTime();
			}
		}
		
		if (prog >= 0) {
			glDisable(GL_TEXTURE_2D);
			glBegin(GL_QUADS);
				glColor(ColorChoice.PROGRESSTRACK);
				glVertex2f(64, 70);
				glVertex2f(476, 70);
				glVertex2f(476, 76);
				glVertex2f(64, 76);
				
				glColor(ColorChoice.PROGRESS);
				glVertex2f(65, 71);
				glVertex2f(65+(prog*412), 71);
				glVertex2f(65+(prog*412), 75);
				glVertex2f(65, 75);
			glEnd();
		}
		
		enterPressed = false;
	}

}
