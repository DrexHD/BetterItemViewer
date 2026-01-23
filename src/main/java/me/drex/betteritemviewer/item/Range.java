package me.drex.betteritemviewer.item;

public record Range(int min, int max) {
    public String format() {
        if (min == max) return String.valueOf(min);
        return min + "-" + max;
    }

    public Range merge(Range other) {
        return new Range(Math.min(min, other.min), Math.max(max, other.max));
    }
}
