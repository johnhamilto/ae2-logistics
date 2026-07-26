package io.github.johnhamilto.ae2logistics.signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;

public class SignalGridService implements SignalService, IGridServiceProvider, IStorageProvider {

    private final Map<ResourceLocation, Long> stored = new HashMap<>();
    private final Map<ResourceLocation, Long> committed = new HashMap<>();
    private final Map<ResourceLocation, Long> writtenThisTick = new HashMap<>();
    private Set<ResourceLocation> lastPartDriven = new HashSet<>();

    private final Map<IGridNode, ILogicNode> logicNodes = new IdentityHashMap<>();
    @Nullable
    private List<ILogicNode> evalOrder;

    public SignalGridService(IGrid grid, IStorageService storageService) {
        storageService.addGlobalStorageProvider(this);
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        storageMounts.mount(new SignalGridStorage(this));
    }

    @Override
    public long get(ResourceLocation channel) {
        return committed.getOrDefault(channel, 0L);
    }

    @Override
    public void setStored(ResourceLocation channel, long value) {
        if (value <= 0) {
            stored.remove(channel);
            if (!lastPartDriven.contains(channel)) {
                committed.remove(channel);
            }
        } else {
            stored.put(channel, value);
            if (!lastPartDriven.contains(channel)) {
                committed.put(channel, value);
            }
        }
    }

    @Override
    public Map<ResourceLocation, Long> committed() {
        return committed;
    }

    @Override
    public void invalidateGraph() {
        evalOrder = null;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable net.minecraft.nbt.CompoundTag savedData) {
        var logic = gridNode.getService(ILogicNode.class);
        if (logic != null) {
            logicNodes.put(gridNode, logic);
            evalOrder = null;
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        if (logicNodes.remove(gridNode) != null) {
            evalOrder = null;
        }
    }

    @Override
    public void onServerStartTick() {
        if (logicNodes.isEmpty() && lastPartDriven.isEmpty()) {
            return;
        }
        if (evalOrder == null) {
            evalOrder = buildEvalOrder();
        }

        writtenThisTick.clear();
        var context = new ILogicNode.LogicContext() {
            ILogicNode current;

            @Override
            public long read(ResourceLocation channel) {
                var written = writtenThisTick.get(channel);
                return written != null ? written : committed.getOrDefault(channel, 0L);
            }

            @Override
            public void write(long value) {
                var channel = current.writtenChannel();
                if (channel != null) {
                    writtenThisTick.merge(channel, Math.max(0, value), SignalMath::add);
                }
            }
        };

        for (var node : evalOrder) {
            context.current = node;
            node.evaluate(context);
        }

        // Commit: part-written channels take this tick's value; channels a part stopped
        // driving fall back to their stored value.
        for (var channel : lastPartDriven) {
            if (!writtenThisTick.containsKey(channel)) {
                var storedValue = stored.get(channel);
                if (storedValue != null) {
                    committed.put(channel, storedValue);
                } else {
                    committed.remove(channel);
                }
            }
        }
        committed.putAll(writtenThisTick);
        lastPartDriven = new HashSet<>(writtenThisTick.keySet());
    }

    /**
     * Kahn's algorithm over the channel dataflow graph (edges run from each writer of a
     * channel to each of its readers). Ready nodes evaluate in stableKey order so the
     * result is deterministic. When only cycles remain, the remaining node with the
     * smallest stableKey is forced ready; its unmet inputs read last tick's values,
     * giving every cycle an implicit one-tick delay at a deterministic point.
     */
    private List<ILogicNode> buildEvalOrder() {
        var nodes = new ArrayList<>(logicNodes.values());
        Map<ResourceLocation, List<ILogicNode>> writers = new HashMap<>();
        for (var node : nodes) {
            var written = node.writtenChannel();
            if (written != null) {
                writers.computeIfAbsent(written, k -> new ArrayList<>()).add(node);
            }
        }

        Map<ILogicNode, Set<ILogicNode>> outgoing = new IdentityHashMap<>();
        Map<ILogicNode, Integer> indegree = new IdentityHashMap<>();
        for (var node : nodes) {
            indegree.putIfAbsent(node, 0);
            for (var channel : node.readChannels()) {
                for (var writer : writers.getOrDefault(channel, List.of())) {
                    if (writer == node) {
                        continue;
                    }
                    if (outgoing.computeIfAbsent(writer, k -> new HashSet<>()).add(node)) {
                        indegree.merge(node, 1, Integer::sum);
                    }
                }
            }
        }

        var ready = new PriorityQueue<>(Comparator.comparingLong(ILogicNode::stableKey));
        for (var node : nodes) {
            if (indegree.get(node) == 0) {
                ready.add(node);
            }
        }

        var order = new ArrayList<ILogicNode>(nodes.size());
        var placed = new HashSet<ILogicNode>();
        while (order.size() < nodes.size()) {
            ILogicNode next = ready.poll();
            if (next == null) {
                // Only cycles remain: force the smallest remaining key.
                next = nodes.stream()
                        .filter(n -> !placed.contains(n))
                        .min(Comparator.comparingLong(ILogicNode::stableKey))
                        .orElseThrow();
            }
            if (!placed.add(next)) {
                continue;
            }
            order.add(next);
            for (var reader : outgoing.getOrDefault(next, Set.of())) {
                if (!placed.contains(reader) && indegree.merge(reader, -1, Integer::sum) == 0) {
                    ready.add(reader);
                }
            }
        }
        return order;
    }
}
