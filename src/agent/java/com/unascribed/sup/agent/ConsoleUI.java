/*
 * This file is part of unsup.
 * Copyright © 2023-2025 Exa Skye
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

package com.unascribed.sup.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.unascribed.sup.agent.PuppetHandler.AlertOption;
import com.unascribed.sup.agent.PuppetHandler.AlertOptionType;
import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.ColorChoice;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.Version;

/**
 * Simple stdin/stdout TUI used when nogui is active and a real console is
 * available.
 * All methods degrade gracefully (with a warning) when stdin is not a TTY or has lower color options.
 */
public class ConsoleUI {

	/** True when stdin is a real interactive terminal. */
	public static final boolean INTERACTIVE = System.console() != null;

	/**
	 * True when the terminal supports ANSI escape codes (cursor movement,
	 * erase-line).
	 * Requires INTERACTIVE to be true as well.
	 */
	public static final boolean ANSI = INTERACTIVE && detectAnsi();

	/**
	 * True when the terminal supports ANSI color codes.
	 * 16-color support is universal wherever cursor codes work, so this equals
	 * ANSI. Suppressed when NO_COLOR is set.
	 */
	public static final boolean ANSI_COLOR = ANSI;

	/**
	 * True when the terminal supports 24-bit (truecolor) ANSI color sequences.
	 * Detected via COLORTERM=truecolor/24bit, or TERM containing "24bit"/"truecolor".
	 */
	public static final boolean ANSI_TRUECOLOR = ANSI_COLOR && detectTruecolor();

	/**
	 * True when the terminal supports 256-color ANSI sequences (xterm palette).
	 * Used as a middle tier when truecolor is not available. Detected by $TERM
	 * containing "256color".
	 */
	public static final boolean ANSI_256COLOR = ANSI_COLOR && !ANSI_TRUECOLOR && detectAnsi256();

	// -------------------------------------------------------------------------
	// ANSI color utilities (24-bit truecolor → 256-color → 16-color)
	// -------------------------------------------------------------------------

	/**
	 * Hand-tuned ANSI-16 fallback index for each ColorChoice ordinal.
	 * Nearest-neighbor search in RGB (or even perceptual) space seems to
	 * fail for some colors, and by hand is relatively easy for the defaults.
	 */
	private static final int[] ANSI16_FALLBACK;
	static {
		ANSI16_FALLBACK = new int[ColorChoice.values().length];
		// BACKGROUND  #263238  dark teal-gray  → black
		ANSI16_FALLBACK[ColorChoice.BACKGROUND.ordinal()]    =  0;
		// TITLE       #FFFFFF  white           → bright-white
		ANSI16_FALLBACK[ColorChoice.TITLE.ordinal()]         = 15;
		// SUBTITLE    #90A4AE  muted blue-gray → bright-black (gray)
		ANSI16_FALLBACK[ColorChoice.SUBTITLE.ordinal()]      =  8;
		// PROGRESS    #00EB76  bright green    → bright-green
		ANSI16_FALLBACK[ColorChoice.PROGRESS.ordinal()]      = 10;
		// PROGRESSTRACK #455A64 dark gray      → black
		ANSI16_FALLBACK[ColorChoice.PROGRESSTRACK.ordinal()] =  0;
		// DIALOG      #FFFFFF  white           → bright-white
		ANSI16_FALLBACK[ColorChoice.DIALOG.ordinal()]        = 15;
		// BUTTON      #00A653  mid green       → green (bright-green if possible)
		ANSI16_FALLBACK[ColorChoice.BUTTON.ordinal()]        = 10;
		// BUTTONTEXT  #FFFFFF  white           → bright-white
		ANSI16_FALLBACK[ColorChoice.BUTTONTEXT.ordinal()]    = 15;
		// QUESTION    #D500F9  vivid magenta   → bright-magenta
		ANSI16_FALLBACK[ColorChoice.QUESTION.ordinal()]      = 13;
		// INFO        #2979FF  bright blue     → bright-blue
		ANSI16_FALLBACK[ColorChoice.INFO.ordinal()]          = 12;
		// WARNING     #FF9100  orange          → bright-yellow (closest available)
		ANSI16_FALLBACK[ColorChoice.WARNING.ordinal()]       = 11;
		// ERROR       #FF1744  bright red      → bright-red
		ANSI16_FALLBACK[ColorChoice.ERROR.ordinal()]         =  9;
	}

	/**
	 * Returns the ANSI foreground escape for the given color choice.
	 * Uses 24-bit sequences when truecolor is available, 256-color xterm
	 * sequences when the terminal supports them, and falls back to the
	 * hand-tuned semantic ANSI-16 index otherwise. Returns "" if colors are
	 * off entirely.
	 */
	private static String fg(ColorChoice c) {
		if (!ANSI_COLOR) return "";
		int rgb = saturate(c);
		if (ANSI_TRUECOLOR) {
			int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			return "\033[38;2;" + r + ";" + g + ";" + b + "m";
		}
		if (ANSI_256COLOR) {
			return "\033[38;5;" + toAnsi256(rgb) + "m";
		}
		int n = ANSI16_FALLBACK[c.ordinal()];
		return n < 8 ? "\033[3" + n + "m" : "\033[9" + (n - 8) + "m";
	}

	/**
	 * Returns the ANSI background escape for the given color choice.
	 * Uses 24-bit sequences when truecolor is available, 256-color xterm
	 * sequences when the terminal supports them, and falls back to the
	 * hand-tuned semantic ANSI-16 index otherwise. Returns "" if colors are
	 * off entirely.
	 */
	private static String bg(ColorChoice c) {
		if (!ANSI_COLOR) return "";
		int rgb = saturate(c);
		if (ANSI_TRUECOLOR) {
			int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			return "\033[48;2;" + r + ";" + g + ";" + b + "m";
		}
		if (ANSI_256COLOR) {
			return "\033[48;5;" + toAnsi256(rgb) + "m";
		}
		int n = ANSI16_FALLBACK[c.ordinal()];
		return n < 8 ? "\033[4" + n + "m" : "\033[10" + (n - 8) + "m";
	}

	/**
	 * Returns a raw ANSI foreground escape for a literal 24-bit RGB value,
	 * bypassing ColorChoice lookup and saturation. Used for fixed UI chrome
	 * colors (e.g. chip backgrounds) that are not theme-able (ehe, see what I did there).
	 * Returns "" if colors are off.
	 */
	private static String fgRaw(int rgb) {
		if (!ANSI_COLOR) return "";
		if (ANSI_TRUECOLOR) {
			int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			return "\033[38;2;" + r + ";" + g + ";" + b + "m";
		}
		if (ANSI_256COLOR) return "\033[38;5;" + toAnsi256(rgb) + "m";
		return "\033[37m"; // light gray fallback
	}

	/** Raw ANSI background for a literal RGB value. Returns "" if colors are off. */
	private static String bgRaw(int rgb) {
		if (!ANSI_COLOR) return "";
		if (ANSI_TRUECOLOR) {
			int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			return "\033[48;2;" + r + ";" + g + ";" + b + "m";
		}
		if (ANSI_256COLOR) return "\033[48;5;" + toAnsi256(rgb) + "m";
		return "\033[40m"; // dark bg fallback
	}

	/**
	 * Renders a small "chip" — text on a dark #303030 background.
	 * The fg is chosen from two fixed levels: white (#FFFFFF) for emphasis,
	 * gray (#9E9E9E) for secondary text.
	 *
	 * @param text    the chip text
	 * @param whiteFg true for white foreground, false for gray (#9E9E9E)
	 */
	private static String chip(String text, boolean whiteFg) {
		if (!ANSI_COLOR) return text;
		int fg = whiteFg ? 0xFFFFFF : 0x9E9E9E;
		return bgRaw(0x303030) + fgRaw(fg) + text + reset();
	}

	/**
	 * Lightly boosts the saturation of colors that benefit from it (green,
	 * purple/magenta families) so they read more vividly against dark terminal
	 * backgrounds. The boost is additive on the dominant channel(s) and
	 * subtractive on the minor ones, clamped to 0–255. Colors that are already
	 * near-neutral (gray, white, black) are returned unchanged.
	 */
	private static int saturate(ColorChoice c) {
		int rgb = c.get();
		// Only boost in color-capable paths; 16-color uses the semantic table.
		if (!ANSI_256COLOR && !ANSI_TRUECOLOR) return rgb;

		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		int sat = max - min; // rough chroma

		// Skip near-neutral colors (chroma < 40) — grays, whites, blacks.
		if (sat < 40) return rgb;

		// Amount to push dominant channel up / minor channels down.
		// Scaled so high-chroma colors get a smaller absolute nudge.
		int boost = Math.max(4, 30 - sat / 8);
		r = clamp(r == max ? r + boost : r == min ? r - boost : r);
		g = clamp(g == max ? g + boost : g == min ? g - boost : g);
		b = clamp(b == max ? b + boost : b == min ? b - boost : b);
		return (r << 16) | (g << 8) | b;
	}

	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

	/** Returns the ANSI reset sequence. */
	private static String reset() {
		return ANSI_COLOR ? "\033[0m" : "";
	}

	/**
	 * Returns the colored "[unsup]" header badge: dark #303030 background,
	 * gray brackets, BUTTON-green "unsup" in the middle.
	 */
	private static String unsupHeader() {
		if (!ANSI_COLOR) return "[unsup]";
		return bgRaw(0x303030) + fgRaw(0x9E9E9E) + "[" + reset()
				+ bgRaw(0x303030) + fg(ColorChoice.BUTTON) + "unsup" + reset()
				+ bgRaw(0x303030) + fgRaw(0x9E9E9E) + "]" + reset();
	}

	/**
	 * Returns a colored button hint of the form {@code [K]ey}.
	 * The bracket+key is in BUTTON green; the rest of the word is in BUTTONTEXT
	 * white. If {@code isDefault} is true the key letter is uppercased,
	 * otherwise lowercase.
	 *
	 * @param key      single letter
	 * @param label    full word (e.g. "yes", "ok", "cancel") — leading char is
	 *                 the key and is omitted from the suffix
	 * @param isDefault whether this option is the default (uppercase key)
	 */
	private static String btn(String key, String label, boolean isDefault) {
		String k = isDefault ? key.toUpperCase(Locale.ROOT) : key.toLowerCase(Locale.ROOT);
		String suffix = label.length() > 1 ? label.substring(1) : "";
		return bg(ColorChoice.BACKGROUND) + fg(ColorChoice.SUBTITLE) + "[" + reset()
				+ bg(ColorChoice.BACKGROUND) + fg(ColorChoice.BUTTON) + k + reset()
				+ bg(ColorChoice.BACKGROUND) + fg(ColorChoice.SUBTITLE) + "]" + reset()
				+ bg(ColorChoice.BACKGROUND) + fg(ColorChoice.BUTTONTEXT) + suffix + reset();
	}

	/** Separator between prompt options. */
	private static String btnSep() {
		return " " + fg(ColorChoice.SUBTITLE) + "/" + reset() + " ";
	}

	private static boolean detectAnsi256() {
		String term = System.getenv("TERM");
		return term != null && term.contains("256color");
	}

	/**
	 * Maps a 24-bit RGB value to the nearest xterm 256-color palette index,
	 * with each cube axis clamped to a maximum of 3 (level 175) so that the
	 * two brightest rungs (215, 255) are never used. This keeps colors bright
	 * but pastel — vivid enough to read well without hitting the harshest
	 * saturated extremes of the full cube.
	 *
	 * The palette regions are:
	 *   16–231  6×6×6 color cube, levels {0, 95, 135, 175, 215, 255}
	 *   232–255 24-step grayscale ramp, levels 8, 18, … 238
	 */
	private static int toAnsi256(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

		// Map into the 6×6×6 cube, then clamp each axis to max index 3 (level 175)
		// so the brightest rungs (215, 255) are never used, keeping colors pastel.
		int ri = Math.min(3, r < 48 ? 0 : r < 115 ? 1 : (r - 35) / 40);
		int gi = Math.min(3, g < 48 ? 0 : g < 115 ? 1 : (g - 35) / 40);
		int bi = Math.min(3, b < 48 ? 0 : b < 115 ? 1 : (b - 35) / 40);
		int cubeIdx = 16 + 36 * ri + 6 * gi + bi;

		// Nearest grayscale entry (indices 232–255): levels 8, 18, 28, … 238
		int gray = (r + g + b) / 3;
		int grayIdx = gray < 8 ? 232 : gray > 238 ? 255 : 232 + (gray - 8) / 10;

		// Pick whichever of cube or grayscale is closer to the target
		int[] cubeRgb = cubeEntryRgb(ri, gi, bi);
		int[] grayRgb = grayEntryRgb(grayIdx);
		int cubeDist = dist(r, g, b, cubeRgb);
		int grayDist = dist(r, g, b, grayRgb);
		return cubeDist <= grayDist ? cubeIdx : grayIdx;
	}

	private static int[] cubeEntryRgb(int ri, int gi, int bi) {
		int[] v = { 0, 95, 135, 175, 215, 255 };
		return new int[]{ v[ri], v[gi], v[bi] };
	}

	private static int[] grayEntryRgb(int idx) {
		int level = idx == 232 ? 8 : idx == 255 ? 238 : 8 + (idx - 232) * 10;
		return new int[]{ level, level, level };
	}

	private static int dist(int r, int g, int b, int[] entry) {
		int dr = r - entry[0], dg = g - entry[1], db = b - entry[2];
		return dr * dr + dg * dg + db * db;
	}

	private static boolean detectAnsi() {
		if (System.getenv("NO_COLOR") != null) return false;
		// TERM=dumb means no ANSI; any other set value is fine
		String term = System.getenv("TERM");
		return term != null && !term.equals("dumb");
	}

	private static boolean detectTruecolor() {
		// COLORTERM=truecolor or COLORTERM=24bit directly allows all colors
		String colorterm = System.getenv("COLORTERM");
		if ("truecolor".equalsIgnoreCase(colorterm) || "24bit".equalsIgnoreCase(colorterm))
			return true;
		// TERM values that explicitly advertise 24-bit support
		String term = System.getenv("TERM");
		return term != null && (term.contains("24bit") || term.contains("truecolor"));
	}

	private static final BufferedReader STDIN = new BufferedReader(
			new InputStreamReader(System.in, StandardCharsets.UTF_8));

	// -------------------------------------------------------------------------
	// Progress bar!
	// -------------------------------------------------------------------------

	private static volatile int progressValue = -1;
	private static volatile boolean progressDeterminate = false;
	private static volatile String progressTitle = "";
	private static volatile String progressSubtitle = "";
	// Spinner frame for indeterminate mode
	private static final AtomicInteger spinFrame = new AtomicInteger(0);
	private static final char[] SPIN_CHARS = { '|', '/', '-', '\\' };
	private static final int BAR_WIDTH = 30;

	/**
	 * Called by PuppetHandler.updateTitle. Sets the displayed title and starts
	 * the determinate progress bar. If {@code determinate} is false (i.e. the
	 * apply phase, which is nearly instant), the bar is hidden instead — there
	 * is nothing useful to show.
	 */
	public static void startProgress(String title, boolean determinate) {
		if (!ANSI)
			return;
		synchronized (Log.class) {
			if (!determinate) {
				// Apply phase is instant; just clear the bar if one is showing.
				if (progressValue >= 0) {
					progressValue = -1;
					clearBar();
					removeLogHooks();
				}
				return;
			}
			progressTitle = title != null ? title : "";
			progressSubtitle = "";
			progressValue = 0;
			progressDeterminate = true;
			installLogHooks();
			renderBar();
		}
	}

	/** Called by PuppetHandler.updateSubtitle / updateSubtitleDownloading. */
	public static void updateSubtitle(String subtitle) {
		if (!ANSI)
			return;
		synchronized (Log.class) {
			progressSubtitle = subtitle != null ? subtitle : "";
			if (progressValue >= 0)
				renderBar();
		}
	}

	/** Advance to a new line, stamping whatever is on the current bar line. No-op when not ANSI. */
	public static void newline() {
		if (!ANSI)
			return;
		synchronized (Log.class) {
			System.out.println();
		}
	}

	/** Called by PuppetHandler.updateProgress. prog is 0–10000. */
	public static void updateProgress(int prog) {
		if (!ANSI)
			return;
		synchronized (Log.class) {
			progressValue = prog;
			progressDeterminate = true;
			if (progressValue >= 0)
				renderBar();
		}
	}

	/**
	 * Hide the progress bar (e.g. when update is done or before an interactive
	 * prompt).
	 */
	public static void hideProgress() {
		if (!ANSI)
			return;
		synchronized (Log.class) {
			if (progressValue < 0)
				return;
			progressValue = -1;
			clearBar();
			removeLogHooks();
		}
	}

	private static void installLogHooks() {
		Log.beforeLine = ConsoleUI::clearBar;
		Log.afterLine = ConsoleUI::renderBar;
	}

	private static void removeLogHooks() {
		Log.beforeLine = null;
		Log.afterLine = null;
	}

	/** Erase the current progress bar line. Must be called under Log.class lock. */
	private static void clearBar() {
		// \r moves to start of line; \033[K erases to end of line
		System.out.print("\r\033[K");
		System.out.flush();
	}

	/**
	 * Draw (or redraw) the progress bar on the current line. Must be called under
	 * Log.class lock.
	 */
	private static void renderBar() {
		if (progressValue < 0)
			return;

		StringBuilder sb = new StringBuilder();
		sb.append('\r');

		// Colored bracket helpers (inline, no method call overhead)
		String bracketOpen  = chip(" ", false) + (ANSI_COLOR ? (fg(ColorChoice.SUBTITLE) + bg(ColorChoice.BACKGROUND) + "|" + reset() + bg(ColorChoice.BACKGROUND))  : "|");
		String bracketClose = (ANSI_COLOR ? fg(ColorChoice.SUBTITLE) + "|" + reset() : "|") + chip(" ", false);

		if (progressDeterminate) {
			final String[] BRAILLE = { "⣀", "⣄", "⣤", "⣦", "⣶", "⣷", "⣿" };
			int filled     = (int) ((long) progressValue * BAR_WIDTH / 1000);
			int remainder  = (int) ((long) progressValue * BAR_WIDTH % 1000); // 0..999
			int partialIdx = remainder * BRAILLE.length / 1000;               // 0..6
			boolean hasPartial = remainder > 0 && filled < BAR_WIDTH;

			sb.append(bracketOpen);
			if (ANSI_COLOR) {
				sb.append(fg(ColorChoice.PROGRESS));
				for (int i = 0; i < filled; i++) sb.append('⣿');
				if (hasPartial) sb.append(BRAILLE[partialIdx]);
				sb.append(reset()).append(fg(ColorChoice.PROGRESSTRACK)).append(bg(ColorChoice.BACKGROUND));
				int trackStart = filled + (hasPartial ? 1 : 0);
				for (int i = trackStart; i < BAR_WIDTH; i++) sb.append('.');
				sb.append(reset()).append(bg(ColorChoice.BACKGROUND));
			} else {
				for (int i = 0; i < BAR_WIDTH; i++)
					sb.append(i < filled ? '=' : i == filled ? '>' : ' ');
			}
			sb.append(bracketClose);
			if (ANSI_COLOR) {
				sb.append("   ").append(fg(ColorChoice.TITLE)).append(bg(ColorChoice.BACKGROUND))
						.append(String.format("%3d%% ", progressValue / 10)).append(reset());
			} else {
				sb.append(String.format(" %3d%%", progressValue / 10));
			}

		} else {
			char spin = SPIN_CHARS[spinFrame.getAndIncrement() % SPIN_CHARS.length];
			int mid = BAR_WIDTH / 2;
			sb.append(bracketOpen);
			if (ANSI_COLOR) {
				for (int i = 0; i < BAR_WIDTH; i++) {
					if (i == mid) {
						sb.append(fg(ColorChoice.PROGRESS)).append(spin).append(reset())
								.append(fg(ColorChoice.PROGRESSTRACK));
					} else {
						// start track color before first char
						if (i == 0) sb.append(fg(ColorChoice.PROGRESSTRACK));
						sb.append('.');
					}
				}
				sb.append(reset());
			} else {
				for (int i = 0; i < BAR_WIDTH; i++)
					sb.append(i == mid ? spin : ' ');
			}
			sb.append(bracketClose);
			sb.append("    ");
		}

		// Append title and subtitle with per-segment color, truncated to fit within 120
		// visible chars.
		// Fixed visible width: '|' (1) + BAR_WIDTH + '|' (1) + pct/spin suffix (5 or
		// 4).
		int fixedVis = 1 + BAR_WIDTH + 1 + (progressDeterminate ? 5 : 4);
		int budget = 120 - fixedVis;

		String curTitle = progressTitle;
		String curSubtitle = progressSubtitle;

		if (!curTitle.isEmpty() && budget > 2) {
			int avail = budget - 2; // subtract the " " separator
			String text = curTitle;
			if (text.length() > avail) {
				text = text.substring(0, avail - 1) + "…";
				curSubtitle = ""; // no budget left for subtitle
			}
			budget -= (2 + text.length());
			sb.append("  ").append(fg(ColorChoice.TITLE)).append(text).append(reset());
		}
		if (!curSubtitle.isEmpty() && budget > 2) {
			int avail = budget - 2;
			String text = curSubtitle;
			if (text.length() > avail) {
				text = text.substring(0, avail - 1) + "…";
			}
			sb.append("  ").append(fg(ColorChoice.SUBTITLE)).append(text).append(reset());
		}

		System.out.print(sb.toString());
		System.out.flush();
	}

	// -------------------------------------------------------------------------
	// Alert / confirm prompts
	// -------------------------------------------------------------------------

	public static AlertOption promptAlert(String title, String body,
			AlertMessageType messageType, AlertOptionType optionType, AlertOption def) {
		if (!INTERACTIVE) {
			Log.warn("No console available for interactive prompt \"" + title
					+ "\", using default: " + def.name().toLowerCase(Locale.ROOT));
			return def;
		}
		hideProgress();

		String prompt = buildAlertPrompt(optionType, def);

		ColorChoice typeColor = switch (messageType) {
			case QUESTION -> ColorChoice.QUESTION;
			case INFO     -> ColorChoice.INFO;
			case WARN     -> ColorChoice.WARNING;
			case ERROR    -> ColorChoice.ERROR;
			case NONE     -> ColorChoice.TITLE;
		};

		while (true) {
			System.out.println();
			System.out.println(unsupHeader() + " " + fg(typeColor) + title + reset());
			if (body != null && !body.isEmpty()) {
				System.out.println("  " + fg(ColorChoice.DIALOG) + body + reset());
			}
			System.out.print("  " + fg(ColorChoice.BUTTON) + prompt + reset() + ": ");
			System.out.flush();

			String line = readLine();
			if (line == null) {
				Log.warn("Console closed during prompt \"" + title
						+ "\", using default: " + def.name().toLowerCase(Locale.ROOT));
				return def;
			}
			line = line.trim().toLowerCase(Locale.ROOT);

			if (line.isEmpty())
				return def;

			AlertOption parsed = parseAlertOption(line, optionType);
			if (parsed != null)
				return parsed;

			System.out.println("  " + fg(ColorChoice.WARNING) + "Invalid input. Please try again." + reset());
		}
	}

	private static String buildAlertPrompt(AlertOptionType optionType, AlertOption def) {
		return switch (optionType) {
			case OK ->
				btn("O", "ok", def == AlertOption.OK);
			case OK_CANCEL ->
				btn("O", "ok", def == AlertOption.OK) + btnSep()
						+ btn("C", "cancel", def == AlertOption.CANCEL);
			case YES_NO ->
				btn("Y", "yes", def == AlertOption.YES) + btnSep()
						+ btn("N", "no", def == AlertOption.NO);
			case YES_NO_CANCEL ->
				btn("Y", "yes", def == AlertOption.YES) + btnSep()
						+ btn("N", "no", def == AlertOption.NO) + btnSep()
						+ btn("C", "cancel", def == AlertOption.CANCEL);
			case YES_NO_TO_ALL_CANCEL ->
				btn("Y", "yes", def == AlertOption.YES) + btnSep()
						+ btn("N", "no", def == AlertOption.NO) + btnSep()
						+ btn("A", "all (yes to all)", false) + btnSep()
						+ btn("Z", "zero (no to all)", false) + btnSep()
						+ btn("C", "cancel", def == AlertOption.CANCEL);
		};
	}

	private static AlertOption parseAlertOption(String input, AlertOptionType optionType) {
		return switch (input) {
			case "y", "yes" -> AlertOption.YES;
			case "n", "no" -> AlertOption.NO;
			case "o", "ok" -> AlertOption.OK;
			case "c", "cancel" -> AlertOption.CANCEL;
			case "a", "yestoall" -> AlertOption.YESTOALL;
			case "z", "notoall" -> AlertOption.NOTOALL;
			default -> null;
		};
	}

	// -------------------------------------------------------------------------
	// Generic choice prompt (openChoiceAlert)
	// -------------------------------------------------------------------------

	public static String promptChoiceAlert(String title, String body,
			Collection<String> choices, String def) {
		if (!INTERACTIVE) {
			Log.warn("No console available for choice \"" + title
					+ "\", using default: " + def);
			return def;
		}
		hideProgress();

		List<String> choiceList = new ArrayList<>(choices);
		int defaultIndex = choiceList.indexOf(def);
		if (defaultIndex < 0)
			defaultIndex = 0;

		while (true) {
			System.out.println();
			System.out.println(unsupHeader() + " " + fg(ColorChoice.TITLE) + title + reset());
			if (body != null && !body.isEmpty()) {
				System.out.println("  " + fg(ColorChoice.DIALOG) + body + reset());
			}
			for (int i = 0; i < choiceList.size(); i++) {
				System.out.println("  " + fg(ColorChoice.BUTTON) + (i + 1) + ")" + reset()
						+ " " + fg(ColorChoice.BUTTONTEXT) + choiceList.get(i) + reset());
			}
			System.out.print("  " + fg(ColorChoice.SUBTITLE) + "Choice " + reset()
					+ fg(ColorChoice.SUBTITLE) + "[" + reset()
					+ fg(ColorChoice.BUTTON) + (defaultIndex + 1) + reset()
					+ fg(ColorChoice.SUBTITLE) + "]" + reset()
					+ fg(ColorChoice.SUBTITLE) + ": " + reset());
			System.out.flush();

			String line = readLine();
			if (line == null) {
				Log.warn("Console closed during choice \"" + title
						+ "\", using default: " + def);
				return def;
			}
			line = line.trim();

			if (line.isEmpty()) return choiceList.get(defaultIndex);

			try {
				int n = Integer.parseInt(line);
				if (n >= 1 && n <= choiceList.size()) return choiceList.get(n - 1);
			} catch (NumberFormatException ignored) {}

			System.out.println("  " + fg(ColorChoice.WARNING) + "Invalid input. Enter a number between 1 and " + choiceList.size() + "." + reset());
		}
	}

	// -------------------------------------------------------------------------
	// Flavor selection (one choice per FlavorGroup)
	// -------------------------------------------------------------------------

	public static List<String> promptFlavorSelect(String title, String body,
			List<FlavorGroup> groups) {
		if (!INTERACTIVE) {
			Log.warn("No console available for flavor selection, using configured defaults");
			return buildFlavorDefaults(groups);
		}
		hideProgress();

		List<String> selected = new ArrayList<>();

		System.out.println();
		System.out.println(unsupHeader() + " " + fg(ColorChoice.TITLE) + title + reset());
		if (body != null && !body.isEmpty()) {
			System.out.println("  " + fg(ColorChoice.DIALOG) + body + reset());
		}

		for (FlavorGroup group : groups) {
			var choices = group.choices();
			int defaultIndex = 0;
			for (int i = 0; i < choices.size(); i++) {
				if (choices.get(i).def()) {
					defaultIndex = i;
					break;
				}
			}

			while (true) {
				System.out.println();
				System.out.print("  " + fg(ColorChoice.QUESTION) + group.name() + reset());
				if (group.description() != null && !group.description().isEmpty()) {
					System.out.print("  " + fg(ColorChoice.SUBTITLE) + "(" + group.description() + ")" + reset());
				}
				System.out.println();
				for (int i = 0; i < choices.size(); i++) {
					var c = choices.get(i);
					String desc = (c.description() != null && !c.description().isEmpty())
							? " " + fg(ColorChoice.SUBTITLE) + "- " + c.description() + reset() : "";
					String marker = (i == defaultIndex)
							? "  " + chip("[default]", false) : "";
					System.out.println("    " + fg(ColorChoice.BUTTON) + (i + 1) + ")" + reset()
							+ " " + fg(ColorChoice.BUTTONTEXT) + c.name() + reset() + desc + marker);
				}
				System.out.print("  " + fg(ColorChoice.SUBTITLE) + "Choice " + reset()
						+ fg(ColorChoice.SUBTITLE) + "[" + reset()
						+ fg(ColorChoice.BUTTON) + (defaultIndex + 1) + reset()
						+ fg(ColorChoice.SUBTITLE) + "]" + reset()
						+ fg(ColorChoice.SUBTITLE) + ": " + reset());
				System.out.flush();

				String line = readLine();
				if (line == null) {
					Log.warn("Console closed during flavor selection for \""
							+ group.name() + "\", using default");
					selected.add(choices.get(defaultIndex).id());
					break;
				}
				line = line.trim();

				if (line.isEmpty()) {
					selected.add(choices.get(defaultIndex).id());
					break;
				}

				try {
					int n = Integer.parseInt(line);
					if (n >= 1 && n <= choices.size()) {
						selected.add(choices.get(n - 1).id());
						break;
					}
				} catch (NumberFormatException ignored) {}

				System.out.println("  " + fg(ColorChoice.WARNING) + "Invalid input. Enter a number between 1 and " + choices.size() + "." + reset());
			}
		}

		return selected;
	}

	private static List<String> buildFlavorDefaults(List<FlavorGroup> groups) {
		List<String> result = new ArrayList<>();
		for (FlavorGroup group : groups) {
			for (var c : group.choices()) {
				if (c.def()) {
					result.add(c.id());
					break;
				}
			}
			// If no default marked, nothing is added — caller (AbstractFormatHandler) should handle it
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// Version selection
	// -------------------------------------------------------------------------

	public static Optional<Integer> promptVersionSelect(List<Version> versions, int currentCode) {
		if (!INTERACTIVE) {
			Log.warn("No console available for version selector, skipping");
			return Optional.empty();
		}
		hideProgress();

		int currentIndex = -1;
		for (int i = 0; i < versions.size(); i++) {
			if (versions.get(i).code() == currentCode) {
				currentIndex = i;
				break;
			}
		}

		while (true) {
			System.out.println();
			System.out.println(unsupHeader() + " " + fg(ColorChoice.TITLE) + "Select version" + reset());
			if (currentIndex >= 0) {
				Version cv = versions.get(currentIndex);
				System.out.println("  " + fg(ColorChoice.SUBTITLE) + "Current: " + reset()
						+ fg(ColorChoice.DIALOG) + cv.name() + reset()
						+ " " + fg(ColorChoice.SUBTITLE) + "(" + cv.code() + ")" + reset());
			}
			System.out.println();
			System.out.println("  " + fg(ColorChoice.SUBTITLE) + "Available versions:" + reset());
			for (int i = 0; i < versions.size(); i++) {
				Version v = versions.get(i);
				String tags = "";
				if (i == 0)
					tags += "  " + chip("[latest]", true);
				if (v.code() == currentCode)
					tags += "  " + chip("[current]", false);
				System.out.println("    " + fg(ColorChoice.BUTTON) + (i + 1) + ")" + reset()
						+ " " + fg(ColorChoice.BUTTONTEXT) + v.name() + reset()
						+ " " + fg(ColorChoice.SUBTITLE) + "(" + v.code() + ")" + reset()
						+ tags);
			}
			System.out.println();
			System.out.print("  " + fg(ColorChoice.SUBTITLE) + "Enter number, or " + reset()
					+ btn("S", "skip", true)
					+ fg(ColorChoice.SUBTITLE) + ": " + reset());
			System.out.flush();

			String line = readLine();
			if (line == null) {
				Log.warn("Console closed during version selector, skipping");
				return Optional.empty();
			}
			line = line.trim();

			if (line.isEmpty() || line.equalsIgnoreCase("s") || line.equalsIgnoreCase("skip")) {
				return Optional.empty();
			}

			try {
				int n = Integer.parseInt(line);
				if (n >= 1 && n <= versions.size()) {
					return Optional.of(versions.get(n - 1).code());
				}
			} catch (NumberFormatException ignored) {
			}

			System.out.println("  " + fg(ColorChoice.WARNING) + "Invalid input. Enter a number between 1 and "
					+ versions.size() + ", or S to skip." + reset());
		}
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	private static String readLine() {
		try {
			return STDIN.readLine();
		} catch (IOException e) {
			return null;
		}
	}

}
