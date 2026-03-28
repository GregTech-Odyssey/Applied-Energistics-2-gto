/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting;

import java.util.Objects;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

public final class CraftingPlan implements ICraftingPlan {
    private long gtocore$primaryOutputThreshold = 0; // 当主产物接受次数积累到>此值，即可输出到ae，例如为3，那么当主产物积累到4个时就会输出1个
    private long gtocore$primaryOutputThreatAccumulate = 0; // 主产物接受次数积累
    private long gtocore$primaryOutputThreatAccumulateThreshold = 0; // 发送次数

    private Reference2ObjectOpenHashMap<AEKey, Object2LongOpenHashMap<IPatternDetails>> gtocore$allocations; // 某个物品对某些样板优先输出的次数

    private final GenericStack finalOutput;
    private final long bytes;
    private final boolean simulation;
    private final boolean multiplePaths;
    private final KeyCounter usedItems;
    private final KeyCounter emittedItems;
    private final KeyCounter missingItems;
    private final Object2LongMap<IPatternDetails> patternTimes;

    public CraftingPlan(GenericStack finalOutput,
            long bytes,
            boolean simulation,
            boolean multiplePaths,
            KeyCounter usedItems,
            KeyCounter emittedItems,
            KeyCounter missingItems,
            Object2LongMap<IPatternDetails> patternTimes) {
        this.finalOutput = finalOutput;
        this.bytes = bytes;
        this.simulation = simulation;
        this.multiplePaths = multiplePaths;
        this.usedItems = usedItems;
        this.emittedItems = emittedItems;
        this.missingItems = missingItems;
        this.patternTimes = patternTimes;
    }

    @Override
    public GenericStack finalOutput() {
        return finalOutput;
    }

    @Override
    public long bytes() {
        return bytes;
    }

    @Override
    public boolean simulation() {
        return simulation;
    }

    @Override
    public boolean multiplePaths() {
        return multiplePaths;
    }

    @Override
    public KeyCounter usedItems() {
        return usedItems;
    }

    @Override
    public KeyCounter emittedItems() {
        return emittedItems;
    }

    @Override
    public KeyCounter missingItems() {
        return missingItems;
    }

    @Override
    public Object2LongMap<IPatternDetails> patternTimes() {
        return patternTimes;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (CraftingPlan) obj;
        return Objects.equals(this.finalOutput, that.finalOutput) &&
                this.bytes == that.bytes &&
                this.simulation == that.simulation &&
                this.multiplePaths == that.multiplePaths &&
                Objects.equals(this.usedItems, that.usedItems) &&
                Objects.equals(this.emittedItems, that.emittedItems) &&
                Objects.equals(this.missingItems, that.missingItems) &&
                Objects.equals(this.patternTimes, that.patternTimes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(finalOutput, bytes, simulation, multiplePaths, usedItems, emittedItems, missingItems,
                patternTimes);
    }

    @Override
    public String toString() {
        return "CraftingPlan[" +
                "finalOutput=" + finalOutput + ", " +
                "bytes=" + bytes + ", " +
                "simulation=" + simulation + ", " +
                "multiplePaths=" + multiplePaths + ", " +
                "usedItems=" + usedItems + ", " +
                "emittedItems=" + emittedItems + ", " +
                "missingItems=" + missingItems + ", " +
                "patternTimes=" + patternTimes + ']';
    }

    public long getGtocore$primaryOutputThreshold() {
        return gtocore$primaryOutputThreshold;
    }

    public void setGtocore$primaryOutputThreshold(long gtocore$primaryOutputThreshold) {
        this.gtocore$primaryOutputThreshold = gtocore$primaryOutputThreshold;
    }

    public long getGtocore$primaryOutputThreatAccumulate() {
        return gtocore$primaryOutputThreatAccumulate;
    }

    public void setGtocore$primaryOutputThreatAccumulate(long gtocore$primaryOutputThreatAccumulate) {
        this.gtocore$primaryOutputThreatAccumulate = gtocore$primaryOutputThreatAccumulate;
    }

    public long getGtocore$primaryOutputThreatAccumulateThreshold() {
        return gtocore$primaryOutputThreatAccumulateThreshold;
    }

    public void setGtocore$primaryOutputThreatAccumulateThreshold(long gtocore$primaryOutputThreatAccumulateThreshold) {
        this.gtocore$primaryOutputThreatAccumulateThreshold = gtocore$primaryOutputThreatAccumulateThreshold;
    }

    public Reference2ObjectOpenHashMap<AEKey, Object2LongOpenHashMap<IPatternDetails>> getGtocore$allocations() {
        return gtocore$allocations;
    }

    public void setGtocore$allocations(
            Reference2ObjectOpenHashMap<AEKey, Object2LongOpenHashMap<IPatternDetails>> gtocore$allocations) {
        this.gtocore$allocations = gtocore$allocations;
    }
}
