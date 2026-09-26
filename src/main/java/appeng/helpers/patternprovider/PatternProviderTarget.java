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

package appeng.helpers.patternprovider;

import java.util.IdentityHashMap;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.util.concurrent.Runnables;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.capabilities.Capabilities;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;

/**
 * Wrapper used by the pattern provider logic to interact with adjacent inventories.
 */
public interface PatternProviderTarget {
    @Nullable
    static PatternProviderTarget get(Level l, BlockPos pos, @Nullable BlockEntity be, Direction side,
            IActionSource src) {
        if (be == null)
            return null;

        // our capability first: allows any storage channel
        var storage = be.getCapability(Capabilities.STORAGE, side).orElse(null);
        if (storage != null) {
            return wrapMeStorage(storage, src);
        }

        // otherwise fall back to the platform capability
        // TODO: possibly optimize this
        var strategies = StackWorldBehaviors.createExternalStorageStrategies((ServerLevel) l, pos, side);
        var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(2);
        for (var entry : strategies.entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, Runnables.doNothing());
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        if (externalStorages.size() > 0) {
            return wrapMeStorage(new CompositeStorage(externalStorages), src);
        }

        return null;
    }

    ResourceLocation PROGRAMMED_CIRCUIT = new ResourceLocation("gtceu", "programmed_circuit");

    private static PatternProviderTarget wrapMeStorage(MEStorage storage, IActionSource src) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return storage.insert(what, amount, type, src);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                return PatternProviderTarget.isBlocked(storage, patternInputs, null, AdvancedBlockingMode.DEFAULT);
            }
        };
    }

    static boolean isBlocked(MEStorage storage, Set<AEKey> patternInputs, @Nullable MEStorage advancedBlockingStorage,
            AdvancedBlockingMode advancedBlockingMode) {
        if (advancedBlockingStorage != null) {
            for (var stack : advancedBlockingStorage.getAvailableStacks()) {
                var key = stack.getKey();

                if (advancedBlockingMode == AdvancedBlockingMode.LOOSE) {
                    return true;
                }

                if (key instanceof AEItemKey itemKey && PROGRAMMED_CIRCUIT.equals(itemKey.getId())) {
                    continue;
                }

                if (patternInputs.contains(key.dropSecondary())) {
                    return true;
                }
            }
            return false;
        }

        for (var stack : storage.getAvailableStacks()) {
            if (patternInputs.contains(stack.getKey().dropSecondary())) {
                return true;
            }
        }
        return false;
    }

    long insert(AEKey what, long amount, Actionable type);

    boolean containsPatternInput(Set<AEKey> patternInputs);
}
