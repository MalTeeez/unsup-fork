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
