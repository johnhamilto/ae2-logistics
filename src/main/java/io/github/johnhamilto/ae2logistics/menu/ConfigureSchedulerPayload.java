package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;

/** Applies floor/batch/class/guard for all rules; ghost targets travel via clicked(). */
public record ConfigureSchedulerPayload(BlockPos pos, long[] floors, long[] batches, byte[] classes,
        String[] guards) implements CustomPacketPayload {

    public static final Type<ConfigureSchedulerPayload> TYPE = new Type<>(AE2Logistics.id("configure_scheduler"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureSchedulerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
                            buffer.writeLong(payload.floors[i]);
                            buffer.writeLong(payload.batches[i]);
                            buffer.writeByte(payload.classes[i]);
                            buffer.writeUtf(payload.guards[i]);
                        }
                    },
                    buffer -> {
                        var pos = buffer.readBlockPos();
                        var floors = new long[JobSchedulerBlockEntity.RULES];
                        var batches = new long[JobSchedulerBlockEntity.RULES];
                        var classes = new byte[JobSchedulerBlockEntity.RULES];
                        var guards = new String[JobSchedulerBlockEntity.RULES];
                        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
                            floors[i] = buffer.readLong();
                            batches[i] = buffer.readLong();
                            classes[i] = buffer.readByte();
                            guards[i] = buffer.readUtf();
                        }
                        return new ConfigureSchedulerPayload(pos, floors, batches, classes, guards);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigureSchedulerPayload payload, IPayloadContext context) {
        var player = context.player();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || !(player.level().getBlockEntity(payload.pos) instanceof JobSchedulerBlockEntity scheduler)) {
            return;
        }
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            var guardText = payload.guards[i].trim();
            scheduler.applyRuleConfig(i, payload.floors[i], payload.batches[i], payload.classes[i],
                    guardText.isEmpty() ? null : ResourceLocation.tryParse(guardText));
        }
    }
}
