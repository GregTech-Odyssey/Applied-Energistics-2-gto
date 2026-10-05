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

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ExternalStorageLookup;
import appeng.api.storage.StorageAccess;

/**
 * Wrapper used by the pattern provider logic to interact with adjacent inventories.
 */
public interface PatternProviderTarget {
    @Nullable
    static PatternProviderTarget get(Level l, BlockPos pos, @Nullable BlockEntity be, Direction side,
            IActionSource src) {
        var storage = ExternalStorageLookup.resolve(be, side, null, StorageAccess.FULL);
        return storage != null ? new MEStorageTarget(storage, src) : null;
    }

    long insert(AEKey what, long amount, Actionable type);

    boolean containsPatternInput(Set<AEKey> patternInputs);
}
