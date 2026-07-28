package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;
import io.github.johnhamilto.ae2logistics.menu.ConfigureSchedulerPayload;
import io.github.johnhamilto.ae2logistics.menu.JobSchedulerMenu;

public class JobSchedulerScreen extends AbstractContainerScreen<JobSchedulerMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/mesh_panel.png");

    private static final String[] CLASS_NAMES = {"bulk", "maint"};

    private final EditBox[] floorBoxes = new EditBox[JobSchedulerBlockEntity.RULES];
    private final EditBox[] batchBoxes = new EditBox[JobSchedulerBlockEntity.RULES];
    private final EditBox[] guardBoxes = new EditBox[JobSchedulerBlockEntity.RULES];
    private final byte[] classValues = new byte[JobSchedulerBlockEntity.RULES];

    public JobSchedulerScreen(JobSchedulerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            int index = i;
            int y = topPos + JobSchedulerMenu.GHOST_Y + i * JobSchedulerMenu.ROW_STEP;
            classValues[i] = menu.classes[i];

            floorBoxes[i] = new EditBox(font, leftPos + 32, y + 2, 40, 13, Component.empty());
            floorBoxes[i].setMaxLength(12);
            floorBoxes[i].setValue(Long.toString(menu.floors[i]));
            addRenderableWidget(floorBoxes[i]);

            batchBoxes[i] = new EditBox(font, leftPos + 76, y + 2, 34, 13, Component.empty());
            batchBoxes[i].setMaxLength(9);
            batchBoxes[i].setValue(Long.toString(menu.batches[i]));
            addRenderableWidget(batchBoxes[i]);

            addRenderableWidget(Button.builder(Component.literal(CLASS_NAMES[classValues[i]]), b -> {
                classValues[index] = (byte) ((classValues[index] + 1) % 2);
                b.setMessage(Component.literal(CLASS_NAMES[classValues[index]]));
            }).bounds(leftPos + 114, y + 1, 36, 15).build());

            guardBoxes[i] = new EditBox(font, leftPos + 154, y + 2, 36, 13, Component.empty());
            guardBoxes[i].setMaxLength(80);
            guardBoxes[i].setValue(menu.guards[i]);
            addRenderableWidget(guardBoxes[i]);
        }

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 140, topPos + 122, 50, 15).build());
    }

    private void apply() {
        var floors = new long[JobSchedulerBlockEntity.RULES];
        var batches = new long[JobSchedulerBlockEntity.RULES];
        var guards = new String[JobSchedulerBlockEntity.RULES];
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            floors[i] = parse(floorBoxes[i].getValue(), 0);
            batches[i] = parse(batchBoxes[i].getValue(), 16);
            guards[i] = guardBoxes[i].getValue();
        }
        PacketDistributor.sendToServer(new ConfigureSchedulerPayload(
                menu.pos, floors, batches, classValues.clone(), guards));
    }

    private static long parse(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stateLabel(int state) {
        return switch (state) {
            case JobSchedulerBlockEntity.STATE_GUARD_HOLD -> "hold";
            case JobSchedulerBlockEntity.STATE_PLANNING -> "plan";
            case JobSchedulerBlockEntity.STATE_MISSING -> "missing";
            case JobSchedulerBlockEntity.STATE_NO_CPU -> "no CPU";
            case JobSchedulerBlockEntity.STATE_RUNNING -> "run";
            case JobSchedulerBlockEntity.STATE_RATE_WAIT -> "wait";
            default -> "idle";
        };
    }

    private static int stateColor(int state) {
        return switch (state) {
            case JobSchedulerBlockEntity.STATE_MISSING, JobSchedulerBlockEntity.STATE_NO_CPU -> 0xE0524E;
            case JobSchedulerBlockEntity.STATE_RUNNING -> 0x6FDB6F;
            case JobSchedulerBlockEntity.STATE_GUARD_HOLD, JobSchedulerBlockEntity.STATE_RATE_WAIT -> 0xF5C542;
            default -> 0x8A9AA8;
        };
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            slotFrame(guiGraphics, JobSchedulerMenu.GHOST_X,
                    JobSchedulerMenu.GHOST_Y + i * JobSchedulerMenu.ROW_STEP);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(guiGraphics, JobSchedulerMenu.INV_X + col * 18,
                        JobSchedulerMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(guiGraphics, JobSchedulerMenu.INV_X + col * 18, JobSchedulerMenu.HOTBAR_Y);
        }
    }

    private void slotFrame(GuiGraphics guiGraphics, int slotX, int slotY) {
        int x = leftPos + slotX - 1;
        int y = topPos + slotY - 1;
        guiGraphics.fill(x, y, x + 18, y + 18, 0xFF1A1F27);
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF2C333F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        guiGraphics.drawString(font, "floor", 34, 12, 0x5A6B7C, false);
        guiGraphics.drawString(font, "batch", 78, 12, 0x5A6B7C, false);
        guiGraphics.drawString(font, "class", 116, 12, 0x5A6B7C, false);
        guiGraphics.drawString(font, "guard", 156, 12, 0x5A6B7C, false);

        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            int state = menu.ruleStateValue(i);
            guiGraphics.drawString(font, stateLabel(state), 10,
                    JobSchedulerMenu.GHOST_Y + i * JobSchedulerMenu.ROW_STEP + 19,
                    stateColor(state), false);
        }
        guiGraphics.drawString(font, "keep >= floor, craft in batches", 10, 126, 0x5A6B7C, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
