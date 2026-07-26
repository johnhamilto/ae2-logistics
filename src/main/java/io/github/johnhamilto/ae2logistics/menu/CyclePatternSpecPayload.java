package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.PatternWorkbenchBlockEntity;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.crafting.EncodedAdaptivePattern;

public record CyclePatternSpecPayload(BlockPos pos, int inputIndex) implements CustomPacketPayload {

    public static final Type<CyclePatternSpecPayload> TYPE = new Type<>(AE2Logistics.id("cycle_pattern_spec"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CyclePatternSpecPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeVarInt(payload.inputIndex);
            },
            buffer -> new CyclePatternSpecPayload(buffer.readBlockPos(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CyclePatternSpecPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if (payload.pos.distToCenterSqr(player.position()) > 100
                || !(level.getBlockEntity(payload.pos) instanceof PatternWorkbenchBlockEntity workbench)) {
            return;
        }

        var stack = workbench.inventory().getItem(0);
        if (stack.isEmpty()) {
            return;
        }

        EncodedAdaptivePattern encoded = stack.get(AE2Logistics.ENCODED_ADAPTIVE_PATTERN.get());
        if (encoded == null) {
            var processing = stack.get(AEComponents.ENCODED_PROCESSING_PATTERN);
            if (processing == null) {
                return;
            }
            var exactSpecs = new ArrayList<AdaptiveInputSpec>(processing.sparseInputs().size());
            for (int i = 0; i < processing.sparseInputs().size(); i++) {
                exactSpecs.add(AdaptiveInputSpec.EXACT);
            }
            encoded = new EncodedAdaptivePattern(processing.sparseInputs(), processing.sparseOutputs(), exactSpecs);
        }

        var inputs = encoded.sparseInputs();
        int index = payload.inputIndex;
        if (index < 0 || index >= inputs.size()) {
            return;
        }
        GenericStack input = inputs.get(index);
        if (input == null) {
            return;
        }

        var specs = new ArrayList<AdaptiveInputSpec>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            specs.add(encoded.specFor(i));
        }
        specs.set(index, AdaptivePattern.nextSpec(input, specs.get(index)));

        var result = stack.getItem() == AE2Logistics.ADAPTIVE_PATTERN.get()
                ? stack.copy()
                : new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        result.set(AE2Logistics.ENCODED_ADAPTIVE_PATTERN.get(),
                new EncodedAdaptivePattern(inputs, encoded.sparseOutputs(), specs));
        workbench.inventory().setItem(0, result);
    }
}
