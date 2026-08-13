package io.github.johnhamilto.ae2logistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.storage.IStorageMounts;
import appeng.parts.storagebus.StorageBusPart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

/**
 * A storage bus that applies our input cards (DESIGN F12): Conform Card and
 * Stack Limiter Card, plus the four stock cards. AE2's stock bus hardcodes which
 * cards it consults, so foreign cards sit inert there; this subclass inherits the
 * whole stock pipeline (target discovery, ticking monitors, invalidation) and
 * intercepts only the mount, wrapping whatever the stock code mounts in an
 * {@link InputCardGate}. Everything else - partition, access, priority, memory
 * cards - is the storage bus it extends.
 */
public class GatedStorageBusPart extends StorageBusPart {

    public GatedStorageBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        super.mountInventories((inventory, priority) -> mounts
                .mount(new InputCardGate(inventory, this), priority));
    }

    /** Our own menu type re-titles AE2's storage bus window. */
    @Override
    public net.minecraft.world.inventory.MenuType<?> getMenuType() {
        return AE2Logistics.GATED_STORAGE_BUS_MENU.get();
    }
}
