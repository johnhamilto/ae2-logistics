package io.github.johnhamilto.ae2logistics.config;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.entity.player.Player;

/**
 * Memory-card-style settings transfer for our own block entities that do not extend
 * AE2's base classes. The Config Terminal and Config Blueprint consult this alongside
 * AE2's AEBasePart/AEBaseBlockEntity surfaces.
 */
public interface TransferableSettings {

    DataComponentMap exportTransferSettings(@Nullable Player player);

    void importTransferSettings(DataComponentMap settings, @Nullable Player player);
}
