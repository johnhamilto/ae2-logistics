package io.github.johnhamilto.ae2logistics.menu;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

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

public record CyclePatternSpecPayload(BlockPos pos, int inputIndex, byte action) implements CustomPacketPayload {

    public static final byte ACTION_CYCLE = 0;
    public static final byte ACTION_ADD_ALTERNATIVE = 1;
    public static final byte ACTION_RESET = 2;
    public static final byte ACTION_TOGGLE_CATALYST = 3;

    public static final Type<CyclePatternSpecPayload> TYPE = new Type<>(AE2Logistics.id("cycle_pattern_spec"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CyclePatternSpecPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeVarInt(payload.inputIndex);
                buffer.writeByte(payload.action);
            },
            buffer -> new CyclePatternSpecPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readByte()));

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
        var current = specs.get(index);
        var next = switch (payload.action) {
            case ACTION_ADD_ALTERNATIVE -> {
                var carried = player.containerMenu.getCarried();
                var key = carriedKey(carried);
                yield key != null ? current.withAlternative(key) : current;
            }
            case ACTION_RESET -> AdaptiveInputSpec.EXACT;
            case ACTION_TOGGLE_CATALYST -> current.withCatalyst(!current.catalyst());
            default -> AdaptivePattern.nextSpec(input, current);
        };
        specs.set(index, next);

        var result = stack.getItem() == AE2Logistics.ADAPTIVE_PATTERN.get()
                ? stack.copy()
                : new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        result.set(AE2Logistics.ENCODED_ADAPTIVE_PATTERN.get(),
                new EncodedAdaptivePattern(inputs, encoded.sparseOutputs(), specs));
        workbench.inventory().setItem(0, result);
    }

    @Nullable
    private static GenericStack carriedKey(ItemStack carried) {
        if (carried.isEmpty()) {
            return null;
        }
        var unwrapped = GenericStack.fromItemStack(carried);
        if (unwrapped != null) {
            return new GenericStack(unwrapped.what(), 1);
        }
        var key = appeng.api.stacks.AEItemKey.of(carried);
        return key != null ? new GenericStack(key, 1) : null;
    }
}
