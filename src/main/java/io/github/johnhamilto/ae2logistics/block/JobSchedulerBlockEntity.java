package io.github.johnhamilto.ae2logistics.block;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.MachineSource;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * F4's core: stock rules with admission control. Each rule keeps an item above a floor
 * by submitting batch crafts - but only when the plan is complete (no provably-stalling
 * jobs) and a CPU from the rule's class pool is free. Interactive CPUs are respected via
 * AE2's own Player-Only selection mode; bulk rules use unnamed or "bulk*" CPUs,
 * maintenance rules require "maint*" CPUs. Rate-limited so restocking never thunders.
 */
public class JobSchedulerBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IActionHost,
        io.github.johnhamilto.ae2logistics.config.TransferableSettings {

    public static final int RULES = 4;
    public static final int ATTEMPT_INTERVAL_TICKS = 200;

    public static final byte CLASS_BULK = 0;
    public static final byte CLASS_MAINT = 1;

    public static final byte STATE_IDLE = 0;
    public static final byte STATE_GUARD_HOLD = 1;
    public static final byte STATE_PLANNING = 2;
    public static final byte STATE_MISSING = 3;
    public static final byte STATE_NO_CPU = 4;
    public static final byte STATE_RUNNING = 5;
    public static final byte STATE_RATE_WAIT = 6;
    public static final byte STATE_DEADLINE = 7;
    public static final byte STATE_PREEMPTED = 8;

    public static final class Rule {
        @Nullable
        public GenericStack target;
        public long floor;
        public long batch = 16;
        public byte jobClass = CLASS_BULK;
        @Nullable
        public ResourceLocation guard;
        /** Max runtime in seconds once submitted; 0 disables the watchdog. */
        public long deadlineSeconds;
        /** May cancel the youngest same-class job of a lower-priority (higher-index) rule. */
        public boolean preempt;

        long lastAttemptTick = -ATTEMPT_INTERVAL_TICKS;
        long runStartTick;
        byte state = STATE_IDLE;
        byte deferReason;
        @Nullable
        Future<ICraftingPlan> future;
        // submitJob with a null requester returns NO link (CraftingSubmitResult
        // .successful(null)), so running jobs are tracked by CPU + expected output.
        @Nullable
        ICraftingCPU cpu;
        @Nullable
        AEKey runningOutput;
    }

    private static final IGridNodeListener<JobSchedulerBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(JobSchedulerBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setIdlePowerUsage(2.0)
            .setVisualRepresentation(AE2Logistics.JOB_SCHEDULER_ITEM.get());

    private final Rule[] rules = new Rule[RULES];
    private long tickCounter;

    public JobSchedulerBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.JOB_SCHEDULER_BE.get(), pos, state);
        for (int i = 0; i < RULES; i++) {
            rules[i] = new Rule();
        }
    }

    public Rule rule(int index) {
        return rules[index];
    }

    public byte ruleState(int index) {
        return rules[index].state;
    }

    public void applyRuleConfig(int index, long floor, long batch, byte jobClass,
            @Nullable ResourceLocation guard) {
        var rule = rules[index];
        applyRuleConfig(index, floor, batch, jobClass, guard, rule.deadlineSeconds, rule.preempt);
    }

    public void applyRuleConfig(int index, long floor, long batch, byte jobClass,
            @Nullable ResourceLocation guard, long deadlineSeconds, boolean preempt) {
        var rule = rules[index];
        rule.floor = Math.max(0, floor);
        rule.batch = Math.max(1, batch);
        rule.jobClass = (byte) Math.floorMod(jobClass, 2);
        rule.guard = guard;
        rule.deadlineSeconds = Math.max(0, deadlineSeconds);
        rule.preempt = preempt;
        setChanged();
    }

    public void setRuleTarget(int index, @Nullable GenericStack target) {
        rules[index].target = target == null ? null : new GenericStack(target.what(), 1);
        setChanged();
    }

    public void serverTick() {
        if (++tickCounter % 20 != 0 || level == null || level.isClientSide) {
            return;
        }
        var node = mainNode.getNode();
        if (node == null || node.getGrid() == null || !mainNode.isActive()) {
            return;
        }
        for (int i = 0; i < RULES; i++) {
            tickRule(i, rules[i], node);
        }
    }

    private void tickRule(int index, Rule rule, IGridNode node) {
        if (rule.target == null) {
            rule.state = STATE_IDLE;
            return;
        }
        var grid = node.getGrid();

        if (rule.cpu != null) {
            var status = rule.cpu.getJobStatus();
            boolean ours = status != null && status.crafting() != null
                    && rule.runningOutput != null
                    && status.crafting().what().equals(rule.runningOutput);
            if (!ours) {
                // Finished, canceled, or the CPU moved on to someone else's job.
                rule.cpu = null;
                rule.runningOutput = null;
            } else if (rule.deadlineSeconds > 0
                    // Wall-clock since submission: AE2's own elapsed tracker pauses
                    // while a job is stalled, which is exactly when eviction matters.
                    && tickCounter - rule.runStartTick >= rule.deadlineSeconds * 20) {
                // Watchdog: evict the overdue job so its CPU frees, then re-admit later.
                rule.cpu.cancelJob();
                rule.cpu = null;
                rule.runningOutput = null;
                rule.deferReason = STATE_DEADLINE;
                rule.lastAttemptTick = tickCounter;
                rule.state = STATE_DEADLINE;
                return;
            } else {
                rule.state = STATE_RUNNING;
                return;
            }
        }

        if (rule.future != null) {
            if (!rule.future.isDone()) {
                rule.state = STATE_PLANNING;
                return;
            }
            ICraftingPlan plan;
            try {
                plan = rule.future.get();
            } catch (ExecutionException | InterruptedException e) {
                rule.future = null;
                rule.state = STATE_MISSING;
                rule.deferReason = STATE_MISSING;
                return;
            }
            rule.future = null;
            if (plan.simulation()) {
                rule.state = STATE_MISSING;
                rule.deferReason = STATE_MISSING;
                return;
            }
            var cpu = pickCpu(grid, rule.jobClass, plan.bytes());
            if (cpu == null) {
                rule.state = STATE_NO_CPU;
                rule.deferReason = STATE_NO_CPU;
                tryPreempt(index, rule);
                return;
            }
            var result = grid.getCraftingService().submitJob(plan, null, cpu, false,
                    new MachineSource(this));
            if (result.successful()) {
                rule.cpu = cpu;
                rule.runningOutput = plan.finalOutput().what();
                rule.runStartTick = tickCounter;
                rule.state = STATE_RUNNING;
                rule.deferReason = 0;
            } else {
                rule.state = STATE_NO_CPU;
                rule.deferReason = STATE_NO_CPU;
                tryPreempt(index, rule);
            }
            return;
        }

        if (rule.guard != null) {
            var signals = grid.getService(SignalService.class);
            if (signals != null && signals.get(rule.guard) <= 0) {
                rule.state = STATE_GUARD_HOLD;
                return;
            }
        }

        long stored = grid.getStorageService().getCachedInventory().get(rule.target.what());
        if (stored >= rule.floor) {
            rule.state = STATE_IDLE;
            rule.deferReason = 0;
            return;
        }
        if (tickCounter - rule.lastAttemptTick < io.github.johnhamilto.ae2logistics.AE2LogisticsConfig
                .schedulerAttemptIntervalTicks()) {
            // Keep showing WHY the last attempt failed while the retry window runs.
            rule.state = rule.deferReason != 0 ? rule.deferReason : STATE_RATE_WAIT;
            return;
        }
        rule.lastAttemptTick = tickCounter;
        rule.state = STATE_PLANNING;
        rule.future = grid.getCraftingService().beginCraftingCalculation(level,
                () -> new MachineSource(this), rule.target.what(), rule.batch,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /**
     * Within-pool priority preemption: a deferred rule with preempt enabled cancels the
     * youngest running job of a lower-priority (higher-index) rule in the SAME class
     * pool, freeing a CPU that pool can actually use. Only jobs this scheduler
     * originated are ever touched - foreign jobs are not ours to cancel.
     */
    private void tryPreempt(int index, Rule rule) {
        if (!rule.preempt) {
            return;
        }
        Rule victim = null;
        for (int i = index + 1; i < RULES; i++) {
            var candidate = rules[i];
            if (candidate.cpu == null || candidate.jobClass != rule.jobClass) {
                continue;
            }
            // Youngest job = most recently submitted.
            if (victim == null || candidate.runStartTick > victim.runStartTick) {
                victim = candidate;
            }
        }
        if (victim != null) {
            victim.cpu.cancelJob();
            victim.cpu = null;
            victim.runningOutput = null;
            victim.deferReason = STATE_PREEMPTED;
            victim.lastAttemptTick = tickCounter;
            // Retry within a second instead of waiting out the full rate window.
            rule.lastAttemptTick = tickCounter
                    - io.github.johnhamilto.ae2logistics.AE2LogisticsConfig.schedulerAttemptIntervalTicks()
                    + 20;
        }
    }

    /**
     * Free CPU from the rule's class pool: bulk uses unnamed or "bulk*" CPUs,
     * maintenance requires "maint*". Player-Only CPUs are never taken (AE2's own
     * interactive reservation), and the smallest CPU that fits the plan wins.
     */
    @Nullable
    private static ICraftingCPU pickCpu(appeng.api.networking.IGrid grid, byte jobClass, long bytes) {
        ICraftingCPU best = null;
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (cpu.isBusy() || cpu.getSelectionMode() == CpuSelectionMode.PLAYER_ONLY
                    || cpu.getAvailableStorage() < bytes) {
                continue;
            }
            var name = cpu.getName();
            var lower = name == null ? null
                    : name.getString().toLowerCase(java.util.Locale.ROOT);
            boolean matches = jobClass == CLASS_MAINT
                    ? lower != null && lower.startsWith("maint")
                    : lower == null || lower.startsWith("bulk");
            if (!matches) {
                continue;
            }
            if (best == null || cpu.getAvailableStorage() < best.getAvailableStorage()) {
                best = cpu;
            }
        }
        return best;
    }

    // --- lifecycle ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, be -> be.mainNode.create(be.level, be.getBlockPos()));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.put("rules", saveRules(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        loadRules(tag.getList("rules", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }

    private net.minecraft.nbt.ListTag saveRules(HolderLookup.Provider registries) {
        var list = new net.minecraft.nbt.ListTag();
        for (var rule : rules) {
            var ruleTag = new CompoundTag();
            if (rule.target != null) {
                ruleTag.put("key", rule.target.what().toTagGeneric(registries));
            }
            ruleTag.putLong("floor", rule.floor);
            ruleTag.putLong("batch", rule.batch);
            ruleTag.putByte("class", rule.jobClass);
            if (rule.guard != null) {
                ruleTag.putString("guard", rule.guard.toString());
            }
            ruleTag.putLong("deadline", rule.deadlineSeconds);
            ruleTag.putBoolean("preempt", rule.preempt);
            list.add(ruleTag);
        }
        return list;
    }

    private void loadRules(net.minecraft.nbt.ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < RULES && i < list.size(); i++) {
            var ruleTag = list.getCompound(i);
            var rule = rules[i];
            if (ruleTag.contains("key")) {
                var key = AEKey.fromTagGeneric(registries, ruleTag.getCompound("key"));
                rule.target = key == null ? null : new GenericStack(key, 1);
            } else {
                rule.target = null;
            }
            rule.floor = ruleTag.getLong("floor");
            rule.batch = Math.max(1, ruleTag.getLong("batch"));
            rule.jobClass = ruleTag.getByte("class");
            rule.guard = ruleTag.contains("guard")
                    ? ResourceLocation.tryParse(ruleTag.getString("guard"))
                    : null;
            rule.deadlineSeconds = Math.max(0, ruleTag.getLong("deadline"));
            rule.preempt = ruleTag.getBoolean("preempt");
        }
    }

    @Override
    public net.minecraft.core.component.DataComponentMap exportTransferSettings(
            @Nullable net.minecraft.world.entity.player.Player player) {
        if (level == null) {
            return net.minecraft.core.component.DataComponentMap.EMPTY;
        }
        var tag = new CompoundTag();
        tag.put("schedulerRules", saveRules(level.registryAccess()));
        return net.minecraft.core.component.DataComponentMap.builder()
                .set(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get(), tag)
                .build();
    }

    @Override
    public void importTransferSettings(net.minecraft.core.component.DataComponentMap settings,
            @Nullable net.minecraft.world.entity.player.Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        var tag = settings.get(AE2Logistics.EXPORTED_LOGIC_SETTINGS.get());
        if (tag == null || !tag.contains("schedulerRules")) {
            return;
        }
        loadRules(tag.getList("schedulerRules", net.minecraft.nbt.Tag.TAG_COMPOUND),
                level.registryAccess());
        setChanged();
    }

    // --- grid plumbing ---

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }
}
