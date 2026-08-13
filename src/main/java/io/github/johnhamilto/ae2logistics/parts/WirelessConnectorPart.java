package io.github.johnhamilto.ae2logistics.parts;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.wireless.WirelessLinkRegistry;

/**
 * A piece of smart cable whose run is the air between it and every color-compatible
 * connector in range: WirelessLinkRegistry lays real grid connections between the
 * connectors' main nodes, so pathfinding, channel assignment, and power flow behave
 * exactly as if cable were laid. The node stays flag-free ON PURPOSE - a plain node
 * rides the pathfinder's last strict BFS tier, so a wireless hop never claims a
 * subtree any wired route can reach, while still passing 8 channels (DESIGN F11.8).
 *
 * <p>No GUI by design. Color is the only configuration and the interactions are all
 * in-world: dye recolors, a fluix crystal resets to fluix (pairs with anything),
 * Wireless Boosters click in to extend range (AE2's own WAP range curve), an empty
 * hand pops one booster back out.
 */
public class WirelessConnectorPart extends AEBasePart {

    public static final int MAX_BOOSTERS = 8;

    private static final Map<AEColor, IPartModel> MODELS = buildModels();

    @PartModels
    public static final List<IPartModel> ALL_MODELS = List.copyOf(MODELS.values());

    private static Map<AEColor, IPartModel> buildModels() {
        var map = new EnumMap<AEColor, IPartModel>(AEColor.class);
        for (var color : AEColor.values()) {
            map.put(color, new PartModel(
                    AE2Logistics.id("part/wireless_connector_" + color.registryPrefix)));
        }
        return map;
    }

    private AEColor color = AEColor.TRANSPARENT;
    private int boosters;

    public WirelessConnectorPart(IPartItem<?> partItem) {
        super(partItem);
        // Passive like cable: no idle draw, and no flags - REQUIRE_CHANNEL stays off
        // (consumes nothing), DENSE_CAPACITY/PREFERRED stay off (last-resort pathing).
        getMainNode().setIdlePowerUsage(0);
    }

    public AEColor color() {
        return color;
    }

    public int boosters() {
        return boosters;
    }

    /** Range in blocks, on AE2's own wireless curve so boosters feel like WAP boosters. */
    public double rangeBlocks() {
        return AEConfig.instance().wireless_getMaxRange(boosters);
    }

    @org.jetbrains.annotations.Nullable
    public IGridNode node() {
        return getMainNode().getNode();
    }

    public BlockPos hostPos() {
        return getHost().getBlockEntity().getBlockPos();
    }

    public long stableKey() {
        var host = getHost().getBlockEntity();
        return host.getBlockPos().asLong() * 31 + (getSide() == null ? 6 : getSide().ordinal());
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (!isClientSide()) {
            WirelessLinkRegistry.register(this);
        }
    }

    @Override
    public void removeFromWorld() {
        if (!isClientSide()) {
            WirelessLinkRegistry.unregister(this);
        }
        super.removeFromWorld();
    }

    /** Server-side state change shared by the in-world interactions, tests, and plots. */
    public void applyWirelessConfig(AEColor newColor, int newBoosters) {
        color = newColor;
        boosters = Math.max(0, Math.min(MAX_BOOSTERS, newBoosters));
        getHost().markForSave();
        getHost().markForUpdate();
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        AEColor dyed = null;
        if (heldItem.getItem() instanceof DyeItem dye) {
            dyed = fromDye(dye.getDyeColor());
        } else if (heldItem.is(AEItems.FLUIX_CRYSTAL.asItem())) {
            dyed = AEColor.TRANSPARENT;
        }
        if (dyed != null) {
            if (!isClientSide() && dyed != color) {
                if (!player.isCreative()) {
                    heldItem.shrink(1);
                }
                applyWirelessConfig(dyed, boosters);
                overlay(player, "Wireless Connector: " + color.englishName);
            }
            return true;
        }
        if (heldItem.is(AEItems.WIRELESS_BOOSTER.asItem())) {
            if (!isClientSide()) {
                if (boosters >= MAX_BOOSTERS) {
                    overlay(player, "Boosters full (" + MAX_BOOSTERS + "/" + MAX_BOOSTERS + ")");
                } else {
                    if (!player.isCreative()) {
                        heldItem.shrink(1);
                    }
                    applyWirelessConfig(color, boosters + 1);
                    overlay(player, "Boosters " + boosters + "/" + MAX_BOOSTERS
                            + ", range " + (int) rangeBlocks());
                }
            }
            return true;
        }
        return false;
    }

    /** Empty hand pops one booster back out; the only removal path a GUI-less part needs. */
    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (boosters <= 0) {
            return false;
        }
        if (!isClientSide()) {
            player.getInventory().placeItemBackInInventory(AEItems.WIRELESS_BOOSTER.stack());
            applyWirelessConfig(color, boosters - 1);
            overlay(player, "Boosters " + boosters + "/" + MAX_BOOSTERS
                    + ", range " + (int) rangeBlocks());
        }
        return true;
    }

    private static void overlay(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    @org.jetbrains.annotations.Nullable
    private static AEColor fromDye(net.minecraft.world.item.DyeColor dye) {
        for (var candidate : AEColor.values()) {
            if (candidate.dye == dye) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        if (boosters > 0) {
            drops.add(AEItems.WIRELESS_BOOSTER.stack(boosters));
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        boosters = 0;
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putString("color", color.name());
        data.putInt("boosters", boosters);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        try {
            color = AEColor.valueOf(data.getString("color"));
        } catch (IllegalArgumentException e) {
            color = AEColor.TRANSPARENT;
        }
        boosters = Math.max(0, Math.min(MAX_BOOSTERS, data.getInt("boosters")));
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(color.ordinal());
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean redraw = super.readFromStream(data);
        var streamed = AEColor.values()[data.readByte()];
        if (streamed != color) {
            color = streamed;
            redraw = true;
        }
        return redraw;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        // Base plate against the face, antenna mast toward the cable; matches the model.
        bch.addBox(4, 4, 13, 12, 12, 16);
        bch.addBox(6, 6, 9, 10, 10, 13);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.get(color);
    }
}
