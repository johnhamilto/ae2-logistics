package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side endpoint locator: clicking a roster row flashes a pulsing line box
 * around that endpoint's cable for half a minute. Renders THROUGH walls on thick
 * lines - the point is finding the endpoint, not honoring occlusion.
 */
public final class EndpointHighlighter {

    /** Vanilla's line pipeline with depth testing off and a fixed 4px width. */
    private static final class LocatorRenderType extends RenderType {

        private static final RenderType THROUGH_WALLS = create("ae2logistics_locator",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, 1536, false, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(java.util.OptionalDouble.of(4.0)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private LocatorRenderType(String name, com.mojang.blaze3d.vertex.VertexFormat format,
                com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int bufferSize,
                boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }

    private record Entry(BlockPos pos, long expireAt) {
    }

    private static final int DURATION_TICKS = 20 * 30;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private EndpointHighlighter() {
    }

    public static void highlight(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ENTRIES.removeIf(entry -> entry.pos.equals(pos));
        ENTRIES.add(new Entry(pos.immutable(), level.getGameTime() + DURATION_TICKS));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ENTRIES.isEmpty()) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            ENTRIES.clear();
            return;
        }
        long now = level.getGameTime();
        ENTRIES.removeIf(entry -> now >= entry.expireAt);

        var camera = event.getCamera().getPosition();
        var poseStack = event.getPoseStack();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var lines = buffers.getBuffer(LocatorRenderType.THROUGH_WALLS);
        for (var entry : ENTRIES) {
            // Pulse so the box reads as "look here", not as world geometry.
            float alpha = 0.55f + 0.45f * (float) Math.sin((now % 20) / 20.0 * Math.PI * 2);
            var pos = entry.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            LevelRenderer.renderLineBox(poseStack, lines, -0.05, -0.05, -0.05, 1.05, 1.05, 1.05,
                    0.18f, 0.43f, 0.62f, alpha);
            poseStack.popPose();
        }
        buffers.endBatch(LocatorRenderType.THROUGH_WALLS);
    }
}
