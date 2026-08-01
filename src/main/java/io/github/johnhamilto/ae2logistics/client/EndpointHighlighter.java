package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side endpoint locator: clicking a roster row flashes a box around that
 * endpoint's cable for half a minute. Draws AFTER_LEVEL in two depth passes, the
 * scheme AE2's overlays use - full-strength where visible (LEQUAL), a dimmer ghost
 * where world geometry occludes it (GREATER) - each pass a translucent filled box
 * under a pulsing outline on window-scaled thick lines. Direct immediate-mode
 * draws with explicit state; the box must read from across a room.
 */
public final class EndpointHighlighter {

    private record Entry(BlockPos pos, long expireAt) {
    }

    private static final int DURATION_TICKS = 20 * 30;
    private static final float RED = 0.25f;
    private static final float GREEN = 0.75f;
    private static final float BLUE = 1.0f;
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || ENTRIES.isEmpty()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            ENTRIES.clear();
            return;
        }
        long now = level.getGameTime();
        ENTRIES.removeIf(entry -> now >= entry.expireAt);
        if (ENTRIES.isEmpty()) {
            return;
        }

        // Pulse the outline so the box reads as "look here", not as world geometry.
        float pulse = 0.55f + 0.45f * (float) Math.sin((now % 20) / 20.0 * Math.PI * 2);
        // Vanilla's block outline scales with the window (2.5 base); double it.
        float lineWidth = Math.max(5.0f, minecraft.getWindow().getWidth() * 5.0f / 1920.0f);

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(lineWidth);

        // Where the box is in view: solid presence. Where a wall hides it: a ghost.
        drawPass(event, GL11.GL_LEQUAL, 0.18f, pulse);
        drawPass(event, GL11.GL_GREATER, 0.10f, 0.45f * pulse);

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.lineWidth(1.0f);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void drawPass(RenderLevelStageEvent event, int depthFunc, float faceAlpha,
            float lineAlpha) {
        RenderSystem.depthFunc(depthFunc);
        var camera = event.getCamera().getPosition();
        var poseStack = new PoseStack();

        // Filled faces first, so the outline draws over them.
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var faces = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (var entry : ENTRIES) {
            var pos = entry.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            LevelRenderer.addChainedFilledBoxVertices(poseStack, faces,
                    -0.05, -0.05, -0.05, 1.05, 1.05, 1.05, RED, GREEN, BLUE, faceAlpha);
            poseStack.popPose();
        }
        BufferUploader.drawWithShader(faces.buildOrThrow());

        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        var lines = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (var entry : ENTRIES) {
            var pos = entry.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            LevelRenderer.renderLineBox(poseStack, lines, -0.05, -0.05, -0.05, 1.05, 1.05, 1.05,
                    RED, GREEN, BLUE, lineAlpha);
            poseStack.popPose();
        }
        BufferUploader.drawWithShader(lines.buildOrThrow());
    }
}
