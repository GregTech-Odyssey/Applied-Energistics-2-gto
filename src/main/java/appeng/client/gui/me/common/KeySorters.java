/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.client.gui.me.common;

import java.util.Comparator;

import com.fast.fastcollection.O2IOpenCacheHashMap;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;

final class KeySorters {

    private KeySorters() {
    }

    // FIXME: Calling .getString() to compare two untranslated strings is a problem, we need to investigate how to do
    // this better
    public static final Comparator<AEKey> NAME_ASC = Comparator.comparingInt(
            KeySorters::cachedToValue);

    public static final Comparator<AEKey> NAME_DESC = NAME_ASC.reversed();

    public static final Comparator<AEKey> MOD_ASC = Comparator.comparing(
            AEKey::getModId,
            KeySorters::cachedCompareToIgnoreCase).thenComparing(NAME_ASC);

    public static final Comparator<AEKey> MOD_DESC = MOD_ASC.reversed();

    public static Comparator<AEKey> getComparator(SortOrder order, SortDir dir) {
        return switch (order) {
            case NAME -> dir == SortDir.ASCENDING ? NAME_ASC : NAME_DESC;
            case MOD -> dir == SortDir.ASCENDING ? MOD_ASC : MOD_DESC;
            case AMOUNT -> throw new UnsupportedOperationException();
        };
    }

    private static final String VERY_LONG_STRING = new StringBuilder().repeat(" ", 512).toString();

    private static int cachedCompareToIgnoreCase(String a, String b) {
        int aValue = cachedStringValues.computeIfAbsent(a, str -> a.compareToIgnoreCase(VERY_LONG_STRING));
        int bValue = cachedStringValues.computeIfAbsent(b, str -> b.compareToIgnoreCase(VERY_LONG_STRING));
        return Integer.compare(aValue, bValue);
    }

    private static int cachedToValue(AEKey a) {
        return cachedKeyValues.computeIfAbsent(a,
                key -> a.getDisplayName().getString().compareToIgnoreCase(VERY_LONG_STRING));
    }

    private static final O2IOpenCacheHashMap<String> cachedStringValues = new O2IOpenCacheHashMap<>();
    private static final Reference2IntOpenHashMap<AEKey> cachedKeyValues = new Reference2IntOpenHashMap<>();

}
