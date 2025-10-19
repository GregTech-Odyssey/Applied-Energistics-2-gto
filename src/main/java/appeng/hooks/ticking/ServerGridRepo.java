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

package appeng.hooks.ticking;

import java.util.Objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import appeng.me.Grid;

/**
 * A class to hold data related to ticking networks.
 */
class ServerGridRepo {
    final ObjectArrayList<Grid> networks = new ObjectArrayList<>();
    private final ReferenceOpenHashSet<Grid> toAdd = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<Grid> toRemove = new ReferenceOpenHashSet<>();
    final ObjectArrayList<Grid> start = new ObjectArrayList<>();
    final ObjectArrayList<Grid> end = new ObjectArrayList<>();
    final ObjectArrayList<Grid> lStart = new ObjectArrayList<>();
    final ObjectArrayList<Grid> lEnd = new ObjectArrayList<>();

    /**
     * Resets all internal data
     */
    void clear() {
        this.networks.clear();
        this.toAdd.clear();
        this.toRemove.clear();
    }

    /**
     * Queues adding a new network.
     * <p>
     * Is added once {@link ServerGridRepo#updateNetworks()} is called.
     * <p>
     * Also removes it from the removal list, in case the network is validated again.
     */
    synchronized void addNetwork(Grid g) {
        Objects.requireNonNull(g);

        this.toAdd.add(g);
        this.toRemove.remove(g);
    }

    /**
     * Queues removal of a network.
     * <p>
     * Is fully removed once {@link ServerGridRepo#updateNetworks()} is called.
     * <p>
     * Also removes it from the list to add in case it got invalid.
     */
    synchronized void removeNetwork(Grid g) {
        Objects.requireNonNull(g);

        this.toRemove.add(g);
        this.toAdd.remove(g);
    }

    /**
     * Processes all networks to add or remove.
     * <p>
     * First all removals are handled, then the ones queued to be added.
     */
    synchronized void updateNetworks() {
        this.networks.removeAll(this.toRemove);
        this.toRemove.clear();

        this.networks.addAll(this.toAdd);
        this.toAdd.clear();
        start.clear();
        end.clear();
        lStart.clear();
        lEnd.clear();
        networks.forEach(g -> {
            if (g.pivot == null)
                return;
            if (g.services.hasServerStartTick())
                start.add(g);
            if (g.services.hasServerEndTick())
                end.add(g);
            if (g.services.hasLevelStartTick())
                lStart.add(g);
            if (g.services.hasLevelEndTick())
                lEnd.add(g);
        });
    }

}
