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

package com.unascribed.sup.puppet.opengl.pieces;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.lwjgl.PointerBuffer;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;

import com.github.bsideup.jabel.Desugar;
import com.unascribed.sup.bootstrap.Util;
import com.unascribed.sup.data.SysProps;
import com.unascribed.sup.puppet.FontResources;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.opengl.util.CJK;

import static org.lwjgl.util.freetype.FreeType.*;
import static com.unascribed.sup.puppet.opengl.util.GL.*;
import static org.lwjgl.system.MemoryUtil.*;

public class FontManager {
	
	public double dpiScale = 1;
	private final Map<String, FT_Face> ftFaces = new HashMap<>();
	private final long ftLibrary;
	private final FT_Bitmap scratchBitmap;
	
	private static final String LOCALE_FONT = "#PREFERRED_LOCALE_FONT";
	private static final String LOCALE_FONT_BOLD = "#PREFERRED_LOCALE_FONT_BOLD";
	
	private final Map<CacheKey, CachedTexture> cachedTextures = new HashMap<>();
	
	public enum Face {
		REGULAR("locale/default/FiraGO-Regular.ttf", LOCALE_FONT),
		BOLD("locale/default/FiraGO-Bold.ttf", LOCALE_FONT_BOLD),
		ITALIC("locale/default/FiraGO-Italic.ttf", LOCALE_FONT),
		BOLDITALIC("locale/default/FiraGO-BoldItalic.ttf", LOCALE_FONT_BOLD),
		;
		public final String[] filenames;

		Face(String... filenames) {
			this.filenames = filenames;
		}
		
	}
	
	public FontManager() {
		{
			PointerBuffer libraryBuf = memAllocPointer(1);
			int ftError = FT_Init_FreeType(libraryBuf);
			if (ftError != 0) {
				throw new RuntimeException("Failed to initialize FreeType: "+FT_Error_String(ftError));
			}
			ftLibrary = libraryBuf.get(0);
			memFree(libraryBuf);
		}
		
		scratchBitmap = FT_Bitmap.malloc();
		PointerBuffer buf = memPointerBuffer(scratchBitmap.address()+FT_Bitmap.BUFFER, 1);
		buf.put(memAlloc(1));
	}
	
	private static final Pattern WHITESPACE = Pattern.compile(" +");
	
	public float[] drawWrapped(Face f, float baseX, float x, float y, float size, float maxWidth, String str) {
		String[] words = WHITESPACE.matcher(str).replaceAll(" ").split(" ");
		float spaceWidth = measureString(f, size, " ");
		for (String word : words) {
			float w = measureString(f, size, word);
			if (x+w >= baseX+maxWidth) {
				x = baseX;
				y += alignToScreenPixel(size*1.2f);
			}
			drawString(f, x, y, size, word);
			x += w;
			x += spaceWidth;
		}
		if (!str.endsWith(" ")) {
			x -= spaceWidth;
		}
		return new float[] { x, y };
	}
	
	public float drawString(Face f, float x, float y, float size, String str) {
		if (dpiScale != 1) {
			glPushMatrix();
			x = alignToScreenPixel((x+getState().glTranslationX)*dpiScale);
			y = alignToScreenPixel((y+getState().glTranslationY)*dpiScale);
			size = (float)(size*dpiScale);
			glLoadIdentity();
			glTranslatef(0, 0, -200);
		}
		
		float totalW = 0;
		int ftSize = (int)(size*64*dpiScale);
		glEnable(GL_TEXTURE_2D);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glEnable(GL_BLEND);
		for (int cp : str.codePoints().toArray()) {
			CacheKey key = new CacheKey(f, ftSize, cp);
			CachedTexture ct = cachedTextures.get(key);
			if (ct == null) {
				ct = new CachedTexture();
				FT_Face ftFace = chooseFace(f, cp, ftSize);
				if (ftFace == null) continue;
				if (ftFace.glyph().bitmap().address() == NULL) continue;
				if (FT_Bitmap_Convert(ftLibrary, ftFace.glyph().bitmap(), scratchBitmap, 4) != 0) continue;
				ct.x = ftFace.glyph().bitmap_left();
				ct.y = ftFace.glyph().bitmap_top();
				ct.width = scratchBitmap.width();
				ct.height = scratchBitmap.rows();
				if (scratchBitmap.pitch() < 0) {
					ct.flip = true;
				}
				if (ct.width != 0 && ct.height != 0) {
					ct.name = glGenTextures();
					glBindTexture(GL_TEXTURE_2D, ct.name);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
					glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, ct.width, ct.height, 0, GL_ALPHA, GL_UNSIGNED_BYTE, scratchBitmap.buffer(ct.width*ct.height));
				}
				ct.advanceX = ftFace.glyph().advance().x();
				ct.advanceY = ftFace.glyph().advance().y();
				
				cachedTextures.put(key, ct);
			}
			if (ct.name != 0) {
				int minV = 0;
				int maxV = 1;
				
				if (ct.flip) {
					maxV = 0;
					minV = 1;
				}
				
				// only align x and not y to avoid "serial killer letters" effect often seen in e.g. Qt6
				double xo = alignToScreenPixel(x+ct.x/dpiScale);
				double yo = y-(ct.y/dpiScale);
	
				glBindTexture(GL_TEXTURE_2D, ct.name);
				glBegin(GL_QUADS);
					glTexCoord2i(0, minV);
					glVertex2d(xo, yo);
					glTexCoord2i(1, minV);
					glVertex2d(xo+(ct.width/dpiScale), yo);
					glTexCoord2i(1, maxV);
					glVertex2d(xo+(ct.width/dpiScale), yo+(ct.height/dpiScale));
					glTexCoord2i(0, maxV);
					glVertex2d(xo, yo+(ct.height/dpiScale));
				glEnd();
			}
			
			float glyphW = alignToScreenPixel(ct.advanceX/64f/dpiScale);
			totalW += glyphW;
			x += glyphW;
			y += ct.advanceY/64f/dpiScale;
		}
		glDisable(GL_TEXTURE_2D);
		if (dpiScale != 1) {
			glPopMatrix();
		}
		return totalW;
	}

	private float alignToScreenPixel(double x) {
		double newx = Math.round(x*dpiScale)/dpiScale;
		return (float)newx;
	}

	public float measureString(Face f, float size, String str) {
		size = alignToScreenPixel(size);
		int ftSize = (int)(size*64);
		float w = 0;
		for (int cp : str.codePoints().toArray()) {
			FT_Face ftFace = chooseFace(f, cp, ftSize);
			if (ftFace == null) continue;
			w += ftFace.glyph().advance().x()/64f;
		}
		return w;
	}
	
	private final Set<Integer> missingGlyphs = new HashSet<>();
	
	private FT_Face chooseFace(Face f, int cp, int ftSize) {
		FT_Face ftFace = null;
		boolean missing = missingGlyphs.contains(cp);
		if (!missing) {
			for (int j = 0; j < f.filenames.length; j++) {
				FT_Face ftFaceTmp = ftFaces.computeIfAbsent(f.filenames[j], this::loadFont);
				if (ftFaceTmp != null) {
					FT_Set_Char_Size(ftFaceTmp, 0, ftSize, 72, 72);
					if (FT_Get_Char_Index(ftFaceTmp, cp) != 0) {
						if (FT_Load_Char(ftFaceTmp, cp, FT_LOAD_RENDER) == 0) {
							ftFace = ftFaceTmp;
							break;
						}
					}
				}
			}
		}
		if (ftFace == null) {
			if (!missing) {
				Puppet.log("WARN", "Can't find a glyph for "+new String(new int[] {cp}, 0, 1)+" (U+"+Integer.toHexString(cp).toUpperCase(Locale.ROOT)+")");
				missingGlyphs.add(cp);
			}
			ftFace = ftFaces.computeIfAbsent(f.filenames[0], this::loadFont);
			if (FT_Load_Char(ftFace, '\uFFFD', FT_LOAD_RENDER) != 0) {
				return null;
			}
		}
		return ftFace;
	}

	private FT_Face loadFont(String name) {
		PointerBuffer ftFacePtr = memAllocPointer(1);
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			File f = null;
			if (LOCALE_FONT.equals(name) || LOCALE_FONT_BOLD.equals(name)) {
				boolean bold = LOCALE_FONT_BOLD.equals(name);
				f = CJK.getOSPreferredFont(SysProps.LANGUAGE, bold);
				if (f == null) {
					name = "locale/"+SysProps.LANGUAGE+"/"+(bold?"Bold":"Regular")+".otf";
				}
			}
			if (f != null) {
				name = f.getName();
				try (var in = new FileInputStream(f)) {
					Util.copy(in, baos);
				} catch (IOException e) {
					Puppet.log("WARN", "Failed to load system preferred locale font "+f, e);
					return null;
				}
			} else {
				try (var in = FontResources.get(name)) {
					if (in == null) return null;
					Util.copy(in, baos);
				} catch (IOException e) {
					Puppet.log("WARN", "Failed to load font "+name, e);
					return null;
				}
			}
			ByteBuffer buf = memAlloc(baos.size());
			buf.put(baos.toByteArray());
			buf.flip();
			int error = FT_New_Memory_Face(ftLibrary, buf, 0, ftFacePtr);
			if (error != 0) {
				Puppet.log("WARN", "Failed to load font "+name+": "+FT_Error_String(error));
				return null;
			}
			Puppet.log("DEBUG", "Loaded font "+name+" successfully");
			return FT_Face.create(ftFacePtr.get(0));
		} finally {
			memFree(ftFacePtr);
		}
	}

	@Desugar
	private record CacheKey(Face face, float size, int codepoint) {}
	
	private static class CachedTexture {
		public int name, width, height, x, y;
		public float advanceX, advanceY;
		public boolean flip;
	}
	
}
