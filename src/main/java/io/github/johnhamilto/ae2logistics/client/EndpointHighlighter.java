package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * Client-side endpoint locator: clicking a roster row flashes a box around that
 * endpoint's cable for half a minute. Draws at AfterWeather (the stage AE2's own
 * overlays use) in two depth passes - full-strength where visible (LEQUAL), a dimmer
 * ghost where world geometry occludes it (GREATER) - each pass a translucent filled
 * box under a pulsing outline on window-scaled thick lines. The passes are baked
 * into dedicated pipelines and flushed through the shared buffer source; the box
 * must read from across a room.
 */
public final class EndpointHighlighter {

    private record Entry(BlockPos pos, long expireAt) {
    }

    private static final int DURATION_TICKS = 20 * 30;
    private static final float RED = 0.25f;
    private static final float GREEN = 0.75f;
    private static final float BLUE = 1.0f;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    // Filled faces: position_color quads, translucent, no cull, no depth write. The
    // occluded variants flip the depth test to GREATER, the scheme AE2 uses for its
    // behind-block lines; the ghost outline clones vanilla's lines pipeline the same way.
    private static final RenderPipeline FACE_PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(AE2Logistics.id("pipeline/endpoint_highlight_face"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();
    private static final RenderPipeline FACE_OCCLUDED_PIPELINE = FACE_PIPELINE.toBuilder()
            .withLocation(AE2Logistics.id("pipeline/endpoint_highlight_face_occluded"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
            .build();
    private static final RenderPipeline LINE_OCCLUDED_PIPELINE = RenderPipelines.LINES.toBuilder()
            .withLocation(AE2Logistics.id("pipeline/endpoint_highlight_line_occluded"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
            .build();

    private static final RenderType FACE = RenderType.create(
            "ae2logistics:endpoint_highlight_face",
            RenderSetup.builder(FACE_PIPELINE).createRenderSetup());
    private static final RenderType FACE_OCCLUDED = RenderType.create(
            "ae2logistics:endpoint_highlight_face_occluded",
            RenderSetup.builder(FACE_OCCLUDED_PIPELINE).createRenderSetup());
    private static final RenderType LINE_OCCLUDED = RenderType.create(
            "ae2logistics:endpoint_highlight_line_occluded",
            RenderSetup.builder(LINE_OCCLUDED_PIPELINE).createRenderSetup());

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

    public static void render(RenderLevelStageEvent.AfterWeather event) {
        if (ENTRIES.isEmpty()) {
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

        var camera = minecraft.gameRenderer.getMainCamera().position();
        var buffers = minecraft.renderBuffers().bufferSource();
        var poseStack = event.getPoseStack();

        // Where a wall hides the box: a ghost. Where it is in view: solid presence.
        // Filled faces first, so the outline draws over them.
        drawFaces(buffers.getBuffer(FACE_OCCLUDED), poseStack, camera, 0.10f);
        drawOutlines(buffers.getBuffer(LINE_OCCLUDED), poseStack, camera, 0.45f * pulse, lineWidth);
        drawFaces(buffers.getBuffer(FACE), poseStack, camera, 0.18f);
        drawOutlines(buffers.getBuffer(RenderTypes.lines()), poseStack, camera, pulse, lineWidth);

        buffers.endBatch(FACE_OCCLUDED);
        buffers.endBatch(LINE_OCCLUDED);
        buffers.endBatch(FACE);
        buffers.endBatch(RenderTypes.lines());
    }

    private static void drawFaces(VertexConsumer buffer, PoseStack poseStack, Vec3 camera,
            float alpha) {
        for (var entry : ENTRIES) {
            var pos = entry.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            filledBox(buffer, poseStack.last(), -0.05f, -0.05f, -0.05f, 1.05f, 1.05f, 1.05f, alpha);
            poseStack.popPose();
        }
    }

    private static void drawOutlines(VertexConsumer buffer, PoseStack poseStack, Vec3 camera,
            float alpha, float lineWidth) {
        for (var entry : ENTRIES) {
            var pos = entry.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            lineBox(buffer, poseStack.last(), -0.05f, -0.05f, -0.05f, 1.05f, 1.05f, 1.05f,
                    alpha, lineWidth);
            poseStack.popPose();
        }
    }

    private static void filledBox(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0,
            float z0, float x1, float y1, float z1, float alpha) {
        quad(buffer, pose, alpha, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(buffer, pose, alpha, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(buffer, pose, alpha, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(buffer, pose, alpha, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, alpha, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(buffer, pose, alpha, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float alpha,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        buffer.addVertex(pose, ax, ay, az).setColor(RED, GREEN, BLUE, alpha);
        buffer.addVertex(pose, bx, by, bz).setColor(RED, GREEN, BLUE, alpha);
        buffer.addVertex(pose, cx, cy, cz).setColor(RED, GREEN, BLUE, alpha);
        buffer.addVertex(pose, dx, dy, dz).setColor(RED, GREEN, BLUE, alpha);
    }

    private static void lineBox(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0,
            float z0, float x1, float y1, float z1, float alpha, float lineWidth) {
        // Four edges along each axis; the normal carries the line direction.
        line(buffer, pose, alpha, lineWidth, x0, y0, z0, x1, y0, z0, 1, 0, 0);
        line(buffer, pose, alpha, lineWidth, x0, y1, z0, x1, y1, z0, 1, 0, 0);
        line(buffer, pose, alpha, lineWidth, x0, y0, z1, x1, y0, z1, 1, 0, 0);
        line(buffer, pose, alpha, lineWidth, x0, y1, z1, x1, y1, z1, 1, 0, 0);
        line(buffer, pose, alpha, lineWidth, x0, y0, z0, x0, y1, z0, 0, 1, 0);
        line(buffer, pose, alpha, lineWidth, x1, y0, z0, x1, y1, z0, 0, 1, 0);
        line(buffer, pose, alpha, lineWidth, x0, y0, z1, x0, y1, z1, 0, 1, 0);
        line(buffer, pose, alpha, lineWidth, x1, y0, z1, x1, y1, z1, 0, 1, 0);
        line(buffer, pose, alpha, lineWidth, x0, y0, z0, x0, y0, z1, 0, 0, 1);
        line(buffer, pose, alpha, lineWidth, x1, y0, z0, x1, y0, z1, 0, 0, 1);
        line(buffer, pose, alpha, lineWidth, x0, y1, z0, x0, y1, z1, 0, 0, 1);
        line(buffer, pose, alpha, lineWidth, x1, y1, z0, x1, y1, z1, 0, 0, 1);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, float alpha,
            float lineWidth, float ax, float ay, float az, float bx, float by, float bz,
            float nx, float ny, float nz) {
        buffer.addVertex(pose, ax, ay, az).setColor(RED, GREEN, BLUE, alpha)
                .setNormal(nx, ny, nz).setLineWidth(lineWidth);
        buffer.addVertex(pose, bx, by, bz).setColor(RED, GREEN, BLUE, alpha)
                .setNormal(nx, ny, nz).setLineWidth(lineWidth);
    }
}
