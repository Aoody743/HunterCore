package net.momirealms.craftengine.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

@SuppressWarnings({"DuplicatedCode", "rawtypes", "unchecked"})
public final class ClassIdentityMap<V> {
    private Impl<V> impl = Map0.instance();

    public void put(@NotNull Class<?> key, @NotNull V value) {
        this.impl = this.impl.put(key, value);
    }

    @Nullable
    public V get(@NotNull Class<?> key) {
        return this.impl.get(key);
    }

    public int size() {
        return this.impl.size();
    }

    public boolean isEmpty() {
        return this.impl.size() == 0;
    }

    private static abstract class Impl<V> {
        @Nullable abstract V get(@NotNull Class<?> key);
        @NotNull  abstract Impl<V> put(@NotNull Class<?> key, @NotNull V value);
        abstract int size();
    }

    private static final class Map0<V> extends Impl<V> {
        private static final Map0 INSTANCE = new Map0<>();

        static <V> Map0<V> instance() { return INSTANCE; }

        public V get(@NotNull Class<?> key) { return null; }
        public int size() { return 0; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            return new Map1<>(key, value);
        }
    }

    private static final class Map1<V> extends Impl<V> {
        private final Class<?> k0; private final V v0;

        Map1(Class<?> k0, V v0) {
            this.k0 = k0; this.v0 = v0;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            return null;
        }

        public int size() { return 1; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map1<>(k0, value);
            return new Map2<>(k0, v0, key, value);
        }
    }

    private static final class Map2<V> extends Impl<V> {
        private final Class<?> k0, k1;
        private final V v0, v1;

        Map2(Class<?> k0, V v0, Class<?> k1, V v1) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            return null;
        }

        public int size() { return 2; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map2<>(k0, value, k1, v1);
            if (key == k1) return new Map2<>(k0, v0,    k1, value);
            return new Map3<>(k0, v0, k1, v1, key, value);
        }
    }

    private static final class Map3<V> extends Impl<V> {
        private final Class<?> k0, k1, k2;
        private final V v0, v1, v2;

        Map3(Class<?> k0, V v0, Class<?> k1, V v1, Class<?> k2, V v2) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            return null;
        }

        public int size() { return 3; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map3<>(k0, value, k1, v1,    k2, v2);
            if (key == k1) return new Map3<>(k0, v0,    k1, value, k2, v2);
            if (key == k2) return new Map3<>(k0, v0,    k1, v1,    k2, value);
            return new Map4<>(k0, v0, k1, v1, k2, v2, key, value);
        }
    }

    private static final class Map4<V> extends Impl<V> {
        private final Class<?> k0, k1, k2, k3;
        private final V v0, v1, v2, v3;

        Map4(Class<?> k0, V v0, Class<?> k1, V v1,
             Class<?> k2, V v2, Class<?> k3, V v3) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
            this.k3 = k3; this.v3 = v3;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            if (key == k3) return v3;
            return null;
        }

        public int size() { return 4; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map4<>(k0, value, k1, v1,    k2, v2,    k3, v3);
            if (key == k1) return new Map4<>(k0, v0,    k1, value, k2, v2,    k3, v3);
            if (key == k2) return new Map4<>(k0, v0,    k1, v1,    k2, value, k3, v3);
            if (key == k3) return new Map4<>(k0, v0,    k1, v1,    k2, v2,    k3, value);
            return new Map5<>(k0, v0, k1, v1, k2, v2, k3, v3, key, value);
        }
    }

    private static final class Map5<V> extends Impl<V> {
        private final Class<?> k0, k1, k2, k3, k4;
        private final V v0, v1, v2, v3, v4;

        Map5(Class<?> k0, V v0, Class<?> k1, V v1, Class<?> k2, V v2,
             Class<?> k3, V v3, Class<?> k4, V v4) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
            this.k3 = k3; this.v3 = v3;
            this.k4 = k4; this.v4 = v4;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            if (key == k3) return v3;
            if (key == k4) return v4;
            return null;
        }

        public int size() { return 5; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map5<>(k0, value, k1, v1,    k2, v2,    k3, v3,    k4, v4);
            if (key == k1) return new Map5<>(k0, v0,    k1, value, k2, v2,    k3, v3,    k4, v4);
            if (key == k2) return new Map5<>(k0, v0,    k1, v1,    k2, value, k3, v3,    k4, v4);
            if (key == k3) return new Map5<>(k0, v0,    k1, v1,    k2, v2,    k3, value, k4, v4);
            if (key == k4) return new Map5<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, value);
            return new Map6<>(k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, key, value);
        }
    }

    private static final class Map6<V> extends Impl<V> {
        private final Class<?> k0, k1, k2, k3, k4, k5;
        private final V v0, v1, v2, v3, v4, v5;

        Map6(Class<?> k0, V v0, Class<?> k1, V v1, Class<?> k2, V v2,
             Class<?> k3, V v3, Class<?> k4, V v4, Class<?> k5, V v5) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
            this.k3 = k3; this.v3 = v3;
            this.k4 = k4; this.v4 = v4;
            this.k5 = k5; this.v5 = v5;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            if (key == k3) return v3;
            if (key == k4) return v4;
            if (key == k5) return v5;
            return null;
        }

        public int size() { return 6; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map6<>(k0, value, k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5);
            if (key == k1) return new Map6<>(k0, v0,    k1, value, k2, v2,    k3, v3,    k4, v4,    k5, v5);
            if (key == k2) return new Map6<>(k0, v0,    k1, v1,    k2, value, k3, v3,    k4, v4,    k5, v5);
            if (key == k3) return new Map6<>(k0, v0,    k1, v1,    k2, v2,    k3, value, k4, v4,    k5, v5);
            if (key == k4) return new Map6<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, value, k5, v5);
            if (key == k5) return new Map6<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, value);
            return new Map7<>(k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, key, value);
        }
    }

    private static final class Map7<V> extends Impl<V> {
        private final Class<?> k0, k1, k2, k3, k4, k5, k6;
        private final V v0, v1, v2, v3, v4, v5, v6;

        Map7(Class<?> k0, V v0, Class<?> k1, V v1, Class<?> k2, V v2,
             Class<?> k3, V v3, Class<?> k4, V v4, Class<?> k5, V v5,
             Class<?> k6, V v6) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
            this.k3 = k3; this.v3 = v3;
            this.k4 = k4; this.v4 = v4;
            this.k5 = k5; this.v5 = v5;
            this.k6 = k6; this.v6 = v6;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            if (key == k3) return v3;
            if (key == k4) return v4;
            if (key == k5) return v5;
            if (key == k6) return v6;
            return null;
        }

        public int size() { return 7; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map7<>(k0, value, k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6);
            if (key == k1) return new Map7<>(k0, v0,    k1, value, k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6);
            if (key == k2) return new Map7<>(k0, v0,    k1, v1,    k2, value, k3, v3,    k4, v4,    k5, v5,    k6, v6);
            if (key == k3) return new Map7<>(k0, v0,    k1, v1,    k2, v2,    k3, value, k4, v4,    k5, v5,    k6, v6);
            if (key == k4) return new Map7<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, value, k5, v5,    k6, v6);
            if (key == k5) return new Map7<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, value, k6, v6);
            if (key == k6) return new Map7<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, value);
            return new Map8<>(k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, key, value);
        }
    }

    private static final class Map8<V> extends Impl<V> {
        private final Class<?> k0, k1, k2, k3, k4, k5, k6, k7;
        private final V v0, v1, v2, v3, v4, v5, v6, v7;

        Map8(Class<?> k0, V v0, Class<?> k1, V v1, Class<?> k2, V v2, Class<?> k3, V v3,
             Class<?> k4, V v4, Class<?> k5, V v5, Class<?> k6, V v6, Class<?> k7, V v7) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
            this.k3 = k3; this.v3 = v3;
            this.k4 = k4; this.v4 = v4;
            this.k5 = k5; this.v5 = v5;
            this.k6 = k6; this.v6 = v6;
            this.k7 = k7; this.v7 = v7;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            if (key == k3) return v3;
            if (key == k4) return v4;
            if (key == k5) return v5;
            if (key == k6) return v6;
            if (key == k7) return v7;
            return null;
        }

        public int size() { return 8; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map8<>(k0, value, k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7);
            if (key == k1) return new Map8<>(k0, v0,    k1, value, k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7);
            if (key == k2) return new Map8<>(k0, v0,    k1, v1,    k2, value, k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7);
            if (key == k3) return new Map8<>(k0, v0,    k1, v1,    k2, v2,    k3, value, k4, v4,    k5, v5,    k6, v6,    k7, v7);
            if (key == k4) return new Map8<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, value, k5, v5,    k6, v6,    k7, v7);
            if (key == k5) return new Map8<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, value, k6, v6,    k7, v7);
            if (key == k6) return new Map8<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, value, k7, v7);
            if (key == k7) return new Map8<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, value);
            return new Map9<>(k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, key, value);
        }
    }

    private static final class Map9<V> extends Impl<V> {
        private final Class<?> k0, k1, k2, k3, k4, k5, k6, k7, k8;
        private final V v0, v1, v2, v3, v4, v5, v6, v7, v8;

        Map9(Class<?> k0, V v0, Class<?> k1, V v1, Class<?> k2, V v2,
             Class<?> k3, V v3, Class<?> k4, V v4, Class<?> k5, V v5,
             Class<?> k6, V v6, Class<?> k7, V v7, Class<?> k8, V v8) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
            this.k3 = k3; this.v3 = v3;
            this.k4 = k4; this.v4 = v4;
            this.k5 = k5; this.v5 = v5;
            this.k6 = k6; this.v6 = v6;
            this.k7 = k7; this.v7 = v7;
            this.k8 = k8; this.v8 = v8;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            if (key == k3) return v3;
            if (key == k4) return v4;
            if (key == k5) return v5;
            if (key == k6) return v6;
            if (key == k7) return v7;
            if (key == k8) return v8;
            return null;
        }

        public int size() { return 9; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map9<>(k0, value, k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8);
            if (key == k1) return new Map9<>(k0, v0,    k1, value, k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8);
            if (key == k2) return new Map9<>(k0, v0,    k1, v1,    k2, value, k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8);
            if (key == k3) return new Map9<>(k0, v0,    k1, v1,    k2, v2,    k3, value, k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8);
            if (key == k4) return new Map9<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, value, k5, v5,    k6, v6,    k7, v7,    k8, v8);
            if (key == k5) return new Map9<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, value, k6, v6,    k7, v7,    k8, v8);
            if (key == k6) return new Map9<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, value, k7, v7,    k8, v8);
            if (key == k7) return new Map9<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, value, k8, v8);
            if (key == k8) return new Map9<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, value);
            return new Map10<>(k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, key, value);
        }
    }

    private static final class Map10<V> extends Impl<V> {
        private final Class<?> k0, k1, k2, k3, k4, k5, k6, k7, k8, k9;
        private final V v0, v1, v2, v3, v4, v5, v6, v7, v8, v9;

        Map10(Class<?> k0, V v0, Class<?> k1, V v1, Class<?> k2, V v2,
              Class<?> k3, V v3, Class<?> k4, V v4, Class<?> k5, V v5,
              Class<?> k6, V v6, Class<?> k7, V v7, Class<?> k8, V v8,
              Class<?> k9, V v9) {
            this.k0 = k0; this.v0 = v0;
            this.k1 = k1; this.v1 = v1;
            this.k2 = k2; this.v2 = v2;
            this.k3 = k3; this.v3 = v3;
            this.k4 = k4; this.v4 = v4;
            this.k5 = k5; this.v5 = v5;
            this.k6 = k6; this.v6 = v6;
            this.k7 = k7; this.v7 = v7;
            this.k8 = k8; this.v8 = v8;
            this.k9 = k9; this.v9 = v9;
        }

        public V get(@NotNull Class<?> key) {
            if (key == k0) return v0;
            if (key == k1) return v1;
            if (key == k2) return v2;
            if (key == k3) return v3;
            if (key == k4) return v4;
            if (key == k5) return v5;
            if (key == k6) return v6;
            if (key == k7) return v7;
            if (key == k8) return v8;
            if (key == k9) return v9;
            return null;
        }

        public int size() { return 10; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            if (key == k0) return new Map10<>(k0, value, k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8,    k9, v9);
            if (key == k1) return new Map10<>(k0, v0,    k1, value, k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8,    k9, v9);
            if (key == k2) return new Map10<>(k0, v0,    k1, v1,    k2, value, k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8,    k9, v9);
            if (key == k3) return new Map10<>(k0, v0,    k1, v1,    k2, v2,    k3, value, k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8,    k9, v9);
            if (key == k4) return new Map10<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, value, k5, v5,    k6, v6,    k7, v7,    k8, v8,    k9, v9);
            if (key == k5) return new Map10<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, value, k6, v6,    k7, v7,    k8, v8,    k9, v9);
            if (key == k6) return new Map10<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, value, k7, v7,    k8, v8,    k9, v9);
            if (key == k7) return new Map10<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, value, k8, v8,    k9, v9);
            if (key == k8) return new Map10<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, value, k9, v9);
            if (key == k9) return new Map10<>(k0, v0,    k1, v1,    k2, v2,    k3, v3,    k4, v4,    k5, v5,    k6, v6,    k7, v7,    k8, v8,    k9, value);
            Class<?>[] keys   = {k0, k1, k2, k3, k4, k5, k6, k7, k8, k9, key};
            Object[]   values = {v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, value};
            return new MapN<>(keys, values);
        }
    }

    private static final class MapN<V> extends Impl<V> {
        private Class<?>[] keys;
        private Object[]   values;

        MapN(Class<?>[] keys, Object[] values) {
            this.keys   = keys;
            this.values = values;
        }

        public V get(@NotNull Class<?> key) {
            final Class<?>[] k = this.keys;
            for (int i = 0; i < k.length; i++) {
                if (k[i] == key) return (V) this.values[i];
            }
            return null;
        }

        public int size() { return this.keys.length; }

        public @NotNull Impl<V> put(@NotNull Class<?> key, @NotNull V value) {
            final Class<?>[] k = this.keys;
            final int n = k.length;
            for (int i = 0; i < n; i++) {
                if (k[i] == key) {
                    this.values[i] = value;
                    return this;
                }
            }
            this.keys   = Arrays.copyOf(this.keys,   n + 1);
            this.values = Arrays.copyOf(this.values, n + 1);
            this.keys[n]   = key;
            this.values[n] = value;
            return this;
        }
    }
}