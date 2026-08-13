package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.util.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.EncodedAdaptivePattern;
import io.github.johnhamilto.ae2logistics.crafting.GuardedPattern;
import io.github.johnhamilto.ae2logistics.menu.CyclePatternSpecPayload;
import io.github.johnhamilto.ae2logistics.menu.PatternWorkbenchMenu;
import io.github.johnhamilto.ae2logistics.menu.WrapPatternPayload;

/**
 * First screen on AE2's own GUI framework: AE2 chrome (background, slots, labels)
 * comes from the screen style; we paint the adaptive-spec grid and guard controls.
 */
public class PatternWorkbenchScreen extends AEBaseScreen<PatternWorkbenchMenu> {

    private static final int GRID_X = 26;
    private static final int GRID_Y = 17;

    private AETextField guardChannelBox;
    private AETextField guardValueBox;
    private AE2Button guardOpButton;
    private AE2Button wrapButton;
    private int guardOp = 4;
    private ItemStack lastSeenPattern = ItemStack.EMPTY;

    public PatternWorkbenchScreen(PatternWorkbenchMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
    }

    @Override
    public void drawBG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // Generated chrome carries no slot art: give every active slot AE2's inset.
        for (var slot : menu.slots) {
            if (slot.isActive()) {
                Blitter.icon(Icon.SLOT_BACKGROUND)
                        .dest(offsetX + slot.x - 1, offsetY + slot.y - 1).blit(guiGraphics);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        guardChannelBox = new AETextField(style, font, leftPos + 40, topPos + 74, 88, 14);
        guardChannelBox.setBordered(false);
        guardChannelBox.setMaxLength(80);
        addRenderableWidget(guardChannelBox);

        guardOpButton = new CycleButton(leftPos + 132, topPos + 72, 24, 16,
                Component.literal(GuardedPattern.OPS[guardOp]), (b, dir) -> {
                    guardOp = Math.floorMod(guardOp + dir, GuardedPattern.OPS.length);
                    b.setMessage(Component.literal(GuardedPattern.OPS[guardOp]));
                });
        addRenderableWidget(guardOpButton);

        guardValueBox = new AETextField(style, font, leftPos + 40, topPos + 90, 60, 14);
        guardValueBox.setBordered(false);
        guardValueBox.setMaxLength(19);
        guardValueBox.setValue("0");
        addRenderableWidget(guardValueBox);

        wrapButton = new AE2Button(leftPos + 106, topPos + 88, 62, 16,
                Component.literal("Wrap"), b -> wrapOrUnwrap());
        addRenderableWidget(wrapButton);

        lastSeenPattern = ItemStack.EMPTY;
        refreshGuardWidgets();
    }

    private void wrapOrUnwrap() {
        var stack = menu.patternStack();
        if (stack.is(AE2Logistics.GUARDED_PATTERN.get())) {
            ClientPacketDistributor.sendToServer(new WrapPatternPayload(
                    menu.pos, WrapPatternPayload.ACTION_UNWRAP, "", 0, 0));
        } else if (!stack.isEmpty()) {
            long value;
            try {
                value = Long.parseLong(guardValueBox.getValue().trim());
            } catch (NumberFormatException e) {
                value = 0;
            }
            ClientPacketDistributor.sendToServer(new WrapPatternPayload(
                    menu.pos, WrapPatternPayload.ACTION_WRAP,
                    guardChannelBox.getValue(), guardOp, value));
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (!ItemStack.matches(lastSeenPattern, menu.patternStack())) {
            lastSeenPattern = menu.patternStack().copy();
            refreshGuardWidgets();
        }
    }

    private void refreshGuardWidgets() {
        var stack = menu.patternStack();
        boolean present = !stack.isEmpty();
        boolean guarded = stack.is(AE2Logistics.GUARDED_PATTERN.get());
        guardChannelBox.setVisible(present);
        guardValueBox.setVisible(present);
        guardOpButton.visible = present;
        wrapButton.visible = present;
        guardChannelBox.setEditable(!guarded);
        guardValueBox.setEditable(!guarded);
        guardOpButton.active = !guarded;
        wrapButton.setMessage(Component.literal(guarded ? "Unwrap" : "Wrap"));
        wrapButton.setTooltip(guarded
                ? Tooltip.create(Component.literal("Unwrap to edit the recipe"))
                : null);
        if (guarded) {
            var data = stack.get(AE2Logistics.GUARDED_PATTERN_DATA.get());
            if (data != null) {
                guardChannelBox.setValue(data.channel().toString());
                guardValueBox.setValue(Long.toString(data.value()));
                guardOp = Math.floorMod(data.op(), GuardedPattern.OPS.length);
                guardOpButton.setMessage(Component.literal(GuardedPattern.OPS[guardOp]));
            }
        }
    }

    @Nullable
    private EncodedAdaptivePattern decoded() {
        var stack = menu.patternStack();
        if (stack.isEmpty()) {
            return null;
        }
        var adaptive = stack.get(AE2Logistics.ENCODED_ADAPTIVE_PATTERN.get());
        if (adaptive != null) {
            return adaptive;
        }
        var processing = stack.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) {
            var specs = new ArrayList<AdaptiveInputSpec>(processing.sparseInputs().size());
            for (int i = 0; i < processing.sparseInputs().size(); i++) {
                specs.add(AdaptiveInputSpec.EXACT);
            }
            return new EncodedAdaptivePattern(processing.sparseInputs(), processing.sparseOutputs(), specs);
        }
        return null;
    }

    /** Gui-relative painting on top of the styled background. */
    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (menu.patternStack().isEmpty()) {
            guiGraphics.text(font, "Insert an encoded pattern", 8, 78, Palette.HINT, false);
            return;
        }
        var encoded = decoded();
        if (encoded == null) {
            // Crafting/smithing/stonecutting patterns match exactly; guards still apply.
            guiGraphics.text(font, "No adaptive matching", 9, 31, Palette.HINT, false);
            guiGraphics.text(font, "for this pattern type", 9, 43, Palette.HINT, false);
            guiGraphics.text(font, "Guard", 8, 76, Palette.LABEL, false);
            return;
        }
        guiGraphics.text(font, "Guard", 8, 76, Palette.LABEL, false);

        var inputs = encoded.sparseInputs();
        for (int i = 0; i < 9 && i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (input == null || !(input.what() instanceof AEItemKey itemKey)) {
                continue;
            }
            int x = GRID_X + (i % 3) * 18;
            int y = GRID_Y + (i / 3) * 18;
            guiGraphics.item(itemKey.toStack(), x, y);
            var spec = encoded.specFor(i);
            var badge = switch (spec.mode()) {
                case EXACT -> "";
                case FUZZY -> spec.fuzzyMode().map(m -> switch (m) {
                    case PERCENT_99 -> "99";
                    case PERCENT_75 -> "75";
                    case PERCENT_50 -> "50";
                    case PERCENT_25 -> "25";
                    default -> "F";
                }).orElse("F");
                case TAG -> "#";
                case ANY_OF -> "A" + (spec.alternatives().size() + 1);
            };
            if (!badge.isEmpty()) {
                var color = switch (spec.mode()) {
                    case FUZZY -> 0x5CE2FF;
                    case TAG -> 0xF5C542;
                    default -> 0xB08CFF;
                };
                guiGraphics.text(font, badge, x + 17 - font.width(badge), y + 8, color, true);
            }
            if (spec.catalyst()) {
                guiGraphics.text(font, "C", x - 1, y - 2, 0xFFD24D, true);
            }
        }

        var outputs = encoded.sparseOutputs();
        if (!outputs.isEmpty() && outputs.get(0) != null
                && outputs.get(0).what() instanceof AEItemKey outKey) {
            guiGraphics.item(outKey.toStack(), 116, 35);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        var encoded = decoded();
        if (encoded != null) {
            renderGridTooltip(guiGraphics, encoded, mouseX, mouseY);
        }
    }

    private void renderGridTooltip(GuiGraphicsExtractor guiGraphics, EncodedAdaptivePattern encoded, int mouseX, int mouseY) {
        int index = gridIndexAt(mouseX, mouseY);
        if (index < 0 || index >= encoded.sparseInputs().size()) {
            return;
        }
        GenericStack input = encoded.sparseInputs().get(index);
        if (input == null || !(input.what() instanceof AEItemKey itemKey)) {
            return;
        }
        var spec = encoded.specFor(index);
        List<Component> lines = new ArrayList<>();
        lines.add(itemKey.toStack().getHoverName());
        lines.add(switch (spec.mode()) {
            case EXACT -> Component.literal("Match: exact item").withStyle(net.minecraft.ChatFormatting.GRAY);
            case FUZZY -> Component.literal(spec.fuzzyMode()
                    .map(m -> "Match: same item, damage band " + m.getSerializedName())
                    .orElse("Match: any variant (ignores damage/components)"))
                    .withStyle(net.minecraft.ChatFormatting.AQUA);
            case TAG -> Component.literal("Match: tag #" + spec.tag().map(Identifier::toString).orElse("?"))
                    .withStyle(net.minecraft.ChatFormatting.GOLD);
            case ANY_OF -> Component.literal("Match: any of " + (spec.alternatives().size() + 1) + " items")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        });
        if (spec.mode() == AdaptiveInputSpec.Mode.ANY_OF) {
            for (var alternative : spec.alternatives()) {
                if (alternative.what() instanceof AEItemKey altKey) {
                    lines.add(Component.literal("  + ").append(altKey.toStack().getHoverName())
                            .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                }
            }
        }
        if (spec.catalyst()) {
            lines.add(Component.literal("Catalyst: required, credited back")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        lines.add(Component.literal("Click: cycle | +item: alternative").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        lines.add(Component.literal("Shift: reset | Ctrl: catalyst").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        guiGraphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private int gridIndexAt(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos - GRID_X;
        int localY = (int) mouseY - topPos - GRID_Y;
        if (localX < 0 || localY < 0 || localX >= 3 * 18 || localY >= 3 * 18) {
            return -1;
        }
        return (localY / 18) * 3 + (localX / 18);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = gridIndexAt(event.x(), event.y());
        if (index >= 0 && decoded() != null) {
            byte action;
            if (getMinecraft().hasShiftDown()) {
                action = CyclePatternSpecPayload.ACTION_RESET;
            } else if (getMinecraft().hasControlDown()) {
                action = CyclePatternSpecPayload.ACTION_TOGGLE_CATALYST;
            } else if (!menu.getCarried().isEmpty()) {
                action = CyclePatternSpecPayload.ACTION_ADD_ALTERNATIVE;
            } else {
                action = CyclePatternSpecPayload.ACTION_CYCLE;
            }
            ClientPacketDistributor.sendToServer(new CyclePatternSpecPayload(menu.pos, index, action));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
