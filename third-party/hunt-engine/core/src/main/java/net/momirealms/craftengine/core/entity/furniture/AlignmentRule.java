package net.momirealms.craftengine.core.entity.furniture;

import net.momirealms.craftengine.core.util.Pair;

import java.util.function.Function;

public enum AlignmentRule {
    ANY(Function.identity()),
    CORNER(pair -> {
        double p1 = (double) Math.round(pair.left());
        double p2 = (double) Math.round(pair.right());
        return new Pair<>(p1, p2);
    }),
    CENTER(pair -> {
        double p1 = Math.floor(pair.left()) + 0.5;
        double p2 = Math.floor(pair.right()) + 0.5;
        return new Pair<>(p1, p2);
    }),
    HALF(pair -> {
        double p1 = Math.round(pair.left() * 2) / 2.0;
        double p2 = Math.round(pair.right() * 2) / 2.0;
        return new Pair<>(p1, p2);
    }),
    QUARTER(pair -> {
        double p1 = Math.round(pair.left() * 4) / 4.0;
        double p2 = Math.round(pair.right() * 4) / 4.0;
        return new Pair<>(p1, p2);
    }),
    CENTER_QUARTER(pair -> {
        double frac1 = pair.left() - Math.floor(pair.left());
        double frac2 = pair.right() - Math.floor(pair.right());
        double p1 = Math.floor(pair.left()) + (Math.abs(frac1 - 0.25) <= Math.abs(frac1 - 0.75) ? 0.25 : 0.75);
        double p2 = Math.floor(pair.right()) + (Math.abs(frac2 - 0.25) <= Math.abs(frac2 - 0.75) ? 0.25 : 0.75);
        return new Pair<>(p1, p2);
    });

    private final Function<Pair<Double, Double>, Pair<Double, Double>> function;

    AlignmentRule(Function<Pair<Double, Double>, Pair<Double, Double>> function) {
        this.function = function;
    }

    public Pair<Double, Double> apply(final Pair<Double, Double> pair) {
        return function.apply(pair);
    }
}
