package jadx.core.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.NotNull;

/**
 * Simple immutable list implementation
 * Warning: some methods not implemented!
 */
public final class ImmutableList<E> implements List<E>, RandomAccess {
	private final E[] arr;

	@SuppressWarnings("unchecked")
	public ImmutableList(Object[] arr) {
		this.arr = (E[]) Objects.requireNonNull(arr);
	}

	@Override
	public int size() {
		return arr.length;
	}

	@Override
	public boolean isEmpty() {
		return arr.length == 0;
	}

	@Override
	public E get(int index) {
		return arr[index];
	}

	@Override
	public int indexOf(Object o) {
		int len = arr.length;
		for (int i = 0; i < len; i++) {
			E e = arr[i];
			if (Objects.equals(e, o)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		for (int i = arr.length - 1; i > 0; i--) {
			E e = arr[i];
			if (Objects.equals(e, o)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean contains(Object o) {
		return indexOf(o) != -1;
	}

	@Override
	public boolean containsAll(@NotNull Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {
			private int index = 0;
			private int len = arr.length;

			@Override
			public boolean hasNext() {
				return index < len;
			}

			@Override
			public E next() {
				return arr[index++];
			}
		};
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		for (E e : arr) {
			action.accept(e);
		}
	}

	@NotNull
	@Override
	public Object[] toArray() {
		return arr;
	}

	@NotNull
	@Override
	@SuppressWarnings("unchecked")
	public <T> T[] toArray(@NotNull T[] a) {
		return (T[]) arr;
	}

	@Override
	public boolean add(E e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(@NotNull Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, @NotNull Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(@NotNull Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(@NotNull Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void replaceAll(UnaryOperator<E> operator) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sort(Comparator<? super E> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public E set(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public E remove(int index) {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public ListIterator<E> listIterator() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public ListIterator<E> listIterator(int index) {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ImmutableList<?> that = (ImmutableList<?>) o;
		return Arrays.equals(arr, that.arr);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(arr);
	}

	@Override
	public String toString() {
		return "ImmutableList{" + Arrays.toString(arr) + '}';
	}
}
