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

package com.unascribed.sup.agent.signing;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SignatureException;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

public class RawEdDSAProvider implements SigProvider {

	private final ThreadLocal<EdDSAEngine> engine;
	
	public RawEdDSAProvider(EdDSAPublicKey key) throws InvalidKeyException {
		new EdDSAEngine().initVerify(key);
		this.engine = ThreadLocal.withInitial(() -> {
			EdDSAEngine n = new EdDSAEngine();
			try {
				n.initVerify(key);
				n.setParameter(EdDSAEngine.ONE_SHOT_MODE);
			} catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
				throw new AssertionError(e);
			}
			return n;
		});
	}

	@Override
	public boolean verify(byte[] data, byte[] signature) throws SignatureException {
		return engine.get().verifyOneShot(data, signature);
	}
	
}
