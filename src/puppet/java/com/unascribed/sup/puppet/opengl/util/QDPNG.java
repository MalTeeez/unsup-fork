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

package com.unascribed.sup.puppet.opengl.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import me.saharnooby.qoi.QOIImage;

/**
 * An extremely minimal PNG writer that puts zero effort into making small files.
 */
public class QDPNG {

	private static final byte[] PNG = "PNG\r\n".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] IHDR = "IHDR".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] IDAT = "IDAT".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] IEND = "IEND".getBytes(StandardCharsets.US_ASCII);
	
	public static byte[] write(QOIImage out) {
		return new QDPNG(out).write();
	}

	private final QOIImage img;
	
	private final ByteArrayOutputStream root = new ByteArrayOutputStream();
	private final DataOutputStream dosRoot = new DataOutputStream(root);
	
	private final ByteArrayOutputStream tmp = new ByteArrayOutputStream();
	private final CheckedOutputStream crcTmp = new CheckedOutputStream(tmp, new CRC32());
	private final DataOutputStream dosTmp = new DataOutputStream(crcTmp);
	
	private DataOutputStream out = dosRoot;
	
	private QDPNG(QOIImage img) {
		this.img = img;
	}
	
	private byte[] write() {
		try {
			root.reset();
			out = dosRoot;
			
			out.write(0x89);
			out.write(PNG);
			out.write(0x1A);
			out.write('\n');
			beginChunk(IHDR);
				out.writeInt(img.getWidth());
				out.writeInt(img.getHeight());
				out.writeByte(8); // 8bpc
				out.writeByte(6); // RGBA
				out.writeByte(0); // compression method, 0 is DEFLATE (no other methods defined)
				out.writeByte(0); // filter method, 0 is the only legal value
				out.writeByte(0); // interlace method, 0 is uninterlaced
			endChunk();
			beginChunk(IDAT);
				DeflaterOutputStream zout = new DeflaterOutputStream(crcTmp, new Deflater(2));
				for (int y = 0; y < img.getHeight(); y++) {
					zout.write(0); // no filtering
					zout.write(img.getPixelData(), y*img.getWidth()*4, img.getWidth()*4);
				}
				zout.finish();
			endChunk();
			beginChunk(IEND);
			endChunk();
			
			return root.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void beginChunk(byte[] name) throws IOException {
		tmp.reset();
		crcTmp.getChecksum().reset();
		out = dosTmp;
		out.write(name);
	}
	
	private void endChunk() throws IOException {
		out.flush();
		out = dosRoot;
		out.writeInt(tmp.size()-4);
		tmp.writeTo(out);
		out.writeInt((int)crcTmp.getChecksum().getValue());
	}

}
