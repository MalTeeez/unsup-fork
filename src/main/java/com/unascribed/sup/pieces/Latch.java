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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Implements a very basic synchronization primitive that blocks one thread
 * until another thread releases it. Thin abstraction for {@link Object#wait}
 * and {@link Object#notify}, that deals with spurious wakeups.
 * <p>
 * If more than one thread waits on a Latch, an arbitrarily chosen thread will
 * be woken up for each call to {@link #release}.
 * <p>
 * To wait for more than one unit of work, use {@link CountDownLatch}, from
 * which this class borrows its name, as it behaves similarly to a CountDownLatch
 * with a {@code count} of 1. However, CountDownLatch is built on the more
 * robust {@link AbstractQueuedSynchronizer} class, rather than bare
 * {@code wait}/{@code notify}, which are only really sufficient for single-unit
 * latches with only two threads, which is what this class is designed for.
 */
public class Latch {

	// we synchronize on this mutex rather than `this` to prevent users of this
	// class from accidentally using `wait()` and having almost-correct behavior
	private final Object mutex = new Object();
	private volatile boolean complete = false;

	/**
	 * Wait for this Latch to be released by another thread.
	 * @throws InterruptedException if the thread was interrupted while waiting
	 */
	public void await() throws InterruptedException {
		synchronized (mutex) {
			while (!complete) {
				mutex.wait();
			}
		}
	}

	/**
	 * Wait for this Latch to be released by another thread, and ignore any
	 * InterruptedExceptions.
	 */
	public void awaitUninterruptibly() {
		// this is a copy of #await instead of calling it and catching exceptions
		// as the while loop must be contained within the synchronized block for
		// correct behavior
		synchronized (mutex) {
			while (!complete) {
				try {
					mutex.wait();
				} catch (InterruptedException e) {}
			}
		}
	}

	/**
	 * Allow one arbitrarily chosen thread waiting on this Latch to continue
	 * running.
	 */
	public void release() {
		synchronized (mutex) {
			complete = true;
			mutex.notify();
		}
	}

}
