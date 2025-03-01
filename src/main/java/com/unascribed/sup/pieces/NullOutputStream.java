/*
 * This file is part of unsup.
 * Copyright © 2020-2021, 2023, 2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup.pieces;

import java.io.IOException;
import java.io.OutputStream;

public class NullOutputStream extends OutputStream {

	public static final NullOutputStream INSTANCE = new NullOutputStream();
	
	private NullOutputStream() {}
	
	@Override
	public void write(int b) throws IOException {

	}

	@Override
	public void write(byte[] b) throws IOException {
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}
	
	@Override
	public String toString() {
		return "NullOutputStream.INSTANCE";
	}

}
