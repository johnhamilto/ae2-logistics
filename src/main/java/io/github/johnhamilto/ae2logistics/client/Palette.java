package io.github.johnhamilto.ae2logistics.client;

/** The mod's shared GUI colors; the chrome values are sampled from AE2's own screens. */
public final class Palette {

    public static final int LABEL = 0x404040;
    public static final int HINT = 0x7b7b7b;
    public static final int VALUE = 0x2E6E9E;
    public static final int OK = 0x2E8B57;
    public static final int WAIT = 0xA8760B;
    public static final int ALERT = 0xB33A36;
    /** Body text on list rows; darker than HINT, quieter than LABEL. */
    public static final int ROW = 0x505A62;
    /** Output-direction accent (P2P outputs, mesh Output roles). */
    public static final int OUT = 0xA85E1F;
    /** Things on another grid or in another dimension. */
    public static final int REMOTE = 0x7C4FB3;

    /** AE2's darker inset - the "here be info" accent its terminals use for slots. */
    public static final int WELL = 0x2E10102C;
    // Sampled from AE2's scrollbar chrome: off-white frame, grey-blue channel.
    public static final int GUTTER_FRAME = 0xFFF2F2F2;
    public static final int GUTTER_FILL = 0xFF9A9FB4;

    private Palette() {
    }
}
