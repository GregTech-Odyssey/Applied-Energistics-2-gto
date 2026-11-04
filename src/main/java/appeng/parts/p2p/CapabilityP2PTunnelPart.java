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

package appeng.parts.p2p;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.KeyVisitor;
import appeng.api.storage.MEStorage;
import appeng.capabilities.Capabilities;
import appeng.hooks.ticking.TickHandler;

/**
 * Base class for simple capability-based p2p tunnels. Don't forget to set the 3 handlers in the constructor of the
 * child class!
 */
public abstract class CapabilityP2PTunnelPart<P extends CapabilityP2PTunnelPart<P, A>, A> extends P2PTunnelPart<P> {
    private final Capability<A> capability;
    // Prevents recursive block updates.
    private boolean inBlockUpdate = false;
    // Prevents recursive access to the adjacent capability in case P2P input/output faces touch
    private int accessDepth = 0;
    private final CapabilityGuard capabilityGuard = new CapabilityGuard();
    private final EmptyCapabilityGuard emptyCapabilityGuard = new EmptyCapabilityGuard();
    protected A inputHandler;
    protected A outputHandler;
    protected A emptyHandler;
    protected MEStorage inputStorage = new InputMEStorage();
    protected MEStorage outputStorage = new OutputMEStorage();

    public CapabilityP2PTunnelPart(IPartItem<?> partItem, Capability<A> capability) {
        super(partItem);
        this.capability = capability;
    }

    @Override
    protected float getPowerDrainPerTick() {
        return 2.0f;
    }

    public final <T> LazyOptional<T> getCapability(Capability<T> capabilityClass) {
        if (capabilityClass == capability) {
            if (isOutput()) {
                return LazyOptional.of(() -> outputHandler).cast();
            } else {
                return LazyOptional.of(() -> inputHandler).cast();
            }
        }
        if (capabilityClass == Capabilities.STORAGE) {
            var storage = isOutput() ? outputStorage : inputStorage;
            if (storage != null) {
                return LazyOptional.of(() -> storage).cast();
            }
        }
        return LazyOptional.empty();
    }

    /**
     * Return the capability connected to this side of this P2P connection. If this method is called again on this
     * tunnel while the returned object has not been closed, further calls to {@link CapabilityGuard#get()} will return
     * a dummy capability.
     */
    protected final CapabilityGuard getAdjacentCapability() {
        accessDepth++;
        return capabilityGuard;
    }

    /**
     * Returns the capability attached to the input side of this tunnel's P2P connection. If this method is called again
     * on this tunnel while the returned object has not been closed, further calls to {@link CapabilityGuard#get()} will
     * return a dummy capability.
     */
    protected final CapabilityGuard getInputCapability() {
        P input = getInput();
        return input == null ? emptyCapabilityGuard : input.getAdjacentCapability();
    }

    protected class CapabilityGuard implements AutoCloseable {
        /**
         * Get the capability, or a null handler if not available. Use within the scope of the enclosing AdjCapability.
         */
        public A get() {
            if (accessDepth == 0) {
                throw new IllegalStateException("get was called after closing the wrapper");
            } else if (accessDepth == 1) {
                if (isActive()) {
                    var self = getBlockEntity();
                    var te = self.getLevel().getBlockEntity(getFacingPos());

                    if (te != null) {
                        return te.getCapability(capability, getSide().getOpposite())
                                .orElse(emptyHandler);
                    }
                }

                return emptyHandler;
            } else {
                // This capability is already in use (as the nesting is > 1), so we return an empty handler to prevent
                // infinite recursion.
                return emptyHandler;
            }
        }

        @Nullable
        public MEStorage getStorage() {
            if (accessDepth == 0) {
                throw new IllegalStateException("get was called after closing the wrapper");
            } else if (accessDepth == 1) {
                if (isActive()) {
                    var self = getBlockEntity();
                    var te = self.getLevel().getBlockEntity(getFacingPos());

                    if (te != null) {
                        return te.getCapability(Capabilities.STORAGE, getSide().getOpposite())
                                .orElse(null);
                    }
                }

                return null;
            } else {
                // This capability is already in use (as the nesting is > 1), so we return an empty handler to prevent
                // infinite recursion.
                return null;
            }
        }

        @Override
        public void close() {
            if (--accessDepth < 0) {
                throw new IllegalStateException("Close has been called multiple times");
            }
        }
    }

    /**
     * The position right in front of this P2P tunnel.
     */
    private BlockPos getFacingPos() {
        return getHost().getLocation().getPos().relative(getSide());
    }

    /**
     * This specialization is used when the tunnel is not connected.
     */
    protected class EmptyCapabilityGuard extends CapabilityGuard implements AutoCloseable {
        @Override
        public void close() {
        }

        @Override
        public A get() {
            return emptyHandler;
        }
    }

    // Send a block update on p2p status change, or any update on another endpoint.
    protected void sendBlockUpdate() {
        // Prevent recursive block updates.
        if (!inBlockUpdate) {
            inBlockUpdate = true;

            try {
                // getHost().notifyNeighbors() would queue a callback, but we want to do an update synchronously!
                // (otherwise we can't detect infinite recursion, it would just queue updates endlessly)
                getHost().notifyNeighborNow(getSide());
            } finally {
                inBlockUpdate = false;
            }
        }
    }

    @Override
    public void onTunnelNetworkChange() {
        // This might be invoked while the network is being unloaded and we don't want to send a block update then, so
        // we delay it until the next tick.
        TickHandler.INSTANCE.addCallable(getLevel(), () -> {
            if (getMainNode().isReady()) { // Check that the p2p tunnel is still there.
                sendBlockUpdate();
            }
        });
    }

    /**
     * Forward block updates from the attached tile's position to the other end of the tunnel. Required for TE's on the
     * other end to know that the available caps may have changed.
     */
    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        // We only care about block updates on the side this tunnel is facing
        if (!getFacingPos().equals(neighbor)) {
            return;
        }

        // Prevent recursive block updates.
        if (!inBlockUpdate) {
            inBlockUpdate = true;

            try {
                if (isOutput()) {
                    P input = getInput();

                    if (input != null) {
                        input.sendBlockUpdate();
                    }
                } else {
                    for (P output : getOutputs()) {
                        output.sendBlockUpdate();
                    }
                }
            } finally {
                inBlockUpdate = false;
            }
        }
    }

    private class InputMEStorage implements MEStorage {

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            long total = 0;
            var outputs = CapabilityP2PTunnelPart.this.getOutputs();
            final int outputTunnels = outputs.size();
            if (outputTunnels == 0 || amount == 0) {
                return 0;
            }
            final long amountPerOutput = amount / outputTunnels;
            long overflow = amountPerOutput == 0 ? amount : amount % amountPerOutput;
            for (var target : outputs) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final var output = capabilityGuard.getStorage();
                    if (output == null)
                        continue;
                    final long toSend = amountPerOutput + overflow;
                    final long received = output.insert(what, toSend, mode, source);
                    overflow = toSend - received;
                    total += received;
                }
            }
            return total;
        }

        @Override
        public Component getDescription() {
            return Component.empty();
        }
    }

    private class OutputMEStorage implements MEStorage {

        @org.jetbrains.annotations.Nullable
        private AvailableStacksCache cache;

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long total = 0;
            var inputs = CapabilityP2PTunnelPart.this.getInputs();
            final int inputTunnels = inputs.length;
            if (inputTunnels == 0 || amount == 0) {
                return 0;
            }
            final long amountPerOutput = amount / inputTunnels;
            long overflow = amountPerOutput == 0 ? amount : amount % amountPerOutput;
            for (var target : inputs) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final var input = capabilityGuard.getStorage();
                    if (input == null)
                        continue;
                    final long toSend = amountPerOutput + overflow;
                    final long received = input.extract(what, toSend, mode, source);
                    overflow = toSend - received;
                    total += received;
                }
            }
            return total;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            var inputs = CapabilityP2PTunnelPart.this.getInputs();
            final int outputTunnels = inputs.length;
            if (outputTunnels == 0) {
                return;
            }
            for (var target : inputs) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final var input = capabilityGuard.getStorage();
                    if (input == null)
                        continue;
                    input.getAvailableStacks(out);
                }
            }
        }

        @Override
        public boolean forEachKey(KeyVisitor visitor) {
            var inputs = CapabilityP2PTunnelPart.this.getInputs();
            final int outputTunnels = inputs.length;
            if (outputTunnels == 0) {
                return false;
            }
            for (var target : inputs) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final var input = capabilityGuard.getStorage();
                    if (input == null)
                        continue;
                    if (input.forEachKey(visitor)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public KeyCounter getAvailableStacks() {
            var cache = this.cache;
            if (cache == null) {
                this.cache = cache = new AvailableStacksCache(this::getAvailableStacks);
            }
            return cache.getAvailableStacksCache();
        }

        @Override
        public Component getDescription() {
            return null;
        }
    }
}
