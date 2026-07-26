package io.github.johnhamilto.ae2logistics.parts;

public enum LogicPartType {
    CONSTANT,
    THRESHOLD,
    HYSTERESIS,
    ARITHMETIC,
    BOOLEAN,
    REDSTONE_IO;

    public static LogicPartType byOrdinal(int ordinal) {
        var values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
