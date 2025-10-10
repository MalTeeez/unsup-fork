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

	@Override
	public boolean equals(Object obj) {
		return unbiased.equals(obj);
	}

	@Override
	public int hashCode() {
		return unbiased.hashCode();
	}

	@Override
	public String toString() {
		return unbiased.toString();
	}

}
