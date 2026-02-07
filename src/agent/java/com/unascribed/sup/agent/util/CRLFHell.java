package com.unascribed.sup.agent.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.unascribed.sup.agent.data.HashFunction;
import com.unascribed.sup.util.Bases;

public class CRLFHell {

	public enum CorruptionType {
		UNKNOWN,
		EXPECTED_UNIX_GOT_DOS,
		EXPECTED_DOS_GOT_UNIX,
		;
	}
	
	public static CorruptionType checkForCorruption(HashFunction func, String expectedHash, InputStream src) throws IOException {
		var dos = func.createMessageDigest();
		var unix = func.createMessageDigest();
		var raw = func.createMessageDigest();
		if (!(src instanceof ByteArrayInputStream) && !(src instanceof BufferedInputStream)) {
			src = new BufferedInputStream(src);
		}
		while (true) {
			int b = src.read();
			if (b == -1) break;
			raw.update((byte)b);
			if (b == '\r') continue;
			if (b == '\n') {
				dos.update((byte)'\r');
				dos.update((byte)'\n');
				unix.update((byte)'\n');
			} else {
				dos.update((byte)b);
				unix.update((byte)b);
			}
		}
		String dosHash = Bases.bytesToHex(dos.digest());
		String unixHash = Bases.bytesToHex(unix.digest());
		String hash = Bases.bytesToHex(raw.digest());
		if (expectedHash.equals(dosHash) && hash.equals(unixHash)) {
			return CorruptionType.EXPECTED_DOS_GOT_UNIX;
		} else if (expectedHash.equals(unixHash) && hash.equals(dosHash)) {
			return CorruptionType.EXPECTED_UNIX_GOT_DOS;
		} else {
			return CorruptionType.UNKNOWN;
		}
	}
	
}
