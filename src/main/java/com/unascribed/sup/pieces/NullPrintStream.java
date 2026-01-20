/*
 * This file is part of unsup.
 * Copyright © 2020-2021, 2023, 2025 Exa Skye
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

package com.unascribed.sup.pieces;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;

public class NullPrintStream extends PrintStream {

	public static final NullPrintStream INSTANCE = new NullPrintStream();
	
	public NullPrintStream() {
		super(NullOutputStream.INSTANCE, false);
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

	@Override
	public boolean checkError() {
		return false;
	}

	@Override
	protected void setError() {
	}

	@Override
	protected void clearError() {
	}

	@Override
	public void write(int b) {
	}

	@Override
	public void write(byte[] buf, int off, int len) {
	}

	@Override
	public void print(boolean b) {
	}

	@Override
	public void print(char c) {
	}

	@Override
	public void print(int i) {
	}

	@Override
	public void print(long l) {
	}

	@Override
	public void print(float f) {
	}

	@Override
	public void print(double d) {
	}

	@Override
	public void print(char[] s) {
	}

	@Override
	public void print(String s) {
	}

	@Override
	public void print(Object obj) {
	}

	@Override
	public void println() {
	}

	@Override
	public void println(boolean x) {
	}

	@Override
	public void println(char x) {
	}

	@Override
	public void println(int x) {
	}

	@Override
	public void println(long x) {
	}

	@Override
	public void println(float x) {
	}

	@Override
	public void println(double x) {
	}

	@Override
	public void println(char[] x) {
	}

	@Override
	public void println(String x) {
	}

	@Override
	public void println(Object x) {
	}

	@Override
	public PrintStream printf(String format, Object... args) {
		return this;
	}

	@Override
	public PrintStream printf(Locale l, String format, Object... args) {
		return this;
	}

	@Override
	public PrintStream format(String format, Object... args) {
		return this;
	}

	@Override
	public PrintStream format(Locale l, String format, Object... args) {
		return this;
	}

	@Override
	public PrintStream append(CharSequence csq) {
		return this;
	}

	@Override
	public PrintStream append(CharSequence csq, int start, int end) {
		return this;
	}

	@Override
	public PrintStream append(char c) {
		return this;
	}

	@Override
	public void write(byte[] b) throws IOException {
	}
	
	@Override
	public String toString() {
		return "NullPrintStream.INSTANCE";
	}
	
}
