package io.github.johnhamilto.ae2logistics.gametest;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.me.helpers.MachineSource;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.crafting.AdaptiveInputSpec;
import io.github.johnhamilto.ae2logistics.crafting.AdaptivePattern;
import io.github.johnhamilto.ae2logistics.parts.LogicPart;

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

    /**
     * End to end: network storage holds only birch planks; an adaptive pattern wants 4x
     * "#minecraft:planks" (primary oak) to make a crafting table. The planner must accept
     * birch as a substitute and the provider must push exactly those planks.
     */
    @GameTest(template = "empty5", timeoutTicks = 400)
    public void autocraftConsumesTagSubstitute(GameTestHelper helper) {
        var level = helper.getLevel();

        helper.setBlock(new BlockPos(1, 1, 1),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")));
        var busPos = helper.absolutePos(new BlockPos(2, 1, 1));
        var cable = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable"));
        PartHelper.setPart(level, busPos, null, null, (IPartItem<?>) cable);

        helper.setBlock(new BlockPos(3, 1, 1), net.minecraft.world.level.block.Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(3, 1, 1)) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.BIRCH_PLANKS, 8));
        }
        var storageBus = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:storage_bus"));
        PartHelper.setPart(level, busPos, Direction.EAST, null, (IPartItem<?>) storageBus);

        var gridHandle = (LogicPart) PartHelper.setPart(level, busPos, Direction.NORTH, null,
                AE2Logistics.CONSTANT_PART.get());

        var craftingStorage = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage"));
        helper.setBlock(new BlockPos(2, 2, 1), craftingStorage);
        helper.assertTrue(!helper.getBlockState(new BlockPos(2, 2, 1)).isAir(), "crafting storage missing");

        var provider = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider"));
        helper.setBlock(new BlockPos(2, 1, 2), provider);
        helper.assertTrue(!helper.getBlockState(new BlockPos(2, 1, 2)).isAir(), "pattern provider missing");
        helper.setBlock(new BlockPos(2, 1, 3), net.minecraft.world.level.block.Blocks.CHEST);

        var pattern = new ItemStack(AE2Logistics.ADAPTIVE_PATTERN.get());
        AdaptivePattern.encode(pattern,
                List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)),
                List.of(AdaptiveInputSpec.ofTag(ResourceLocation.parse("minecraft:planks"))));

        if (!(helper.getBlockEntity(new BlockPos(2, 1, 2)) instanceof PatternProviderBlockEntity providerBe)) {
            helper.fail("no pattern provider block entity");
            return;
        }
        providerBe.getLogic().getPatternInv().setItemDirect(0, pattern);

        var job = new Object() {
            Future<ICraftingPlan> future;
            ICraftingPlan plan;
            boolean submitted;
        };

        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    var grid = gridHandle.getMainNode().getGrid();
                    helper.assertTrue(grid != null, "grid handle has no grid");
                    var known = grid.getCraftingService().getCraftingFor(AEItemKey.of(Items.CRAFTING_TABLE));
                    helper.assertTrue(!known.isEmpty(),
                            "crafting service has no pattern for crafting_table;"
                                    + " providerActive=" + providerBe.getMainNode().isActive()
                                    + " slot=" + providerBe.getLogic().getPatternInv().getStackInSlot(0)
                                    + " decodable=" + (PatternDetailsHelper.decodePattern(
                                            providerBe.getLogic().getPatternInv().getStackInSlot(0), level) != null));
                    var source = new MachineSource(gridHandle);
                    job.future = grid.getCraftingService().beginCraftingCalculation(level,
                            () -> source, AEItemKey.of(Items.CRAFTING_TABLE), 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                })
                .thenWaitUntil(() -> {
                    if (job.plan == null) {
                        try {
                            job.plan = job.future.get(0, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException e) {
                            throw new GameTestAssertException("still planning");
                        } catch (Exception e) {
                            throw new RuntimeException("planning failed", e);
                        }
                    }
                    if (job.plan.simulation()) {
                        var missing = new StringBuilder();
                        for (var entry : job.plan.missingItems()) {
                            missing.append(entry.getKey()).append('=').append(entry.getLongValue()).append(' ');
                        }
                        throw new GameTestAssertException("plan incomplete; missing: [" + missing + "]");
                    }
                    if (!job.submitted) {
                        var grid = gridHandle.getMainNode().getGrid();
                        var result = grid.getCraftingService().submitJob(job.plan, null, null, true,
                                new MachineSource(gridHandle));
                        if (!result.successful()) {
                            helper.fail("job submission failed: " + result.errorCode()
                                    + " detail=" + result.errorDetail());
                        }
                        job.submitted = true;
                    }
                })
                .thenExecuteAfter(60, () -> {
                    if (helper.getBlockEntity(new BlockPos(2, 1, 3)) instanceof ChestBlockEntity target) {
                        int birch = 0;
                        for (int i = 0; i < target.getContainerSize(); i++) {
                            var stack = target.getItem(i);
                            if (stack.is(Items.BIRCH_PLANKS)) {
                                birch += stack.getCount();
                            }
                        }
                        helper.assertTrue(birch == 4,
                                "provider should have pushed 4 birch planks, found " + birch);
                    } else {
                        helper.fail("no target chest");
                    }
                })
                .thenSucceed();
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
