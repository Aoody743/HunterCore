package net.momirealms.craftengine.core.world.chunk;

import java.util.Arrays;
import java.util.function.IntConsumer;

public record EmptyPaletteStorage(int size) implements PaletteStorage {
    public static final long[] EMPTY_DATA = new long[0];

    @Override
    public int swap(int index, int value) {
        return 0;
    }

    @Override
    public void set(int index, int value) {
    }

    @Override
    public int getAndSet(int index, int value) {
        return 0;
    }

    @Override
    public int get(int index) {
        return 0;
    }

    @Override
    public long[] getData() {
        return EMPTY_DATA;
    }

    @Override
    public int getElementBits() {
        return 0;
    }

    @Override
    public void forEach(IntConsumer action) {
        for (int i = 0; i < this.size; ++i) {
            action.accept(0);
        }
    }

    @Override
    public void writePaletteIndices(int[] out) {
        Arrays.fill(out, 0, this.size, 0);
    }

    @Override
    public PaletteStorage copy() {
        return this;
    }
}
