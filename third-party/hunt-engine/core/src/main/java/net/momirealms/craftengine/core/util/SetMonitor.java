package net.momirealms.craftengine.core.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

public class SetMonitor<E> implements Set<E> {
    private final Set<E> set;
    private final Consumer<E> addConsumer;
    private final Consumer<Object> removeConsumer;

    public SetMonitor(Set<E> set, Consumer<E> addConsumer, Consumer<Object> removeConsumer) {
        this.set = set;
        this.addConsumer = addConsumer;
        this.removeConsumer = removeConsumer;
        for (E element : set) {
            this.addConsumer.accept(element);
        }
    }

    @Override
    public boolean add(E e) {
        this.addConsumer.accept(e);
        return this.set.add(e);
    }

    @Override
    public boolean remove(Object o) {
        this.removeConsumer.accept(o);
        return this.set.remove(o);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        for (E element : c) {
            this.addConsumer.accept(element);
        }
        return this.set.addAll(c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        for (Object o : c) {
            this.removeConsumer.accept(o);
        }
        return this.set.removeAll(c);
    }

    @Override
    public void clear() {
        for (E element : this.set) {
            this.removeConsumer.accept(element);
        }
        this.set.clear();
    }

    @Override
    public int size() {
        return this.set.size();
    }

    @Override
    public boolean isEmpty() {
        return this.set.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.set.contains(o);
    }

    @Override
    public @NotNull Iterator<E> iterator() {
        return this.set.iterator();
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        return this.set.toArray();
    }

    @Override
    public @NotNull <T> T @NotNull [] toArray(@NotNull T[] a) {
        return this.set.toArray(a);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return this.set.containsAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return this.set.retainAll(c);
    }
}
