package io.github.johnhamilto.ae2logistics.integration;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.client.JobSchedulerScreen;
import io.github.johnhamilto.ae2logistics.client.LogicCoreScreen;
import io.github.johnhamilto.ae2logistics.client.LogicPartScreen;
import io.github.johnhamilto.ae2logistics.client.MeshEndpointScreen;
import io.github.johnhamilto.ae2logistics.menu.GhostSlotPayload;

/**
 * JEI ghost-ingredient drag onto our filter/ghost slots. Fluids arrive as their
 * bucket, which the menus' existing carried-stack conversion already understands.
 */
@JeiPlugin
public class JeiIntegration implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return AE2Logistics.id("jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(MeshEndpointScreen.class, new GhostHandler<>());
        registration.addGhostIngredientHandler(LogicPartScreen.class, new GhostHandler<>());
        registration.addGhostIngredientHandler(LogicCoreScreen.class, new GhostHandler<>());
        registration.addGhostIngredientHandler(JobSchedulerScreen.class, new GhostHandler<>());
    }

    private static class GhostHandler<T extends AEBaseScreen<?>> implements IGhostIngredientHandler<T> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient,
                boolean doStart) {
            var stack = ingredientStack(ingredient);
            if (stack.isEmpty()
                    || !(screen.getMenu() instanceof GhostSlotPayload.GhostSlotTarget ghostTarget)) {
                return List.of();
            }
            var menu = screen.getMenu();
            var targets = new ArrayList<Target<I>>();
            for (var slot : menu.slots) {
                int index = slot.index;
                if (!ghostTarget.acceptsGhost(index)) {
                    continue;
                }
                var area = new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16);
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return area;
                    }

                    @Override
                    public void accept(I dropped) {
                        // Optimistic client-side update plus the authoritative server set.
                        ghostTarget.setGhost(index, stack);
                        PacketDistributor.sendToServer(
                                new GhostSlotPayload(menu.containerId, index, stack));
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    private static ItemStack ingredientStack(ITypedIngredient<?> ingredient) {
        var item = ingredient.getItemStack();
        if (item.isPresent()) {
            return item.get();
        }
        if (ingredient.getIngredient() instanceof FluidStack fluid
                && fluid.getFluid().getBucket() != Items.AIR) {
            return new ItemStack(fluid.getFluid().getBucket());
        }
        return ItemStack.EMPTY;
    }
}
