package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
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
 * Client-side endpoint locator: clicking a roster row flashes a pulsing line box
 * around that endpoint's cable for half a minute. Draws AFTER_LEVEL in two passes,
 * the same scheme AE2's overlays use - bright lines where the box is visible
 * (LEQUAL) and a dimmer ghost where world geometry occludes it (GREATER) - as
 * direct immediate-mode draws with explicit state, on thick shader-expanded lines.
 */
public final class EndpointHighlighter {

    private record Entry(BlockPos pos, long expireAt) {
    }

    private static final int DURATION_TICKS = 20 * 30;
    private static final float LINE_WIDTH = 4.0f;
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
        var level = Minecraft.getInstance().level;
        if (level == null) {
            ENTRIES.clear();
            return;
        }
        long now = level.getGameTime();
        ENTRIES.removeIf(entry -> now >= entry.expireAt);
        if (ENTRIES.isEmpty()) {
            return;
        }

        // Pulse so the box reads as "look here", not as world geometry.
        float pulse = 0.55f + 0.45f * (float) Math.sin((now % 20) / 20.0 * Math.PI * 2);

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(LINE_WIDTH);

        // Where the box is in view: bright. Where a wall hides it: a dim ghost.
        drawBoxes(event, GL11.GL_LEQUAL, pulse);
        drawBoxes(event, GL11.GL_GREATER, pulse * 0.35f);

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.lineWidth(1.0f);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void drawBoxes(RenderLevelStageEvent event, int depthFunc, float alpha) {
        RenderSystem.depthFunc(depthFunc);
        var camera = event.getCamera().getPosition();
        var poseStack = new PoseStack();
        var builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (var entry : ENTRIES) {
            var pos = entry.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            LevelRenderer.renderLineBox(poseStack, builder, -0.05, -0.05, -0.05, 1.05, 1.05, 1.05,
                    0.18f, 0.43f, 0.62f, alpha);
            poseStack.popPose();
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
    }
}
