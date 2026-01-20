/*
 * This file is part of unsup.
 * Copyright © 2024-2025 Exa Skye
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.unascribed.sup.data.SysProps;
import com.unascribed.sup.pieces.NullPrintStream;
import com.unascribed.sup.util.SuppressFBWarnings;

/**
 * Hand-rolled bare-minimum logger facility.
 */
public class Log {

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
	private static PrintStream fileStream;
	private static String defaultTag;
	
	@SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
	public static void init() {
		defaultTag = Agent.standalone ? "sync" : "agent";
		File logTarget = new File("logs");
		if (!logTarget.isDirectory()) {
			logTarget = new File(".");
		} else {
			// to avoid confusion with a later-created logs dir
			new File("unsup.log").delete();
			new File("unsup.log.1").delete();
			new File("unsup.log.2").delete();
		}
		File logFile = new File(logTarget, "unsup.log");
		File oldLogFile = new File(logTarget, "unsup.log.1");
		File olderLogFile = new File(logTarget, "unsup.log.2");
		// intentionally ignoring exceptional return here
		// don't really care if any of it fails
		if (logFile.exists()) {
			if (oldLogFile.exists()) {
				if (olderLogFile.exists()) {
					olderLogFile.delete();
				}
				oldLogFile.renameTo(olderLogFile);
			}
			logFile.renameTo(oldLogFile);
		}
		try {
			OutputStream logOut = new FileOutputStream(logFile);
			Agent.addCleanupAction(logOut::close);
			fileStream = new PrintStream(logOut, true, "UTF-8");
		} catch (Exception e) {
			fileStream = NullPrintStream.INSTANCE;
			Log.warn("Failed to open log file "+logFile);
		}
	}

	public static void log(String flavor, String msg) {
		log(flavor, defaultTag, msg);
	}
	
	public static void log(String flavor, String msg, Throwable t) {
		log(flavor, defaultTag, msg, t);
	}
	
	public synchronized static void log(String flavor, String tag, String msg, Throwable t) {
		if (t != null) {
			if (!("DEBUG".equals(flavor)) || SysProps.DEBUG.orBias()) {
				t.printStackTrace();
			}
			t.printStackTrace(fileStream);
		}
		log(flavor, tag, msg);
	}
	
	public synchronized static void log(String flavor, String tag, String msg) {
		String line = "["+dateFormat.format(new Date())+"] [unsup "+tag+"/"+flavor+"]: "+msg;
		if (!("DEBUG".equals(flavor)) || SysProps.DEBUG.orBias()) System.out.println(line);
		fileStream.println(line);
	}
	
	public synchronized static void puppetStderr(String line) {
		line = "puppet: "+line;
		System.out.println(line);
		fileStream.println(line);
	}
	

	
	public static void debug(String msg)                          { log("DEBUG", msg); }
	public static void debug(String msg, Throwable t)             { log("DEBUG", msg, t); }
	public static void debug(String tag, String msg, Throwable t) { log("DEBUG", tag, msg, t); }
	public static void debug(String tag, String msg)              { log("DEBUG", tag, msg); }
	
	public static void  info(String msg)                          { log( "INFO", msg); }
	public static void  info(String msg, Throwable t)             { log( "INFO", msg, t); }
	public static void  info(String tag, String msg, Throwable t) { log( "INFO", tag, msg, t); }
	public static void  info(String tag, String msg)              { log( "INFO", tag, msg); }
	
	public static void  warn(String msg)                          { log( "WARN", msg); }
	public static void  warn(String msg, Throwable t)             { log( "WARN", msg, t); }
	public static void  warn(String tag, String msg, Throwable t) { log( "WARN", tag, msg, t); }
	public static void  warn(String tag, String msg)              { log( "WARN", tag, msg); }
	
	public static void error(String msg)                          { log("ERROR", msg); }
	public static void error(String msg, Throwable t)             { log("ERROR", msg, t); }
	public static void error(String tag, String msg, Throwable t) { log("ERROR", tag, msg, t); }
	public static void error(String tag, String msg)              { log("ERROR", tag, msg); }
	
}
