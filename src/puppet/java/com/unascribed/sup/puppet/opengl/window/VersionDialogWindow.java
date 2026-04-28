/*
 * This file is part of unsup.
 * Copyright © 2026 Exa Skye
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

import com.unascribed.sup.data.ColorChoice;
import com.unascribed.sup.data.Version;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.Translate;
import com.unascribed.sup.puppet.opengl.pieces.FontManager.Face;

import static com.unascribed.sup.puppet.opengl.util.GL.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLMouse.*;
import static org.lwjgl.sdl.SDLKeycode.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class VersionDialogWindow extends Window {

	private static final int ROW_HEIGHT = 36;
	private static final int PADDING = 12;
	private static final int SCROLLBAR_WIDTH = 10;
	private static final int BUTTON_HEIGHT = 28;
	private static final int BUTTON_MARGIN = 8;
	private static final int HINT_SIZE = 10;
	private static final int SKIP_EXTRA_GAP = 20;
	private static final int FOOTER_HEIGHT = BUTTON_HEIGHT + BUTTON_MARGIN * 2;

	private final String name;
	private final String title;
	private final String body;
	private final List<Version> versions;
	private final int currentCode;

	/** Index of currently selected row (-1 = none, should not stay -1 after construction). */
	private int selectedIndex;
	private int hoveredIndex = -1;

	private float scroll = 0;
	private float lastScroll = 0;
	private float scrollVel = 0;
	private float maxScroll = 0;
	private long lastTick = System.nanoTime();

	private boolean needsRedraw = true;
	private boolean clickCursorActive = false;

	private boolean upPressed = false;
	private boolean downPressed = false;
	private boolean confirmPressed = false;
	private boolean cancelPressed = false;
	private boolean skipPressed = false;
	private boolean didKeyboardNav = false;

	public VersionDialogWindow(String name, String title, String body, List<Version> versions, int currentCode) {
		this.name = name;
		this.title = title;
		this.body = body;
		this.versions = versions;
		this.currentCode = currentCode;
		// pre-select the row matching the current version
		this.selectedIndex = 0;
		for (int i = 0; i < versions.size(); i++) {
			if (versions.get(i).code() == currentCode) {
				this.selectedIndex = i;
				break;
			}
		}
		this.enforceSize = false;
	}

	public void create(Window parent, double dpiScale) {
		create(parent, Translate.format(title), 460, 320, dpiScale);
	}

	@Override
	protected synchronized void customizeWindow() {
		SDL_SetWindowMinimumSize(handle, (int)(300 * dpiScale), (int)(200 * dpiScale));
	}

	@Override
	protected synchronized void onKeyDown(int key, int scancode, int mod, boolean repeat) {
		if (key == SDLK_UP || key == SDLK_TAB && (mod & SDL_KMOD_SHIFT) != 0) {
			upPressed = true;
			needsRedraw = true;
			didKeyboardNav = true;
		} else if (key == SDLK_DOWN || key == SDLK_TAB) {
			downPressed = true;
			needsRedraw = true;
			didKeyboardNav = true;
		} else if (key == SDLK_RETURN || key == SDLK_KP_ENTER || key == SDLK_SPACE) {
			confirmPressed = true;
			needsRedraw = true;
		} else if (key == SDLK_ESCAPE) {
			cancelPressed = true;
			needsRedraw = true;
		}
	}

	@Override
	protected synchronized void onScroll(float dwheelX, float dwheelY) {
		scrollVel -= dwheelY * 6;
	}

	@Override
	protected synchronized void onMouseMove(double x, double y) {
		needsRedraw = true;
	}

	@Override
	protected synchronized void onMouseClick() {
		needsRedraw = true;
	}

	@Override
	protected synchronized void onWindowCloseRequest() {
		Puppet.reportChoice(name, "closed");
		close();
	}

	@Override
	protected void setupGL() {
	}

	@Override
	protected synchronized boolean needsRerender() {
		return needsRedraw || Math.abs(scrollVel) > 1e-5;
	}

	@Override
	protected synchronized void renderInner() {
		long nsPerTick = TimeUnit.MILLISECONDS.toNanos(25);
		long time = System.nanoTime();

		// scroll physics
		if (maxScroll < 0) {
			scroll = 0;
			scrollVel = 0;
		}
		if (Math.abs(scrollVel) > 1e-5) {
			needsRedraw = true;
		} else {
			scrollVel = 0;
			lastTick = time;
		}
		while (lastTick < time) {
			lastTick += nsPerTick;
			lastScroll = scroll;
			scroll += scrollVel;
			if (scroll < 0) {
				scrollVel = (-scroll) / 4;
			} else if (scroll > maxScroll) {
				scrollVel = -(scroll - maxScroll) / 4;
			}
			scrollVel *= 0.6f;
		}
		float tickProgress = 1 - ((lastTick - time) / (float) nsPerTick);
		float visScroll = (1 - tickProgress) * lastScroll + tickProgress * this.scroll;

		// keyboard navigation
		if (upPressed) {
			selectedIndex = Math.max(0, selectedIndex - 1);
			upPressed = false;
		}
		if (downPressed) {
			selectedIndex = Math.min(versions.size() - 1, selectedIndex + 1);
			downPressed = false;
		}

		// scroll to keep selected row visible when navigating by keyboard
		if (didKeyboardNav) {
			float headerH = computeHeaderHeight();
			float rowTop = headerH + selectedIndex * ROW_HEIGHT - visScroll;
			float listH = height - headerH - FOOTER_HEIGHT;
			if (rowTop < 0) {
				scroll = headerH + selectedIndex * ROW_HEIGHT - headerH;
				if (scroll < 0) scroll = 0;
				scrollVel = 0;
			} else if (rowTop + ROW_HEIGHT > listH) {
				scroll = headerH + selectedIndex * ROW_HEIGHT - listH + ROW_HEIGHT;
				scrollVel = 0;
			}
			visScroll = scroll;
			didKeyboardNav = false;
		}

		boolean focused = (SDL_GetWindowFlags(handle) & SDL_WINDOW_INPUT_FOCUS) != 0;

		// clear background
		glColor(ColorChoice.BACKGROUND);
		drawRectXY(0, 0, width, height);

		float headerH = computeHeaderHeight();
		float listAreaH = height - headerH - FOOTER_HEIGHT;
		float listW = width - SCROLLBAR_WIDTH - 1;

		// draw header: title + body text
		glColor(ColorChoice.DIALOG);
		font.drawString(Face.BOLD, PADDING, PADDING + 18, 18, Translate.format(title));
		if (body != null && !body.isEmpty()) {
			font.drawWrapped(Face.REGULAR, PADDING, PADDING, headerH - PADDING, 14, width - PADDING * 2, Translate.format(body));
		}

		// divider between header and list
		glColor(ColorChoice.PROGRESSTRACK);
		drawRectXY(0, headerH - 1, width, headerH);

		// clip list drawing to list area via scissor (glScissor uses fb pixels, bottom-left origin)
		int scissorY = (int)((height - headerH - listAreaH) * dpiScale); // distance from bottom in fb pixels
		glScissor(0, scissorY, (int)(listW * dpiScale), (int)(listAreaH * dpiScale));
		glEnable(GL_SCISSOR_TEST);

		hoveredIndex = -1;
		for (int i = 0; i < versions.size(); i++) {
			float rowY = headerH + i * ROW_HEIGHT - visScroll;
			if (rowY + ROW_HEIGHT < headerH || rowY > height - FOOTER_HEIGHT) continue;

			Version v = versions.get(i);
			boolean isCurrent = v.code() == currentCode;
			boolean isSelected = i == selectedIndex;
			boolean isHovered = mouseX >= 0 && mouseX < listW
					&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

			if (isHovered) {
				hoveredIndex = i;
				if (mouseClicked) {
					selectedIndex = i;
					isSelected = true;
				}
			}

			// row background
			if (isSelected) {
				glColor(ColorChoice.BUTTON);
				drawRectXY(0, rowY, listW, rowY + ROW_HEIGHT);
				if (isHovered) {
					glColor(ColorChoice.BUTTONTEXT, 0.15f);
					drawRectXY(0, rowY, listW, rowY + ROW_HEIGHT);
				}
			} else if (isHovered) {
				glColor(ColorChoice.DIALOG, 0.12f);
				drawRectXY(0, rowY, listW, rowY + ROW_HEIGHT);
			}

			// row separator
			glColor(ColorChoice.PROGRESSTRACK);
			drawRectXY(PADDING, rowY + ROW_HEIGHT - 1, listW - PADDING, rowY + ROW_HEIGHT);

			// current-version bullet marker
			float textX = PADDING;
			if (isCurrent) {
				glColor(isSelected ? ColorChoice.BUTTONTEXT : ColorChoice.PROGRESS);
				font.drawString(Face.BOLD, textX, rowY + ROW_HEIGHT / 2f + 6, 14, "•");
				textX += 14;
			}

			// version name (left-aligned)
			glColor(isSelected ? ColorChoice.BUTTONTEXT : ColorChoice.DIALOG);
			font.drawString(Face.REGULAR, textX, rowY + ROW_HEIGHT / 2f + 6, 15, v.name());

			// version code (right-aligned, dimmed)
			String codeStr = "[" + v.code() + "]";
			float codeW = font.measureString(Face.REGULAR, 12, codeStr);
			glColor(isSelected ? ColorChoice.BUTTONTEXT : ColorChoice.SUBTITLE, isSelected ? 0.8f : 1f);
			font.drawString(Face.REGULAR, listW - codeW - PADDING, rowY + ROW_HEIGHT / 2f + 5, 13, codeStr);
		}

		glDisable(GL_SCISSOR_TEST);

		// scrollbar
		float totalContentH = versions.size() * ROW_HEIGHT;
		maxScroll = totalContentH - listAreaH;
		if (maxScroll > 0) {
			float knobH = Math.max(20, (listAreaH / totalContentH) * listAreaH);
			float knobY = headerH + (visScroll / maxScroll) * (listAreaH - knobH);
			float sbX = listW + 1;

			glColor(ColorChoice.PROGRESSTRACK);
			drawRectXY(sbX, headerH, sbX + SCROLLBAR_WIDTH, height - FOOTER_HEIGHT);

			glColor(ColorChoice.PROGRESS);
			drawRectXY(sbX + 2, knobY + 2, sbX + SCROLLBAR_WIDTH - 2, knobY + knobH - 2);
		} else {
			maxScroll = 0;
		}

		// footer with confirm / cancel / skip buttons
		float footerY = height - FOOTER_HEIGHT;
		glColor(ColorChoice.PROGRESSTRACK);
		drawRectXY(0, footerY, width, footerY + 1);

		String confirmLabel = Translate.format("option.switch");
		String cancelLabel = Translate.format("option.cancel");
		String skipLabel = Translate.format("option.skip");
		float confirmW = font.measureString(Face.BOLD, 13, confirmLabel) + 24;
		float cancelW = font.measureString(Face.BOLD, 13, cancelLabel) + 24;
		float skipW = font.measureString(Face.BOLD, 13, skipLabel) + 24;

		float confirmX = width - confirmW - BUTTON_MARGIN;
		float cancelX = confirmX - cancelW - BUTTON_MARGIN;
		float skipX = cancelX - skipW - BUTTON_MARGIN - SKIP_EXTRA_GAP;
		float btnY = footerY + BUTTON_MARGIN;

		boolean confirmHover = mouseX >= confirmX && mouseX <= confirmX + confirmW
				&& mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;
		boolean cancelHover = mouseX >= cancelX && mouseX <= cancelX + cancelW
				&& mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;
		boolean skipHover = mouseX >= skipX && mouseX <= skipX + skipW
				&& mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;

		// ok button
		glColor(ColorChoice.BUTTON);
		drawRectWH(confirmX, btnY, confirmW, BUTTON_HEIGHT);
		if (confirmHover) {
			glColor(ColorChoice.BUTTONTEXT, 0.2f);
			drawRectWH(confirmX, btnY, confirmW, BUTTON_HEIGHT);
		}
		glColor(ColorChoice.BUTTONTEXT);
		font.drawString(Face.REGULAR, confirmX + (confirmW - font.measureString(Face.BOLD, 13, confirmLabel)) / 2,
				btnY + BUTTON_HEIGHT - 8, 13, confirmLabel);

		// cancel button
		glColor(ColorChoice.DIALOG, 0.15f);
		drawRectWH(cancelX, btnY, cancelW, BUTTON_HEIGHT);
		if (cancelHover) {
			glColor(ColorChoice.DIALOG, 0.25f);
			drawRectWH(cancelX, btnY, cancelW, BUTTON_HEIGHT);
		}
		glColor(ColorChoice.DIALOG);
		font.drawString(Face.REGULAR, cancelX + (cancelW - font.measureString(Face.BOLD, 13, cancelLabel)) / 2,
				btnY + BUTTON_HEIGHT - 8, 13, cancelLabel);

		// skip button
		glColor(ColorChoice.DIALOG, 0.15f);
		drawRectWH(skipX, btnY, skipW, BUTTON_HEIGHT);
		if (skipHover) {
			glColor(ColorChoice.DIALOG, 0.25f);
			drawRectWH(skipX, btnY, skipW, BUTTON_HEIGHT);
		}
		glColor(ColorChoice.DIALOG);
		font.drawString(Face.REGULAR, skipX + (skipW - font.measureString(Face.BOLD, 13, skipLabel)) / 2,
				btnY + BUTTON_HEIGHT - 8, 13, skipLabel);

		// hint text — left-aligned, vertically centred in the button row
		String hintText = Translate.format("dialog.version_selector.hint");
		float hintLength = font.measureString(Face.REGULAR, HINT_SIZE, hintText) + 24;
		float hintBaseline = footerY + PADDING + 6;
		glColor(ColorChoice.SUBTITLE, 0.7f);
		font.drawWrapped(Face.REGULAR, PADDING, PADDING, hintBaseline, HINT_SIZE, (hintLength / 1.6f), hintText);

		// cursor management
		boolean anyHover = hoveredIndex != -1 || confirmHover || cancelHover || skipHover;
		if (anyHover && !clickCursorActive) {
			clickCursorActive = true;
			Puppet.runOnMainThread(() -> { if (run) SDL_SetCursor(clickCursor); });
		} else if (!anyHover && clickCursorActive) {
			clickCursorActive = false;
			Puppet.runOnMainThread(() -> { if (run) SDL_SetCursor(defaultCursor); });
		}

		// handle confirm/cancel/skip actions
		if (confirmPressed || (mouseClicked && confirmHover)) {
			confirmPressed = false;
			Puppet.reportChoice(name, String.valueOf(versions.get(selectedIndex).code()));
			close();
		} else if (cancelPressed || (mouseClicked && cancelHover)) {
			cancelPressed = false;
			Puppet.reportChoice(name, "closed");
			close();
		} else if (skipPressed || (mouseClicked && skipHover)) {
			skipPressed = false;
			Puppet.reportChoice(name, "skip");
			close();
		}

		needsRedraw = false;
	}

	/** Height of the header area (title + optional body text + padding). */
	private float computeHeaderHeight() {
		// title line + padding above/below
		float h = PADDING;
		if (body != null && !body.isEmpty()) {
			// rough estimate: allow up to 3 lines of body text at size 14
			h += 14 * 1.5f * 3 + 4;
		}
		return h;
	}

}
