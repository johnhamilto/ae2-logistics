package io.github.johnhamilto.ae2logistics.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;

@GameTestHolder(AE2Logistics.MOD_ID)
@PrefixGameTestTemplate(false)
public class PatternGameTests {

    @GameTest(template = "empty5")
    public void tagSpecExpandsAndMatches(GameTestHelper helper) {
        var level = helper.getLevel();
        var stack = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        var input = new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4);
        var output = new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1);
        AdaptivePattern.encode(stack, List.of(input), List.of(output),
                List.of(AdaptiveInputSpec.ofTag(ResourceLocation.parse("minecraft:planks"))));

        helper.assertTrue(PatternDetailsHelper.isEncodedPattern(stack), "stack must count as encoded pattern");

        var details = PatternDetailsHelper.decodePattern(stack, level);
        helper.assertTrue(details instanceof AdaptivePattern, "must decode as AdaptivePattern");

        var patternInput = details.getInputs()[0];
        helper.assertTrue(patternInput.getPossibleInputs().length > 1,
                "planks tag should expand to multiple candidates, got " + patternInput.getPossibleInputs().length);
        helper.assertTrue(patternInput.getPossibleInputs()[0].what().equals(AEItemKey.of(Items.OAK_PLANKS)),
                "primary candidate must stay first");
        helper.assertTrue(patternInput.getMultiplier() == 4, "multiplier must be 4");
        helper.assertTrue(patternInput.isValid(AEItemKey.of(Items.BIRCH_PLANKS), level),
                "birch planks must match #minecraft:planks");
        helper.assertTrue(!patternInput.isValid(AEItemKey.of(Items.STONE), level),
                "stone must not match #minecraft:planks");
        helper.succeed();
    }

    @GameTest(template = "empty5")
    public void fuzzySpecIgnoresComponentsAndExactDoesNot(GameTestHelper helper) {
        var level = helper.getLevel();
        var output = new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1);

        var damagedPick = new ItemStack(Items.IRON_PICKAXE);
        damagedPick.setDamageValue(50);
        var damagedKey = AEItemKey.of(damagedPick);

        var fuzzyStack = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(fuzzyStack,
                List.of(new GenericStack(AEItemKey.of(Items.IRON_PICKAXE), 1)), List.of(output),
                List.of(AdaptiveInputSpec.fuzzy()));
        var fuzzy = PatternDetailsHelper.decodePattern(fuzzyStack, level);
        helper.assertTrue(fuzzy.getInputs()[0].isValid(damagedKey, level),
                "fuzzy spec must accept a damaged pickaxe");
        helper.assertTrue(!fuzzy.getInputs()[0].isValid(AEItemKey.of(Items.GOLDEN_PICKAXE), level),
                "fuzzy spec must reject a different item");

        var exactStack = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(exactStack,
                List.of(new GenericStack(AEItemKey.of(Items.IRON_PICKAXE), 1)), List.of(output),
                List.of(AdaptiveInputSpec.EXACT));
        var exact = PatternDetailsHelper.decodePattern(exactStack, level);
        helper.assertTrue(!exact.getInputs()[0].isValid(damagedKey, level),
                "exact spec must reject a damaged pickaxe");
        helper.succeed();
    }
}
