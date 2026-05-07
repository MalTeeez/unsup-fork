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
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.data.Version;

/**
 * Simple stdin/stdout TUI used when nogui is active and a real console is available.
 * All methods degrade gracefully (with a warning) when stdin is not a TTY.
 */
public class ConsoleUI {

	/** True when stdin is a real interactive terminal. */
	public static final boolean INTERACTIVE = System.console() != null;

	/**
	 * True when the terminal supports ANSI escape codes (cursor movement, erase-line).
	 * Requires INTERACTIVE to be true as well.
	 */
	public static final boolean ANSI = INTERACTIVE && detectAnsi();

	private static boolean detectAnsi() {
		// On Windows, modern terminals set WT_SESSION (Windows Terminal) or COLORTERM
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return System.getenv("WT_SESSION") != null
					|| System.getenv("COLORTERM") != null
					|| System.getenv("TERM") != null;
		}
		// On Unix, TERM=dumb means no ANSI; anything else (xterm, xterm-256color, …) is fine
		String term = System.getenv("TERM");
		return term != null && !term.equals("dumb");
	}

	private static final BufferedReader STDIN =
			new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

	// -------------------------------------------------------------------------
	// Progress bar state
	// -------------------------------------------------------------------------

	// Progress is 0–10000 (‰ × 10). -1 = indeterminate / hidden.
	private static volatile int progressValue = -1;
	private static volatile boolean progressDeterminate = false;
	private static volatile String progressTitle = "";
	private static volatile String progressSubtitle = "";
	// Spinner frame for indeterminate mode
	private static final AtomicInteger spinFrame = new AtomicInteger(0);
	private static final char[] SPIN_CHARS = { '|', '/', '-', '\\' };
	private static final int BAR_WIDTH = 30;

	/**
	 * Called by PuppetHandler.updateTitle. Sets the displayed title and whether
	 * progress is determinate (bar) or indeterminate (spinner).
	 */
	public static void startProgress(String title, boolean determinate) {
		if (!ANSI) return;
		synchronized (Log.class) {
			progressTitle = title != null ? title : "";
			progressSubtitle = "";
			progressValue = 0;
			progressDeterminate = determinate;
			installLogHooks();
			renderBar();
		}
	}

	/** Called by PuppetHandler.updateSubtitle / updateSubtitleDownloading. */
	public static void updateSubtitle(String subtitle) {
		if (!ANSI) return;
		synchronized (Log.class) {
			progressSubtitle = subtitle != null ? subtitle : "";
			if (progressValue >= 0) renderBar();
		}
	}

	/** Called by PuppetHandler.updateProgress. prog is 0–10000. */
	public static void updateProgress(int prog) {
		if (!ANSI) return;
		synchronized (Log.class) {
			progressValue = prog;
			progressDeterminate = true;
			if (progressValue >= 0) renderBar();
		}
	}

	/** Hide the progress bar (e.g. when update is done or before an interactive prompt). */
	public static void hideProgress() {
		if (!ANSI) return;
		synchronized (Log.class) {
			if (progressValue < 0) return;
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

	/** Draw (or redraw) the progress bar on the current line. Must be called under Log.class lock. */
	private static void renderBar() {
		if (progressValue < 0) return;

		StringBuilder sb = new StringBuilder();
		sb.append('\r');

		if (progressDeterminate) {
			// [=============================>          ]  72%
			int filled = (int) ((long) progressValue * BAR_WIDTH / 10000);
			sb.append('[');
			for (int i = 0; i < BAR_WIDTH; i++) {
				if (i < filled) sb.append('=');
				else if (i == filled) sb.append('>');
				else sb.append(' ');
			}
			sb.append(']');
			int pct = progressValue / 100;
			sb.append(String.format(" %3d%%", pct));
		} else {
			// spinner for indeterminate
			char spin = SPIN_CHARS[spinFrame.getAndIncrement() % SPIN_CHARS.length];
			sb.append('[');
			int mid = BAR_WIDTH / 2;
			for (int i = 0; i < BAR_WIDTH; i++) sb.append(i == mid ? spin : ' ');
			sb.append(']');
			sb.append("    ");
		}

		if (!progressTitle.isEmpty()) {
			sb.append("  ").append(progressTitle);
		}
		if (!progressSubtitle.isEmpty()) {
			sb.append("  ").append(progressSubtitle);
		}

		// Truncate to a reasonable terminal width to avoid wrapping
		String line = sb.toString();
		int maxLen = 120;
		if (line.length() > maxLen) {
			line = line.substring(0, maxLen - 1) + "…";
		}

		System.out.print(line);
		System.out.flush();
	}

	// -------------------------------------------------------------------------
	// Alert / confirm prompts
	// -------------------------------------------------------------------------

	public static AlertOption promptAlert(String title, String body,
			AlertOptionType optionType, AlertOption def) {
		if (!INTERACTIVE) {
			Log.warn("No console available for interactive prompt \"" + title
					+ "\", using default: " + def.name().toLowerCase(Locale.ROOT));
			return def;
		}
		hideProgress();

		String prompt = buildAlertPrompt(optionType, def);

		while (true) {
			System.out.println();
			System.out.println("[unsup] " + title);
			if (body != null && !body.isEmpty()) {
				System.out.println("  " + body);
			}
			System.out.print("  " + prompt + " ");
			System.out.flush();

			String line = readLine();
			if (line == null) {
				Log.warn("Console closed during prompt \"" + title
						+ "\", using default: " + def.name().toLowerCase(Locale.ROOT));
				return def;
			}
			line = line.trim().toLowerCase(Locale.ROOT);

			if (line.isEmpty()) return def;

			AlertOption parsed = parseAlertOption(line, optionType);
			if (parsed != null) return parsed;

			System.out.println("  Invalid input. Please try again.");
		}
	}

	private static String buildAlertPrompt(AlertOptionType optionType, AlertOption def) {
		return switch (optionType) {
			case OK -> "[" + (def == AlertOption.OK ? "OK" : "ok") + "]";
			case OK_CANCEL -> mkPrompt(def, AlertOption.OK, "O", "ok", AlertOption.CANCEL, "C", "cancel");
			case YES_NO -> mkPrompt(def, AlertOption.YES, "Y", "yes", AlertOption.NO, "N", "no");
			case YES_NO_CANCEL ->
				"[" + ch(def, AlertOption.YES, "Y", "y") + "]es / "
				+ "[" + ch(def, AlertOption.NO, "N", "n") + "]o / "
				+ "[" + ch(def, AlertOption.CANCEL, "C", "c") + "]ancel";
			case YES_NO_TO_ALL_CANCEL ->
				"[" + ch(def, AlertOption.YES, "Y", "y") + "]es / "
				+ "[" + ch(def, AlertOption.NO, "N", "n") + "]o / "
				+ "[A] yes to all / [Z] no to all / "
				+ "[" + ch(def, AlertOption.CANCEL, "C", "c") + "]ancel";
		};
	}

	private static String mkPrompt(AlertOption def,
			AlertOption aOpt, String aKey, String aLabel,
			AlertOption bOpt, String bKey, String bLabel) {
		boolean aDef = def == aOpt;
		return "[" + (aDef ? aKey.toUpperCase(Locale.ROOT) : aKey.toLowerCase(Locale.ROOT)) + "]" + aLabel.substring(1)
			+ " / "
			+ "[" + (!aDef ? bKey.toUpperCase(Locale.ROOT) : bKey.toLowerCase(Locale.ROOT)) + "]" + bLabel.substring(1);
	}

	private static String ch(AlertOption def, AlertOption match, String upper, String lower) {
		return def == match ? upper : lower;
	}

	private static AlertOption parseAlertOption(String input, AlertOptionType optionType) {
		return switch (input) {
			case "y", "yes"      -> AlertOption.YES;
			case "n", "no"       -> AlertOption.NO;
			case "o", "ok"       -> AlertOption.OK;
			case "c", "cancel"   -> AlertOption.CANCEL;
			case "a", "yestoall" -> AlertOption.YESTOALL;
			case "z", "notoall"  -> AlertOption.NOTOALL;
			default              -> null;
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
		if (defaultIndex < 0) defaultIndex = 0;

		while (true) {
			System.out.println();
			System.out.println("[unsup] " + title);
			if (body != null && !body.isEmpty()) {
				System.out.println("  " + body);
			}
			for (int i = 0; i < choiceList.size(); i++) {
				System.out.println("  " + (i + 1) + ") " + choiceList.get(i));
			}
			System.out.print("  Choice [" + (defaultIndex + 1) + "]: ");
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

			System.out.println("  Invalid input. Enter a number between 1 and " + choiceList.size() + ".");
		}
	}

	// -------------------------------------------------------------------------
	// Flavor selection (one choice per FlavorGroup — radio-button style)
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
		System.out.println("[unsup] " + title);
		if (body != null && !body.isEmpty()) {
			System.out.println("  " + body);
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
				System.out.print("  " + group.name());
				if (group.description() != null && !group.description().isEmpty()) {
					System.out.print("  (" + group.description() + ")");
				}
				System.out.println();
				for (int i = 0; i < choices.size(); i++) {
					var c = choices.get(i);
					String desc = (c.description() != null && !c.description().isEmpty())
							? " - " + c.description() : "";
					String marker = (i == defaultIndex) ? "  [default]" : "";
					System.out.println("    " + (i + 1) + ") " + c.name() + desc + marker);
				}
				System.out.print("  Choice [" + (defaultIndex + 1) + "]: ");
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

				System.out.println("  Invalid input. Enter a number between 1 and " + choices.size() + ".");
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
			// If no default marked, nothing is added — caller (AbstractFormatHandler) handles it
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
			System.out.println("[unsup] Select version");
			if (currentIndex >= 0) {
				System.out.println("  Current: " + versions.get(currentIndex));
			}
			System.out.println();
			System.out.println("  Available versions:");
			for (int i = 0; i < versions.size(); i++) {
				Version v = versions.get(i);
				String tags = "";
				if (i == 0) tags += "  [latest]";
				if (v.code() == currentCode) tags += "  [current]";
				System.out.println("    " + (i + 1) + ") " + v + tags);
			}
			System.out.println();
			System.out.print("  Enter number, or S to skip [S]: ");
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
			} catch (NumberFormatException ignored) {}

			System.out.println("  Invalid input. Enter a number between 1 and "
					+ versions.size() + ", or S to skip.");
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
