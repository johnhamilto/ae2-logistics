package io.github.johnhamilto.ae2logistics.client;

/**
 * Debounced auto-apply: settings send immediately after button changes, and after a
 * short quiet period while typing (or when the screen closes). Screens feed it a
 * canonical snapshot of their config every frame.
 */
public final class AutoApply {

    private static final int QUIET_TICKS = 15;

    private String lastSent;
    private String lastSeen;
    private long tick;
    private long lastChangeTick;

    /** True when the current snapshot should be sent; confirm with {@link #sent}. */
    public boolean shouldSend(String current, boolean typing) {
        tick++;
        if (lastSent == null) {
            lastSent = current;
            lastSeen = current;
            return false;
        }
        if (!current.equals(lastSeen)) {
            lastSeen = current;
            lastChangeTick = tick;
        }
        if (current.equals(lastSent)) {
            return false;
        }
        return !typing || tick - lastChangeTick >= QUIET_TICKS;
    }

    public void sent(String current) {
        lastSent = current;
        lastSeen = current;
    }

    /** Unsent edits pending (used to flush on close or before context switches). */
    public boolean dirty(String current) {
        return lastSent != null && !current.equals(lastSent);
    }

    /** New baseline after a context switch (e.g. selecting another list entry). */
    public void reset() {
        lastSent = null;
        lastSeen = null;
    }
}
