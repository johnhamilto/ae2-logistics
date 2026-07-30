package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.block.JobSchedulerBlockEntity;
import io.github.johnhamilto.ae2logistics.menu.ConfigureSchedulerPayload;
import io.github.johnhamilto.ae2logistics.menu.JobSchedulerMenu;

public class JobSchedulerScreen extends AEBaseScreen<JobSchedulerMenu> {

    private static final int HINT = 0x7b7b7b;
    private static final int IDLE = 0x606A72;
    private static final int RUN = 0x2E8B57;
    private static final int WARN = 0xA8760B;
    private static final int BAD = 0xB33A36;

    private static final String[] CLASS_NAMES = {"bulk", "maint"};

    private final AETextField[] floorBoxes = new AETextField[JobSchedulerBlockEntity.RULES];
    private final AETextField[] batchBoxes = new AETextField[JobSchedulerBlockEntity.RULES];
    private final AETextField[] guardBoxes = new AETextField[JobSchedulerBlockEntity.RULES];
    private final AETextField[] deadlineBoxes = new AETextField[JobSchedulerBlockEntity.RULES];
    private final byte[] classValues = new byte[JobSchedulerBlockEntity.RULES];
    private final boolean[] preemptValues = new boolean[JobSchedulerBlockEntity.RULES];

    public JobSchedulerScreen(JobSchedulerMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 231;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            int index = i;
            int y = topPos + JobSchedulerMenu.GHOST_Y + i * JobSchedulerMenu.ROW_STEP;
            classValues[i] = menu.classes[i];

            floorBoxes[i] = new AETextField(style, font, leftPos + 32, y + 2, 40, 13);
            floorBoxes[i].setBordered(false);
            floorBoxes[i].setMaxLength(12);
            floorBoxes[i].setValue(Long.toString(menu.floors[i]));
            addRenderableWidget(floorBoxes[i]);

            batchBoxes[i] = new AETextField(style, font, leftPos + 76, y + 2, 34, 13);
            batchBoxes[i].setBordered(false);
            batchBoxes[i].setMaxLength(9);
            batchBoxes[i].setValue(Long.toString(menu.batches[i]));
            addRenderableWidget(batchBoxes[i]);

            addRenderableWidget(new AE2Button(leftPos + 114, y + 1, 36, 15,
                    Component.literal(CLASS_NAMES[classValues[i]]), b -> {
                        classValues[index] = (byte) ((classValues[index] + 1) % 2);
                        b.setMessage(Component.literal(CLASS_NAMES[classValues[index]]));
                    }));

            guardBoxes[i] = new AETextField(style, font, leftPos + 154, y + 2, 36, 13);
            guardBoxes[i].setBordered(false);
            guardBoxes[i].setMaxLength(80);
            guardBoxes[i].setValue(menu.guards[i]);
            addRenderableWidget(guardBoxes[i]);

            deadlineBoxes[i] = new AETextField(style, font, leftPos + 76, y + 16, 34, 10);
            deadlineBoxes[i].setBordered(false);
            deadlineBoxes[i].setMaxLength(6);
            deadlineBoxes[i].setValue(Long.toString(menu.deadlines[i]));
            addRenderableWidget(deadlineBoxes[i]);

            preemptValues[i] = menu.preempts[i];
            addRenderableWidget(new AE2Button(leftPos + 114, y + 15, 36, 12,
                    preemptLabel(preemptValues[i]), b -> {
                        preemptValues[index] = !preemptValues[index];
                        b.setMessage(preemptLabel(preemptValues[index]));
                    }));
        }

    }

    private String snapshot() {
        var sb = new StringBuilder();
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            sb.append(floorBoxes[i].getValue()).append('\0')
                    .append(batchBoxes[i].getValue()).append('\0')
                    .append(classValues[i]).append('\0')
                    .append(guardBoxes[i].getValue()).append('\0')
                    .append(deadlineBoxes[i].getValue()).append('\0')
                    .append(preemptValues[i]).append('\0');
        }
        return sb.toString();
    }

    private final AutoApply autoApply = new AutoApply();

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var current = snapshot();
        if (autoApply.shouldSend(current,
                getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
            apply();
            autoApply.sent(current);
        }
    }

    @Override
    public void removed() {
        if (autoApply.dirty(snapshot())) {
            apply();
        }
        super.removed();
    }

    private void apply() {
        var floors = new long[JobSchedulerBlockEntity.RULES];
        var batches = new long[JobSchedulerBlockEntity.RULES];
        var guards = new String[JobSchedulerBlockEntity.RULES];
        var deadlines = new long[JobSchedulerBlockEntity.RULES];
        var preempts = new byte[JobSchedulerBlockEntity.RULES];
        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            floors[i] = parse(floorBoxes[i].getValue(), 0);
            batches[i] = parse(batchBoxes[i].getValue(), 16);
            guards[i] = guardBoxes[i].getValue();
            deadlines[i] = parse(deadlineBoxes[i].getValue(), 0);
            preempts[i] = (byte) (preemptValues[i] ? 1 : 0);
        }
        PacketDistributor.sendToServer(new ConfigureSchedulerPayload(
                menu.pos, floors, batches, classValues.clone(), guards, deadlines, preempts));
    }

    private static Component preemptLabel(boolean enabled) {
        return Component.literal(enabled ? "preempt" : "polite");
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
            case JobSchedulerBlockEntity.STATE_DEADLINE -> "late";
            case JobSchedulerBlockEntity.STATE_PREEMPTED -> "bumped";
            default -> "idle";
        };
    }

    private static int stateColor(int state) {
        return switch (state) {
            case JobSchedulerBlockEntity.STATE_MISSING, JobSchedulerBlockEntity.STATE_NO_CPU,
                    JobSchedulerBlockEntity.STATE_DEADLINE -> BAD;
            case JobSchedulerBlockEntity.STATE_RUNNING -> RUN;
            case JobSchedulerBlockEntity.STATE_GUARD_HOLD, JobSchedulerBlockEntity.STATE_RATE_WAIT,
                    JobSchedulerBlockEntity.STATE_PREEMPTED -> WARN;
            default -> IDLE;
        };
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "floor", 34, 12, HINT, false);
        guiGraphics.drawString(font, "batch", 78, 12, HINT, false);
        guiGraphics.drawString(font, "class", 116, 12, HINT, false);
        guiGraphics.drawString(font, "guard", 156, 12, HINT, false);

        for (int i = 0; i < JobSchedulerBlockEntity.RULES; i++) {
            int state = menu.ruleStateValue(i);
            guiGraphics.drawString(font, stateLabel(state), 10,
                    JobSchedulerMenu.GHOST_Y + i * JobSchedulerMenu.ROW_STEP + 19,
                    stateColor(state), false);
        }
        guiGraphics.drawString(font, "second line: deadline sec + preemption", 10, 124, HINT, false);
    }
}
