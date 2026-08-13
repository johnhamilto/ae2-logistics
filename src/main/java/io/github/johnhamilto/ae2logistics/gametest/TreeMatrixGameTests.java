package io.github.johnhamilto.ae2logistics.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;

/**
 * Generated matrix over the crafting-tree dimensions: spec mode x storage state x tree
 * depth, with negative controls. Every case builds a real network, plans a real job, and
 * asserts whether the plan completes.
 */
public class TreeMatrixGameTests {

    private static final Identifier PLANKS = Identifier.parse("minecraft:planks");
    private static final Identifier LOGS = Identifier.parse("minecraft:logs");
    private static final Identifier IRON_INGOTS = Identifier.parse("c:ingots/iron");

    private record Case(String name, List<ItemStack> patterns, List<ItemStack> storage,
            AEItemKey request, long amount, boolean expect) {
    }

    // Registration fires before item components bind, so cases() (which builds
    // ItemStacks) must not run until the test itself does - names alone register.
    private static final List<String> CASE_NAMES = List.of(
            "exact_present", "exact_rejects_renamed", "fuzzy_accepts_renamed_nondamageable",
            "tag_accepts_member", "tag_rejects_nonmember", "anyof_accepts_listed",
            "anyof_rejects_unlisted_tagmate", "band99_accepts_pristine", "band99_rejects_damaged",
            "fuzzy_accepts_damaged_renamed", "catalyst_fuzzy_reuses_damaged_tool",
            "depth2_fuzzy_leaf_renamed_log", "depth2_exact_leaf_rejects_other_log",
            "depth2_anyof_leaf_listed_log", "depth3_mixed_specs_renamed_leaf",
            "depth3_wrong_leaf_fails");

    static void register() {
        for (var name : CASE_NAMES) {
            LogisticsTestInstance.add("matrix_" + name, "empty5", 400,
                    helper -> runCase(name, helper));
        }
    }

    private static void runCase(String name, net.minecraft.gametest.framework.GameTestHelper helper) {
        for (var testCase : cases()) {
            if (testCase.name().equals(name)) {
                PatternGameTests.planPlot(helper, testCase.patterns(), testCase.storage(),
                        testCase.request(), testCase.amount(), testCase.expect(), testCase.name());
                return;
            }
        }
        throw helper.assertionException("unknown matrix case " + name);
    }

    private static List<Case> cases() {
        var cases = new ArrayList<Case>();
        var table = AEItemKey.of(Items.CRAFTING_TABLE);

        // Depth 1: one pattern (4x planks-with-spec -> table), storage varies.
        cases.add(new Case("exact_present",
                List.of(planksPattern(AdaptiveInputSpec.EXACT)),
                List.of(plain(Items.OAK_PLANKS, 4)), table, 1, true));
        cases.add(new Case("exact_rejects_renamed",
                List.of(planksPattern(AdaptiveInputSpec.EXACT)),
                List.of(renamed(Items.OAK_PLANKS, 4)), table, 1, false));
        cases.add(new Case("fuzzy_accepts_renamed_nondamageable",
                List.of(planksPattern(AdaptiveInputSpec.fuzzy())),
                List.of(renamed(Items.OAK_PLANKS, 4)), table, 1, true));
        cases.add(new Case("tag_accepts_member",
                List.of(planksPattern(AdaptiveInputSpec.ofTag(PLANKS))),
                List.of(plain(Items.BIRCH_PLANKS, 4)), table, 1, true));
        cases.add(new Case("tag_rejects_nonmember",
                List.of(planksPattern(AdaptiveInputSpec.ofTag(PLANKS))),
                List.of(plain(Items.COBBLESTONE, 4)), table, 1, false));
        cases.add(new Case("anyof_accepts_listed",
                List.of(planksPattern(AdaptiveInputSpec.EXACT
                        .withAlternative(new GenericStack(AEItemKey.of(Items.SPRUCE_PLANKS), 1)))),
                List.of(plain(Items.SPRUCE_PLANKS, 4)), table, 1, true));
        cases.add(new Case("anyof_rejects_unlisted_tagmate",
                List.of(planksPattern(AdaptiveInputSpec.EXACT
                        .withAlternative(new GenericStack(AEItemKey.of(Items.SPRUCE_PLANKS), 1)))),
                List.of(plain(Items.BIRCH_PLANKS, 4)), table, 1, false));

        // Depth 1 with a second, tool input (pickaxe-with-spec + 4x #planks -> table).
        cases.add(new Case("band99_accepts_pristine",
                List.of(toolPattern(AdaptiveInputSpec.fuzzy(FuzzyMode.PERCENT_99))),
                List.of(plain(Items.IRON_PICKAXE, 1), plain(Items.BIRCH_PLANKS, 8)), table, 1, true));
        cases.add(new Case("band99_rejects_damaged",
                List.of(toolPattern(AdaptiveInputSpec.fuzzy(FuzzyMode.PERCENT_99))),
                List.of(damagedRenamed(Items.IRON_PICKAXE), plain(Items.BIRCH_PLANKS, 8)), table, 1, false));
        cases.add(new Case("fuzzy_accepts_damaged_renamed",
                List.of(toolPattern(AdaptiveInputSpec.fuzzy())),
                List.of(damagedRenamed(Items.IRON_PICKAXE), plain(Items.BIRCH_PLANKS, 8)), table, 1, true));
        cases.add(new Case("catalyst_fuzzy_reuses_damaged_tool",
                List.of(toolPattern(AdaptiveInputSpec.fuzzy().withCatalyst(true))),
                List.of(damagedRenamed(Items.IRON_PICKAXE), plain(Items.BIRCH_PLANKS, 8)), table, 2, true));

        // Depth 2: logs -> planks -> table; the deep input's spec decides.
        cases.add(new Case("depth2_fuzzy_leaf_renamed_log",
                List.of(planksPattern(AdaptiveInputSpec.ofTag(PLANKS)),
                        logPattern(AdaptiveInputSpec.fuzzy())),
                List.of(renamed(Items.OAK_LOG, 1)), table, 1, true));
        cases.add(new Case("depth2_exact_leaf_rejects_other_log",
                List.of(planksPattern(AdaptiveInputSpec.ofTag(PLANKS)),
                        logPattern(AdaptiveInputSpec.EXACT)),
                List.of(plain(Items.SPRUCE_LOG, 1)), table, 1, false));
        cases.add(new Case("depth2_anyof_leaf_listed_log",
                List.of(planksPattern(AdaptiveInputSpec.ofTag(PLANKS)),
                        logPattern(AdaptiveInputSpec.EXACT
                                .withAlternative(new GenericStack(AEItemKey.of(Items.SPRUCE_LOG), 1)))),
                List.of(plain(Items.SPRUCE_LOG, 1)), table, 1, true));

        // Depth 3: ingot -> log -> planks -> table, tag+fuzzy+tag with NBT at the leaf.
        cases.add(new Case("depth3_mixed_specs_renamed_leaf",
                List.of(planksPattern(AdaptiveInputSpec.ofTag(PLANKS)),
                        logPattern(AdaptiveInputSpec.fuzzy()),
                        ingotToLogPattern(AdaptiveInputSpec.ofTag(IRON_INGOTS))),
                List.of(renamed(Items.IRON_INGOT, 1)), table, 1, true));
        cases.add(new Case("depth3_wrong_leaf_fails",
                List.of(planksPattern(AdaptiveInputSpec.ofTag(PLANKS)),
                        logPattern(AdaptiveInputSpec.fuzzy()),
                        ingotToLogPattern(AdaptiveInputSpec.ofTag(IRON_INGOTS))),
                List.of(plain(Items.COBBLESTONE, 4)), table, 1, false));

        return cases;
    }

    /** 4x oak planks (with spec) -> 1 crafting table. */
    private static ItemStack planksPattern(AdaptiveInputSpec spec) {
        return encode(
                List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                List.of(spec),
                new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1));
    }

    /** 1 oak log (with spec) -> 4 oak planks. */
    private static ItemStack logPattern(AdaptiveInputSpec spec) {
        return encode(
                List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 1)),
                List.of(spec),
                new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4));
    }

    /** 1 iron ingot (with spec) -> 1 oak log; the depth-3 chain bottom. */
    private static ItemStack ingotToLogPattern(AdaptiveInputSpec spec) {
        return encode(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                List.of(spec),
                new GenericStack(AEItemKey.of(Items.OAK_LOG), 1));
    }

    /** 1 iron pickaxe (with spec) + 4x #planks -> 1 crafting table. */
    private static ItemStack toolPattern(AdaptiveInputSpec pickaxeSpec) {
        var stack = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(stack,
                List.of(new GenericStack(AEItemKey.of(Items.IRON_PICKAXE), 1),
                        new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                List.of(pickaxeSpec, AdaptiveInputSpec.ofTag(PLANKS)));
        return stack;
    }

    private static ItemStack encode(List<GenericStack> inputs, List<AdaptiveInputSpec> specs,
            GenericStack output) {
        var stack = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(stack, inputs, List.of(output), specs);
        return stack;
    }

    private static ItemStack plain(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static ItemStack renamed(Item item, int count) {
        var stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed"));
        return stack;
    }

    private static ItemStack damagedRenamed(Item item) {
        var stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() / 2);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Trusty"));
        return stack;
    }
}
