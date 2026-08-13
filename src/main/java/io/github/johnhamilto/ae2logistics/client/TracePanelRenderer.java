package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity;

/**
 * Draws the merged dashboard on a trace panel wall's face: dark screen, one
 * sparkline row per bound channel with name and current value, all scaled across
 * the whole group. Renders only on the master; the quads sit a hair off the face.
 */
public class TracePanelRenderer
        implements BlockEntityRenderer<TracePanelBlockEntity, TracePanelRenderer.TracePanelRenderState> {

    private static final float EPSILON = 0.004f;

    private record Row(FormattedCharSequence label, long[] samples) {
    }

    public static class TracePanelRenderState extends BlockEntityRenderState {
        boolean master;
        float width;
        float height;
        float faceYRot;
        List<Row> rows = List.of();
    }

    /** Multi-block walls extend past the master's block box; skip section-level culling. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /** Groups cap at 4x4; the default unit box would cull walls whose master is off screen. */
    @Override
    public AABB getRenderBoundingBox(TracePanelBlockEntity panel) {
        return new AABB(panel.getBlockPos()).inflate(4);
    }

    @Override
    public TracePanelRenderState createRenderState() {
        return new TracePanelRenderState();
    }

    @Override
    public void extractRenderState(TracePanelBlockEntity panel, TracePanelRenderState state,
            float partialTick, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(panel, state, partialTick, cameraPos,
                crumblingOverlay);
        state.master = panel.isMaster();
        state.rows = List.of();
        if (!state.master) {
            return;
        }
        state.width = panel.groupWidth();
        state.height = panel.groupHeight();
        state.faceYRot = 180 - panel.facing().toYRot();
        var rows = new ArrayList<Row>();
        for (var channel : panel.boundChannels()) {
            var samples = panel.samples(channel);
            long value = samples.length > 0 ? samples[samples.length - 1] : 0;
            rows.add(new Row(Component.literal(channel + "  " + value).getVisualOrderText(),
                    samples));
        }
        state.rows = rows;
    }

    @Override
    public void submit(TracePanelRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodes, CameraRenderState camera) {
        if (!state.master) {
            return;
        }
        float width = state.width;
        float height = state.height;
        var rows = state.rows;

        poseStack.pushPose();
        // Sign-style orientation: local +x is viewer-right, +y up, face plane at z=1.
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.faceYRot));
        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(0, 0, 1 + EPSILON);

        // Screen background across the whole group, inset by a bezel.
        float inset = 0.04f;
        nodes.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, quads) -> {
            fill(quads, pose, inset, inset, width - inset, height - inset, 0xFF10141B);
            if (!rows.isEmpty()) {
                float rowHeight = (height - 2 * inset) / rows.size();
                for (int i = 0; i < rows.size(); i++) {
                    float rowBottom = inset + (rows.size() - 1 - i) * rowHeight;
                    drawSparkline(quads, pose, inset + 0.02f, rowBottom + 0.02f,
                            width - 2 * (inset + 0.02f), rowHeight - 0.30f,
                            rows.get(i).samples());
                }
            }
        });

        if (!rows.isEmpty()) {
            float rowHeight = (height - 2 * inset) / rows.size();
            for (int i = 0; i < rows.size(); i++) {
                float rowBottom = inset + (rows.size() - 1 - i) * rowHeight;
                drawLabel(poseStack, nodes, state.lightCoords, rows.get(i).label(),
                        inset + 0.03f, rowBottom + rowHeight - 0.26f, width);
            }
        }
        poseStack.popPose();
    }

    private static void drawSparkline(VertexConsumer quads, PoseStack.Pose pose, float x, float y,
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
            fill(quads, pose, cx, y + h, cx + step, y + h + thickness, 0xFF5CE2FF);
        }
    }

    private static void drawLabel(PoseStack poseStack, SubmitNodeCollector nodes, int lightCoords,
            FormattedCharSequence label, float x, float y, float panelWidth) {
        poseStack.pushPose();
        poseStack.translate(x, y + 0.2f, 0.002f);
        float scale = 0.9f / 40f * Math.min(2f, panelWidth);
        poseStack.scale(scale, -scale, scale);
        nodes.submitText(poseStack, 0, 0, label, false, Font.DisplayMode.NORMAL, lightCoords,
                0xFF9BB2C4, 0, 0);
        poseStack.popPose();
    }

    private static void fill(VertexConsumer quads, PoseStack.Pose pose, float x0, float y0,
            float x1, float y1, int argb) {
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
