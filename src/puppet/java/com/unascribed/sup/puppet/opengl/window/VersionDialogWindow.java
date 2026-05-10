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
import static org.lwjgl.sdl.SDLKeyboard.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class VersionDialogWindow extends Window {

	private static final int ROW_HEIGHT = 36;
	private static final int PADDING = 12;
	private static final int SCROLLBAR_WIDTH = 10;
	private static final int BUTTON_HEIGHT = 28;
	private static final int BUTTON_MARGIN = 8;
	private static final int HINT_SIZE = 10;
	private static final int SKIP_EXTRA_GAP = 20;
	private static final int SEARCH_BOX_HEIGHT = 28;
	private static final int SEARCH_BOX_MARGIN = 12;
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
	private long activeCursor = 0; // 0 = unset, otherwise the handle last passed to SDL_SetCursor

	private boolean upPressed = false;
	private boolean downPressed = false;
	private boolean confirmPressed = false;
	private boolean cancelPressed = false;
	private boolean skipPressed = false;
	private boolean didKeyboardNav = false;

	private String searchQuery = "";
	private boolean searchFocused = false;
	private List<Version> filteredVersions;

	private boolean draggingScrollbar = false;
	private float scrollbarDragOffsetY = 0;

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
		this.filteredVersions = new ArrayList<>(versions);
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
		} else if (key == SDLK_BACKSPACE) {
			if (!searchQuery.isEmpty()) {
				searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
				rebuildFilter();
				needsRedraw = true;
			}
		} else if (key == SDLK_ESCAPE) {
			if (searchFocused && !searchQuery.isEmpty()) {
				searchQuery = "";
				rebuildFilter();
				needsRedraw = true;
			} else {
				cancelPressed = true;
				needsRedraw = true;
			}
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
	protected synchronized void onTextInput(String text) {
		searchQuery += text;
		rebuildFilter();
		needsRedraw = true;
	}

	@Override
	protected synchronized void onMouseRelease() {
		if (draggingScrollbar) {
			draggingScrollbar = false;
			needsRedraw = true;
		}
	}

	private void rebuildFilter() {
		String q = searchQuery.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			filteredVersions = new ArrayList<>(versions);
		} else {
			filteredVersions = new ArrayList<>();
			for (Version v : versions) {
				if (v.name().toLowerCase(Locale.ROOT).contains(q)) {
					filteredVersions.add(v);
				}
			}
		}
		selectedIndex = Math.min(selectedIndex, Math.max(0, filteredVersions.size() - 1));
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
		return needsRedraw || Math.abs(scrollVel) > 1e-5 || draggingScrollbar;
	}

	@Override
	protected synchronized void renderInner() {
		long nsPerTick = TimeUnit.MILLISECONDS.toNanos(25);
		long time = System.nanoTime();

		// pre-compute geometry (needed by physics, drag, and rendering)
		float headerH = computeHeaderHeight();
		float listAreaH = height - headerH - FOOTER_HEIGHT;
		float listW = width - SCROLLBAR_WIDTH - 1;
		float totalContentH = filteredVersions.size() * ROW_HEIGHT;
		// use prior-frame maxScroll to seed knob size for drag (avoids chicken-and-egg)
		float sbKnobH = (maxScroll > 0 && listAreaH > 0)
				? Math.max(20, (listAreaH / totalContentH) * listAreaH) : 0;

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
			selectedIndex = Math.min(Math.max(0, filteredVersions.size() - 1), selectedIndex + 1);
			downPressed = false;
		}

		// scroll to keep selected row visible when navigating by keyboard
		if (didKeyboardNav) {
			float rowTop = headerH + selectedIndex * ROW_HEIGHT - visScroll;
			if (rowTop < 0) {
				scroll = (float) selectedIndex * ROW_HEIGHT;
				if (scroll < 0) scroll = 0;
				scrollVel = 0;
			} else if (rowTop + ROW_HEIGHT > listAreaH) {
				scroll = headerH + selectedIndex * ROW_HEIGHT - listAreaH + ROW_HEIGHT;
				scrollVel = 0;
			}
			visScroll = scroll;
			didKeyboardNav = false;
		}

		// scrollbar drag update — runs every frame while button is held
		if (draggingScrollbar) {
			if (mouseDown && maxScroll > 0 && sbKnobH > 0 && (listAreaH - sbKnobH) > 0) {
				float rawScroll = ((float) mouseY - scrollbarDragOffsetY - headerH) / (listAreaH - sbKnobH) * maxScroll;
				scroll = Math.max(0, Math.min(maxScroll, rawScroll));
				scrollVel = 0;
				visScroll = scroll;
				needsRedraw = true;
			} else if (!mouseDown) {
				draggingScrollbar = false;
			}
		}

		boolean focused = (SDL_GetWindowFlags(handle) & SDL_WINDOW_INPUT_FOCUS) != 0;

		// clear background
		glColor(ColorChoice.BACKGROUND);
		drawRectXY(0, 0, width, height);

		// draw header: title + body text
		glColor(ColorChoice.DIALOG);
		font.drawString(Face.BOLD, PADDING, PADDING / 2f + 18, 18, Translate.format(title));
		if (body != null && !body.isEmpty()) {
			// limit stops before the search box
			font.drawWrapped(Face.REGULAR, PADDING, PADDING,
					headerH - SEARCH_BOX_HEIGHT - SEARCH_BOX_MARGIN - PADDING / 2f,
					14, width - PADDING * 2, Translate.format(body));
		}

		// search box
		float sbBoxX = PADDING / 2f;
		float sbBoxY = headerH - SEARCH_BOX_HEIGHT - SEARCH_BOX_MARGIN / 2f;
		float sbBoxW = width - PADDING * 2;

		// focus / unfocus on click
		if (mouseClicked) {
			boolean inSearch = mouseX >= sbBoxX && mouseX <= sbBoxX + sbBoxW
					&& mouseY >= sbBoxY && mouseY <= sbBoxY + SEARCH_BOX_HEIGHT;
			if (inSearch) {
				if (!searchFocused) {
					searchFocused = true;
					Puppet.runOnMainThread(() -> { if (run) SDL_StartTextInput(handle); });
				}
			} else if (searchFocused) {
				searchFocused = false;
				Puppet.runOnMainThread(() -> { if (run) SDL_StopTextInput(handle); });
			}
		}

		// search box border (coloured when focused)
		glColor(searchFocused ? ColorChoice.PROGRESS : ColorChoice.PROGRESSTRACK);
		drawRectXY(sbBoxX - 1, sbBoxY - 1, sbBoxX + sbBoxW + 1, sbBoxY + SEARCH_BOX_HEIGHT + 1);
		glColor(ColorChoice.BACKGROUND);
		drawRectXY(sbBoxX, sbBoxY, sbBoxX + sbBoxW, sbBoxY + SEARCH_BOX_HEIGHT);

		// search box content: placeholder or query + cursor
		float textBaseline = sbBoxY + SEARCH_BOX_HEIGHT / 2f + 5;
		if (searchQuery.isEmpty()) {
			glColor(ColorChoice.SUBTITLE, 0.5f);
			font.drawString(Face.REGULAR, sbBoxX + 8, textBaseline, 13,
					Translate.format("dialog.version_selector.search"));
			if (searchFocused) {
				glColor(ColorChoice.DIALOG, 0.7f);
				font.drawString(Face.REGULAR, sbBoxX + 6, textBaseline, 13, "|");
			}
		} else {
			glColor(ColorChoice.DIALOG, 0.7f);
			font.drawString(Face.REGULAR, sbBoxX + 8, textBaseline, 13,
					searchQuery + (searchFocused ? "|" : ""));
		}

		// divider between header and list
		glColor(ColorChoice.PROGRESSTRACK);
		drawRectXY(0, headerH - 1, width, headerH);

		// clip list drawing to list area via scissor (glScissor uses fb pixels, bottom-left origin)
		int scissorY = (int) ((height - headerH - listAreaH) * dpiScale);
		glScissor(0, scissorY, (int) (listW * dpiScale), (int) (listAreaH * dpiScale));
		glEnable(GL_SCISSOR_TEST);

		hoveredIndex = -1;

		if (filteredVersions.isEmpty()) {
			// no-results placeholder
			glColor(ColorChoice.SUBTITLE, 0.5f);
			String noRes = Translate.format("dialog.version_selector.no_results");
			float noResW = font.measureString(Face.REGULAR, 13, noRes);
			font.drawString(Face.REGULAR, (listW - noResW) / 2f, headerH + listAreaH / 2f + 5, 13, noRes);
		} else {
			for (int i = 0; i < filteredVersions.size(); i++) {
				float rowY = headerH + i * ROW_HEIGHT - visScroll;
				if (rowY + ROW_HEIGHT < headerH || rowY > height - FOOTER_HEIGHT) continue;

				Version v = filteredVersions.get(i);
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
		}

		glDisable(GL_SCISSOR_TEST);

		// solid footer background — covers any list bleed-through from slinky scroll
		float footerY = height - FOOTER_HEIGHT;
		glColor(ColorChoice.BACKGROUND);
		drawRectXY(0, footerY, width, height);

		// scrollbar
		maxScroll = totalContentH - listAreaH;
		boolean sbHovered = false;
		if (maxScroll > 0) {
			float knobH = Math.max(20, (listAreaH / totalContentH) * listAreaH);
			float sbX = listW + 1;

			// click detection: start knob drag or click-to-jump
			if (mouseClicked && mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH) {
				float knobYForClick = Math.max(headerH, Math.min(headerH + listAreaH - knobH,
						headerH + (visScroll / maxScroll) * (listAreaH - knobH)));
				if (mouseY >= knobYForClick && mouseY < knobYForClick + knobH) {
					draggingScrollbar = true;
					scrollbarDragOffsetY = (float) mouseY - knobYForClick;
				} else if (mouseY >= headerH && mouseY < footerY) {
					// jump so knob centre lands at click point
					float newKnobY = (float) mouseY - knobH / 2f;
					newKnobY = Math.max(headerH, Math.min(headerH + listAreaH - knobH, newKnobY));
					scroll = (newKnobY - headerH) / (listAreaH - knobH) * maxScroll;
					scrollVel = 0;
					visScroll = scroll;
				}
			}

			float knobY = Math.max(headerH, Math.min(headerH + listAreaH - knobH,
					headerH + (visScroll / maxScroll) * (listAreaH - knobH)));
			sbHovered = !draggingScrollbar && mouseX >= sbX && mouseX <= sbX + SCROLLBAR_WIDTH
					&& mouseY >= knobY && mouseY < knobY + knobH;

			// track
			glColor(ColorChoice.PROGRESSTRACK);
			drawRectXY(sbX, headerH, sbX + SCROLLBAR_WIDTH, footerY);

			// knob — brighter when hovered or dragging
			if (draggingScrollbar || sbHovered) {
				glColor(ColorChoice.DIALOG, 0.55f);
			} else {
				glColor(ColorChoice.PROGRESS);
			}
			drawRectXY(sbX + 2, knobY + 2, sbX + SCROLLBAR_WIDTH - 2, knobY + knobH - 2);
		} else {
			maxScroll = 0;
			draggingScrollbar = false;
		}

		// footer divider + buttons
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

		boolean canConfirm = !filteredVersions.isEmpty();

		boolean confirmHover = mouseX >= confirmX && mouseX <= confirmX + confirmW
				&& mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;
		boolean cancelHover = mouseX >= cancelX && mouseX <= cancelX + cancelW
				&& mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;
		boolean skipHover = mouseX >= skipX && mouseX <= skipX + skipW
				&& mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;

		// confirm button (dimmed when no results)
		glColor(ColorChoice.BUTTON, canConfirm ? 1f : 0.4f);
		drawRectWH(confirmX, btnY, confirmW, BUTTON_HEIGHT);
		if (confirmHover && canConfirm) {
			glColor(ColorChoice.BUTTONTEXT, 0.2f);
			drawRectWH(confirmX, btnY, confirmW, BUTTON_HEIGHT);
		}
		glColor(ColorChoice.BUTTONTEXT, canConfirm ? 1f : 0.4f);
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

		// cursor management — text cursor over search box, pointer over interactives, default elsewhere
		boolean searchBoxHover = mouseX >= sbBoxX && mouseX <= sbBoxX + sbBoxW
				&& mouseY >= sbBoxY && mouseY <= sbBoxY + SEARCH_BOX_HEIGHT;
		boolean anyClickHover = hoveredIndex != -1 || (confirmHover && canConfirm)
				|| cancelHover || skipHover || sbHovered || draggingScrollbar;
		long desiredCursor = searchBoxHover ? textCursor : anyClickHover ? clickCursor : defaultCursor;
		if (desiredCursor != activeCursor) {
			activeCursor = desiredCursor;
			Puppet.runOnMainThread(() -> { if (run) SDL_SetCursor(desiredCursor); });
		}

		// handle confirm/cancel/skip actions
		if ((confirmPressed || (mouseClicked && confirmHover)) && canConfirm) {
			confirmPressed = false;
			Puppet.reportChoice(name, String.valueOf(filteredVersions.get(selectedIndex).code()));
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

	/** Height of the header area (title + optional body text + search box + padding). */
	private float computeHeaderHeight() {
		float h = PADDING;
		if (body != null && !body.isEmpty()) {
			// rough estimate: allow up to 3 lines of body text at size 14
			h += 14 * 3;
		}
		h += SEARCH_BOX_HEIGHT + SEARCH_BOX_MARGIN;
		return h;
	}

}
