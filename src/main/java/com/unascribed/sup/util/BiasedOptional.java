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

package com.unascribed.sup.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import com.github.bsideup.jabel.Desugar;

/**
 * An Optional with an inherent default value.
 */
@Desugar
public record BiasedOptional<T>(Optional<T> unbiased, T bias) {
	
	public T orBias() {
		if (unbiased.isPresent()) return unbiased.get();
		return bias;
	}
	
	public BiasedOptional<T> withBias(T bias) {
		return new BiasedOptional<>(unbiased, bias);
	}

	// added after java 8
	
	public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
		if (unbiased.isPresent()) {
			action.accept(unbiased.get());
		} else {
			emptyAction.run();
		}
	}

	public boolean isEmpty() {
		return !unbiased.isPresent();
	}

	@SuppressWarnings("unchecked")
	public BiasedOptional<T> or(Supplier<? extends Optional<? extends T>> supplier) {
		if (unbiased.isPresent()) return this;
		return new BiasedOptional<>((Optional<T>)supplier.get(), bias);
	}

	public T orElseThrow() {
		return unbiased.get();
	}
	
	// typekeeping delegates

	public BiasedOptional<T> filter(Predicate<? super T> predicate) {
		return new BiasedOptional<>(unbiased.filter(predicate), bias);
	}
	
	// delegates

	public boolean isPresent() {
		return unbiased.isPresent();
	}

	public void ifPresent(Consumer<? super T> action) {
		unbiased.ifPresent(action);
	}

	public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
		return unbiased.map(mapper);
	}

	public T orElse(T other) {
		return unbiased.orElse(other);
	}

	public T orElseGet(Supplier<? extends T> supplier) {
		return unbiased.orElseGet(supplier);
	}

	public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		return unbiased.orElseThrow(exceptionSupplier);
	}

}
