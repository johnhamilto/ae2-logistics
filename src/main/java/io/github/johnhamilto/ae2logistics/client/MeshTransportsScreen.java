package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.network.chat.Component;

import appeng.client.gui.AESubScreen;
import appeng.util.Icon;
import appeng.client.gui.widgets.AECheckbox;
import appeng.client.gui.widgets.IconButton;

import io.github.johnhamilto.ae2logistics.menu.MeshEndpointMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;

/**
 * The universal endpoint's transport toggles, behind the sidebar cog - styled after
 * AE2's terminal key-type selection sub-screen. Every change applies immediately
 * through the parent screen, which stays alive underneath and is returned to.
 */
public class MeshTransportsScreen extends AESubScreen<MeshEndpointMenu, MeshEndpointScreen> {

    private static final int[] TYPES = {MeshRegistry.TYPE_REDSTONE, MeshRegistry.TYPE_ITEM,
            MeshRegistry.TYPE_FLUID, MeshRegistry.TYPE_ENERGY, MeshRegistry.TYPE_SIGNAL,
            MeshRegistry.TYPE_ME, MeshRegistry.TYPE_PROVIDER};
    private static final String[] TYPE_NAMES = {"Redstone", "Items", "Fluids", "Energy", "Signals",
            "ME Link", "Provider"};

    private final AECheckbox[] switches = new AECheckbox[TYPES.length];

    public MeshTransportsScreen(MeshEndpointScreen parent) {
        // Size comes from the style doc's generatedBackground - the base screen
        // adopts its width/height and draws the panel from them.
        super(parent, "/screens/ae2logistics_mesh_transports.json");
    }

    @Override
    protected void init() {
        super.init();
        setTextContent("dialog_title", Component.literal("Configure Transports"));

        var back = new IconButton(b -> returnToParent()) {
            @Override
            protected Icon getIcon() {
                return Icon.BACK;
            }
        };
        back.setMessage(Component.literal("Back"));
        back.setPosition(leftPos + 176, topPos - 5);
        addRenderableWidget(back);

        for (int i = 0; i < TYPES.length; i++) {
            var box = new AECheckbox(leftPos + 10, topPos + 24 + i * 20, 160, AECheckbox.SIZE,
                    style, Component.literal(TYPE_NAMES[i]));
            box.setSelected((getParent().maskValue & TYPES[i]) != 0);
            box.setChangeListener(this::pushMask);
            switches[i] = box;
            addRenderableWidget(box);
        }
    }

    private void pushMask() {
        int mask = 0;
        for (int i = 0; i < TYPES.length; i++) {
            if (switches[i].isSelected()) {
                mask |= TYPES[i];
            }
        }
        getParent().setMaskValue(mask);
    }
}
