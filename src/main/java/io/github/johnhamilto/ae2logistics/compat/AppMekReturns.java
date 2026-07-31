package io.github.johnhamilto.ae2logistics.compat;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.storage.MEStorage;

import me.ramidzkh.mekae2.ae2.MekanismKey;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;

import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;
import io.github.johnhamilto.ae2logistics.parts.ProviderP2PTunnelPart;

/**
 * Applied Mekanistics bridge, classloaded only when the mod is present: Mekanism
 * machines eject through their OWN chemical capability, so provider return faces
 * expose a native chemical handler forwarding into the same return path every other
 * key type uses. Pushing INTO machines already works AE2-side (AppMek registers an
 * external storage strategy); this closes the return direction.
 */
public final class AppMekReturns {

    private AppMekReturns() {
    }

    public static void register(RegisterPartCapabilitiesEvent event) {
        event.register(Capabilities.CHEMICAL.block(),
                (part, context) -> adapter(part.exposedStorage()), ProviderP2PTunnelPart.class);
        event.register(Capabilities.CHEMICAL.block(),
                (part, context) -> adapter(part.exposedProviderReturnPath()), MeshEndpointPart.class);
    }

    /** Insert-only chemical view of a return path; tank indices are meaningless. */
    @Nullable
    private static IChemicalHandler adapter(@Nullable MEStorage returnPath) {
        if (returnPath == null) {
            return null;
        }
        return new IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                return 1;
            }

            @Override
            public ChemicalStack getChemicalInTank(int tank) {
                return ChemicalStack.EMPTY;
            }

            @Override
            public void setChemicalInTank(int tank, ChemicalStack stack) {
            }

            @Override
            public long getChemicalTankCapacity(int tank) {
                return 1_000_000_000L;
            }

            @Override
            public boolean isValid(int tank, ChemicalStack stack) {
                return true;
            }

            @Override
            public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
                var key = MekanismKey.of(stack);
                if (key == null) {
                    return stack;
                }
                long inserted = returnPath.insert(key, stack.getAmount(),
                        action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                        IActionSource.empty());
                return inserted >= stack.getAmount() ? ChemicalStack.EMPTY
                        : stack.copyWithAmount(stack.getAmount() - inserted);
            }

            @Override
            public ChemicalStack extractChemical(int tank, long amount, Action action) {
                return ChemicalStack.EMPTY;
            }
        };
    }
}
