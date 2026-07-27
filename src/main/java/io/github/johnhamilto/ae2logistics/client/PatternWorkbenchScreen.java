package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.EncodedAdaptivePattern;
import io.github.johnhamilto.ae2logistics.menu.CyclePatternSpecPayload;
import io.github.johnhamilto.ae2logistics.menu.PatternWorkbenchMenu;

public class PatternWorkbenchScreen extends AbstractContainerScreen<PatternWorkbenchMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/workbench_panel.png");

    private static final int GRID_X = 26;
    private static final int GRID_Y = 17;

    public PatternWorkbenchScreen(PatternWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        var encoded = decoded();
        if (encoded != null) {
            var inputs = encoded.sparseInputs();
            for (int i = 0; i < 9 && i < inputs.size(); i++) {
                var input = inputs.get(i);
                if (input == null || !(input.what() instanceof AEItemKey itemKey)) {
                    continue;
                }
                int x = leftPos + GRID_X + (i % 3) * 18;
                int y = topPos + GRID_Y + (i / 3) * 18;
                guiGraphics.renderItem(itemKey.toStack(), x, y);
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
                    guiGraphics.drawString(font, badge, x + 17 - font.width(badge), y + 8, color, true);
                }
                if (spec.catalyst()) {
                    guiGraphics.drawString(font, "C", x - 1, y - 2, 0xFFD24D, true);
                }
            }

            var outputs = encoded.sparseOutputs();
            if (!outputs.isEmpty() && outputs.get(0) != null
                    && outputs.get(0).what() instanceof AEItemKey outKey) {
                guiGraphics.renderItem(outKey.toStack(), leftPos + 116, topPos + 35);
            }

            renderGridTooltip(guiGraphics, encoded, mouseX, mouseY);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderGridTooltip(GuiGraphics guiGraphics, EncodedAdaptivePattern encoded, int mouseX, int mouseY) {
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
            case TAG -> Component.literal("Match: tag #" + spec.tag().map(ResourceLocation::toString).orElse("?"))
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
        guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0 && decoded() != null) {
            byte action;
            if (hasShiftDown()) {
                action = CyclePatternSpecPayload.ACTION_RESET;
            } else if (hasControlDown()) {
                action = CyclePatternSpecPayload.ACTION_TOGGLE_CATALYST;
            } else if (!menu.getCarried().isEmpty()) {
                action = CyclePatternSpecPayload.ACTION_ADD_ALTERNATIVE;
            } else {
                action = CyclePatternSpecPayload.ACTION_CYCLE;
            }
            PacketDistributor.sendToServer(new CyclePatternSpecPayload(menu.pos, index, action));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }
}
