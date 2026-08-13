package io.github.johnhamilto.ae2logistics.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalKey;
import io.github.johnhamilto.ae2logistics.signal.SignalKeyType;

public final class SignalRenderer implements AEKeyRenderHandler<SignalKey> {

    private static final Identifier ICON = AE2Logistics.id("textures/gui/signal.png");
    private static final Identifier FACE_SPRITE = Identifier.withDefaultNamespace("item/redstone");

    public static void initialize(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(
                () -> AEKeyRendering.register(SignalKeyType.TYPE, SignalKey.class, new SignalRenderer())));
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, SignalKey what) {
        guiGraphics.blit(ICON, x, y, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public Component getDisplayName(SignalKey what) {
        return what.getDisplayName();
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, SignalKey what, float scale,
            int combinedLight, Level level) {
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FACE_SPRITE);

        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);

        var buffer = buffers.getBuffer(RenderType.cutout());
        var half = scale / 2;
        var transform = poseStack.last().pose();

        buffer.addVertex(transform, -half, -half, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, half, -half, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, half, half, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, -half, half, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);

        poseStack.popPose();
    }
}
