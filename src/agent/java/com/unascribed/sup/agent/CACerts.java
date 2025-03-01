/*
 * This file is part of unsup.
 * Copyright © 2020-2025 Una Kearney (unascribed) and contributors
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

package com.unascribed.sup.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.util.Resources;

class CACerts {

	public static final List<X509Certificate> certs;
	
	static {
		List<X509Certificate> certsTmp = Collections.emptyList();
		try (ZipInputStream zis = new ZipInputStream(new BrotliInputStream(Resources.open("assets/cacerts.zip.br")))) {
			List<X509Certificate> out = new ArrayList<>();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ZipEntry en;
			while ((en = zis.getNextEntry()) != null) {
				baos.reset();
				try {
					out.add((X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(zis));
					Log.debug("Loaded CA cert "+en.getName());
				} catch (CertificateException e) {
					Log.error("Failed to parse CA cert "+en.getName(), e);
				}
			}
			certsTmp = Collections.unmodifiableList(out);
		} catch (IOException e) {
			Log.error("Failed to load cacerts.zip.br", e);
		}
		certs = certsTmp;
	}
	
}
