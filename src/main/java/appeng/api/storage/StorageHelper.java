/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;

public final class StorageHelper {
    private StorageHelper() {
    }

    /**
     * load a crafting link from nbt data.
     *
     * @param data to be loaded data
     * @return crafting link
     */
    public static ICraftingLink loadCraftingLink(CompoundTag data, ICraftingRequester req) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(req);

        return new CraftingLink(data, req);
    }

    /**
     * Extracts items from a {@link MEStorage} respecting power requirements.
     *
     * @param energy  Energy source.
     * @param inv     Inventory to extract from.
     * @param request Requested item and count.
     * @param src     Action source.
     * @return extracted items or {@code null} of nothing was extracted.
     */
    public static long poweredExtraction(IEnergySource energy, MEStorage inv,
            AEKey request, long amount, IActionSource src) {
        return poweredExtraction(energy, inv, request, amount, src, Actionable.MODULATE);
    }

    /**
     * Extracts items from a {@link MEStorage} respecting power requirements.
     *
     * @param energy  Energy source.
     * @param inv     Inventory to extract from.
     * @param request Requested item and count.
     * @param src     Action source.
     * @param mode    Simulate or modulate
     * @return extracted items or {@code null} of nothing was extracted.
     */
    public static long poweredExtraction(IEnergySource energy, MEStorage inv, AEKey request, long amount,
            IActionSource src, Actionable mode) {
        amount = inv.extract(request, amount, mode, src);
        if (amount <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            var energyFactor = Math.max(1.0, request.getAmountPerOperation());
            energy.extractAEPower(amount / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        return amount;
    }

    /**
     * Inserts items into a {@link MEStorage} respecting power requirements.
     *
     * @param energy Energy source.
     * @param inv    Inventory to insert into.
     * @param input  Items to insert.
     * @param src    Action source.
     * @return the number of items inserted.
     */
    public static long poweredInsert(IEnergySource energy, MEStorage inv,
            AEKey input, long amount, IActionSource src) {
        return poweredInsert(energy, inv, input, amount, src, Actionable.MODULATE);
    }

    /**
     * Inserts items into a {@link MEStorage} respecting power requirements.
     *
     * @param energy Energy source.
     * @param inv    Inventory to insert into.
     * @param input  Items to insert.
     * @param src    Action source.
     * @param mode   Simulate or modulate
     * @return the number of items inserted.
     */
    public static long poweredInsert(IEnergySource energy, MEStorage inv, AEKey input, long amount, IActionSource src,
            Actionable mode) {
        amount = inv.insert(input, amount, mode, src);
        if (amount <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            final double energyFactor = Math.max(1.0, input.getAmountPerOperation());
            energy.extractAEPower(amount / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        return amount;
    }
}
