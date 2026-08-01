package io.github.johnhamilto.ae2logistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

import io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity;

/**
 * Draws the merged dashboard on a trace panel wall's face: dark screen, one
 * sparkline row per bound channel with name and current value, all scaled across
 * the whole group. Renders only on the master; the quads sit a hair off the face.
 */
public class TracePanelRenderer implements BlockEntityRenderer<TracePanelBlockEntity> {

    private static final float EPSILON = 0.004f;

    /** Multi-block walls extend past the master's block box; skip vanilla's culling. */
    @Override
    public boolean shouldRenderOffScreen(TracePanelBlockEntity panel) {
        return panel.isMaster() && (panel.groupWidth() > 1 || panel.groupHeight() > 1);
    }

    @Override
    public void render(TracePanelBlockEntity panel, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!panel.isMaster()) {
            return;
        }
        var channels = panel.boundChannels();
        float width = panel.groupWidth();
        float height = panel.groupHeight();

        poseStack.pushPose();
        // Sign-style orientation: local +x is viewer-right, +y up, face plane at z=1.
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - panel.facing().toYRot()));
        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(0, 0, 1 + EPSILON);

        var quads = buffers.getBuffer(RenderType.debugQuads());
        // Screen background across the whole group, inset by a bezel.
        float inset = 0.04f;
        fill(quads, poseStack, inset, inset, width - inset, height - inset, 0xFF10141B);

        if (!channels.isEmpty()) {
            float rowHeight = (height - 2 * inset) / channels.size();
            var font = Minecraft.getInstance().font;
            for (int i = 0; i < channels.size(); i++) {
                var channel = channels.get(i);
                float rowBottom = inset + (channels.size() - 1 - i) * rowHeight;
                var samples = panel.samples(channel);
                drawSparkline(quads, poseStack, inset + 0.02f, rowBottom + 0.02f,
                        width - 2 * (inset + 0.02f), rowHeight - 0.30f, samples);
                drawLabel(font, poseStack, buffers, packedLight, channel.toString(),
                        samples.length > 0 ? samples[samples.length - 1] : 0,
                        inset + 0.03f, rowBottom + rowHeight - 0.26f, width);
            }
        }
        poseStack.popPose();
    }

    private static void drawSparkline(VertexConsumer quads, PoseStack poseStack, float x, float y,
            float width, float height, long[] samples) {
        if (samples.length < 2 || height <= 0) {
            return;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long sample : samples) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        long range = Math.max(1, max - min);
        float step = width / samples.length;
        float thickness = Math.max(0.012f, height * 0.05f);
        for (int i = 0; i < samples.length; i++) {
            float h = (samples[i] - min) * (height - thickness) / range;
            float cx = x + i * step;
            fill(quads, poseStack, cx, y + h, cx + step, y + h + thickness, 0xFF5CE2FF);
        }
    }

    private static void drawLabel(Font font, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, String channel, long value, float x, float y, float panelWidth) {
        poseStack.pushPose();
        poseStack.translate(x, y + 0.2f, 0.002f);
        float scale = 0.9f / 40f * Math.min(2f, panelWidth);
        poseStack.scale(scale, -scale, scale);
        var text = channel + "  " + value;
        font.drawInBatch(text, 0, 0, 0x9BB2C4, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    private static void fill(VertexConsumer quads, PoseStack poseStack, float x0, float y0,
            float x1, float y1, int argb) {
        var pose = poseStack.last().pose();
        float a = (argb >>> 24 & 0xFF) / 255f;
        float r = (argb >>> 16 & 0xFF) / 255f;
        float g = (argb >>> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        quads.addVertex(pose, x0, y0, 0).setColor(r, g, b, a);
        quads.addVertex(pose, x1, y0, 0).setColor(r, g, b, a);
        quads.addVertex(pose, x1, y1, 0).setColor(r, g, b, a);
        quads.addVertex(pose, x0, y1, 0).setColor(r, g, b, a);
    }
}
