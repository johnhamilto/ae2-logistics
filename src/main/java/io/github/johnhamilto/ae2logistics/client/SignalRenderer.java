package io.github.johnhamilto.ae2logistics.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import appeng.client.api.AEKeyRenderer;
import appeng.client.api.AEKeyRendering;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalKey;
import io.github.johnhamilto.ae2logistics.signal.SignalKeyType;

public final class SignalRenderer implements AEKeyRenderer<SignalKey, SignalRenderer.RenderState> {

    private static final Identifier ICON = AE2Logistics.id("textures/gui/signal.png");
    private static final Identifier FACE_SPRITE = Identifier.withDefaultNamespace("item/redstone");

    public static void initialize(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(
                () -> AEKeyRendering.register(SignalKeyType.TYPE, SignalKey.class, new SignalRenderer())));
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, int x, int y, SignalKey what) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ICON, x, y, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public Class<RenderState> stateClass() {
        return RenderState.class;
    }

    @Override
    public RenderState createState() {
        return new RenderState();
    }

    @Override
    public void extract(RenderState state, SignalKey what, @Nullable Level level, int seed) {
        state.sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(TextureAtlas.LOCATION_ITEMS).getSprite(FACE_SPRITE);
    }

    @Override
    public void submit(PoseStack poseStack, RenderState state, SubmitNodeCollector nodes, int lightCoords) {
        var sprite = state.sprite;

        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);

        // Unit quad; the monitor renderer scales the pose to the face.
        nodes.submitCustomGeometry(poseStack, RenderTypes.entityCutout(sprite.atlasLocation()),
                (pose, buffer) -> {
                    buffer.addVertex(pose, -0.5f, -0.5f, 0)
                            .setColor(0xFFFFFFFF)
                            .setUv(sprite.getU0(), sprite.getV1())
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(lightCoords)
                            .setNormal(0, 0, 1);
                    buffer.addVertex(pose, 0.5f, -0.5f, 0)
                            .setColor(0xFFFFFFFF)
                            .setUv(sprite.getU1(), sprite.getV1())
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(lightCoords)
                            .setNormal(0, 0, 1);
                    buffer.addVertex(pose, 0.5f, 0.5f, 0)
                            .setColor(0xFFFFFFFF)
                            .setUv(sprite.getU1(), sprite.getV0())
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(lightCoords)
                            .setNormal(0, 0, 1);
                    buffer.addVertex(pose, -0.5f, 0.5f, 0)
                            .setColor(0xFFFFFFFF)
                            .setUv(sprite.getU0(), sprite.getV0())
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(lightCoords)
                            .setNormal(0, 0, 1);
                });

        poseStack.popPose();
    }

    @Override
    public List<Component> getTooltip(SignalKey stack) {
        return List.of(stack.getDisplayName());
    }

    public static final class RenderState {
        TextureAtlasSprite sprite;
    }
}
