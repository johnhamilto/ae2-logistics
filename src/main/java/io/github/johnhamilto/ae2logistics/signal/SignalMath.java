package io.github.johnhamilto.ae2logistics.signal;

/**
 * Signal arithmetic. Signals are non-negative longs; every operation saturates into
 * [0, Long.MAX_VALUE] instead of overflowing or going negative.
 */
public final class SignalMath {

    private SignalMath() {
    }

    public static long add(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    public static long subtract(long a, long b) {
        return Math.max(0, a - b);
    }

    public static long multiply(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        long r = a * b;
        if (r / b != a || r < 0) {
            return Long.MAX_VALUE;
        }
        return r;
    }

    public static long divide(long a, long b) {
        return b == 0 ? 0 : a / b;
    }

    public static long modulo(long a, long b) {
        return b == 0 ? 0 : a % b;
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
