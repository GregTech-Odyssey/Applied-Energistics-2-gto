/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.me.service;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectFunction;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.events.GridBootingStatusChange;
import appeng.api.networking.events.GridPowerStatusChange;
import appeng.core.AELog;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.parts.p2p.P2PTunnelPart;

public class P2PService implements IGridService, IGridServiceProvider {
    static {
        GridHelper.addGridServiceEventHandler(GridBootingStatusChange.class, P2PService.class,
                (service, evt) -> {
                    if (!evt.isBooting()) {
                        service.wakeInputTunnels();
                    }
                });
        GridHelper.addGridServiceEventHandler(GridPowerStatusChange.class, P2PService.class,
                (service, evt) -> {
                    service.wakeInputTunnels();
                });
    }

    public static P2PService get(IGrid grid) {
        return grid.getService(P2PService.class);
    }

    private final IGrid myGrid;
    private final ReferenceOpenHashSet<P2PTunnelPart> all = new ReferenceOpenHashSet<>();
    private final Short2ObjectOpenHashMap<ArrayList<P2PTunnelPart>> inputs =new  Short2ObjectOpenHashMap<>();
    private final Short2ObjectOpenHashMap<ArrayList<P2PTunnelPart>> outputs =new  Short2ObjectOpenHashMap<>();
    private final Random frequencyGenerator;

    public P2PService(IGrid g) {
        this.myGrid = g;
        this.frequencyGenerator = new Random(g.hashCode());
    }

    public void wakeInputTunnels() {
        var tm = this.myGrid.getTickManager();
        for (var tunnels : this.inputs.values()) {
            for (var tunnel :tunnels) {
                if (tunnel instanceof MEP2PTunnelPart) {
                    tm.wakeDevice(tunnel.getGridNode());
                }
            }
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof P2PTunnelPart<?> tunnel) {
            if (tunnel instanceof MEP2PTunnelPart && !node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                return;
            }
            all.forEach(P2PTunnelPart::update);
            all.remove(tunnel);
            if (tunnel.isOutput()) {
                var outputs = this.outputs.get(tunnel.getFrequency());
                if (outputs!=null) outputs.remove(tunnel);
            } else {
                var inputs = this.inputs.get(tunnel.getFrequency());
                if (inputs!=null) inputs.remove(tunnel);
            }

            this.updateTunnel(tunnel.getFrequency(), !tunnel.isOutput(), false);
        }
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        if (node.getOwner() instanceof P2PTunnelPart<?> tunnel) {
            if (tunnel instanceof MEP2PTunnelPart && !node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                return;
            }
            all.add(tunnel);
            all.forEach(P2PTunnelPart::update);
            // AELog.info( "add-" + (t.output ? "output: " : "input: ") + t.freq );

            if (tunnel.isOutput()) {
                this.outputs.computeIfAbsent(tunnel.getFrequency(), k->new ArrayList<>()).add(tunnel);
            } else {
                this.inputs.computeIfAbsent(tunnel.getFrequency(), k->new ArrayList<>()).add(tunnel);
            }

            this.updateTunnel(tunnel.getFrequency(), !tunnel.isOutput(), false);
        }
    }

    private void updateTunnel(short freq, boolean updateOutputs, boolean configChange) {
        if (updateOutputs) {
            var outputs = this.outputs.get(freq);
            if (outputs != null) {
                for (var p : outputs) {
                    if (configChange) {
                        p.onTunnelConfigChange();
                    }
                    p.onTunnelNetworkChange();
                }
            }
        }
        if (!updateOutputs) {
            var inputs = this.inputs.get(freq);
            if (inputs != null) {
                for (var p : inputs) {
                    if (configChange) {
                        p.onTunnelConfigChange();
                    }
                    p.onTunnelNetworkChange();
                }
            }
        }
    }

    public void updateFreq(P2PTunnelPart t, short newFrequency) {
        var outputs = this.outputs.get(t.getFrequency());
        if (outputs!=null) outputs.remove(t);
        var inputs = this.inputs.get(t.getFrequency());
        if (inputs!=null) inputs.remove(t);

        var oldFrequency = t.getFrequency();
        t.setFrequency(newFrequency);

        if (t.isOutput()) {
            this.outputs.computeIfAbsent(t.getFrequency(), k->new ArrayList<>()).add(t);
        } else {
            this.inputs.computeIfAbsent(t.getFrequency(), k->new ArrayList<>()).add(t);
        }

        if (oldFrequency != newFrequency) {
            this.updateTunnel(oldFrequency, true, true);
            this.updateTunnel(oldFrequency, false, true);
        }
        this.updateTunnel(newFrequency, true, true);
        this.updateTunnel(newFrequency, false, true);
        all.forEach(P2PTunnelPart::update);
    }

    public short newFrequency() {
        short newFrequency;
        int cycles = 0;

        do {
            newFrequency = (short) this.frequencyGenerator.nextInt(1 << 16);
            cycles++;
        } while (newFrequency == 0 || this.inputs.containsKey(newFrequency));

        if (cycles > 25) {
            AELog.debug("Generating a new P2P frequency '%1$d' took %2$d cycles", newFrequency, cycles);
        }

        return newFrequency;
    }

    public <T extends P2PTunnelPart<T>> List<T> getOutputs(short freq, Class<T> c) {
        // Check that a matching input exists for the requested type
        var input = this.inputs.get(freq);
        if (input == null) {
            return Collections.emptyList();
        } else {
            boolean isInstance = false;
            for (var p : input) {
                if (c.isInstance(p)) {
                    isInstance = true;
                    break;
                }
            }
            if (isInstance) {
                var outputs = this.outputs.get(freq);
                if (outputs == null) {
                    return Collections.emptyList();
                } else {
                    var list = new ArrayList<>(outputs.size());
                    for (var output : outputs) {
                        if (c.isInstance(output)) {
                            list.add(output);
                        }
                    }
                    return (List) ImmutableList.copyOf(list);
                }
            } else {
                return Collections.emptyList();
            }
        }
    }

    public static final  P2PTunnelPart[] EMPTY = new P2PTunnelPart[0];

    public <T extends P2PTunnelPart<T>> P2PTunnelPart[] getInputs(short freq, Class<T> c) {
        var inputs = this.inputs.get(freq);
        if (inputs == null) {
            return  EMPTY;
        } else {
            var list = new ArrayList<>(inputs.size());
            for (var input : inputs) {
                if (c.isInstance(input)) {
                    list.add(input);
                }
            }
            return list.toArray(EMPTY);
        }
    }
}
