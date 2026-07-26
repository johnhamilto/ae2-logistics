package dev.jackhamilton.ae2logistics;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AE2Logistics.MOD_ID)
public class AE2Logistics {

    public static final String MOD_ID = "ae2logistics";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public AE2Logistics(IEventBus modBus) {
        LOG.info("AE2 Logistics initialized");
    }
}
