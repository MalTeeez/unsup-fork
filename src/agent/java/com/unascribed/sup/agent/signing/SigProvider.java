/*
 * This file is part of unsup.
 * Copyright © 2023, 2025 Exa Skye
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

package com.unascribed.sup.agent.signing;

import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

public interface SigProvider {

	boolean verify(byte[] data, byte[] signature) throws SignatureException;

	static SigProvider parse(String line) throws InvalidKeySpecException, InvalidKeyException {
		if (line.startsWith("ed25519 ")) {
			return new RawEdDSAProvider(new EdDSAPublicKey(new X509EncodedKeySpec(Base64.getDecoder().decode(line.substring(8)))));
		} else if (line.startsWith("signify ")) {
			byte[] data = Base64.getDecoder().decode(line.substring(8));
			if (SignifyProvider.checkFormat(data, SignifyProvider.Ed)) {
				return new SignifyProvider(SignifyProvider.toLong(data, 2),
						new EdDSAPublicKey(new EdDSAPublicKeySpec(Arrays.copyOfRange(data, 10, data.length),
								EdDSANamedCurveTable.ED_25519_CURVE_SPEC)));
			} else {
				throw new IllegalArgumentException("Unknown signify key format");
			}
		} else {
			throw new IllegalArgumentException("Unknown key kind, expected ed25519 or signify");
		}
	}
	
	static SigProvider of(String line) {
		try {
			return parse(line);
		} catch (InvalidKeyException | InvalidKeySpecException e) {
			throw new AssertionError(e);
		}
	}

}
