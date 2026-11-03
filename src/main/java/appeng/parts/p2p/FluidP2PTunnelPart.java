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

package appeng.parts.p2p;

import java.util.List;

import com.google.common.primitives.Ints;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;

public class FluidP2PTunnelPart extends CapabilityP2PTunnelPart<FluidP2PTunnelPart, IFluidHandler> {

    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_fluids"));
    private static final IFluidHandler NULL_FLUID_HANDLER = new NullFluidHandler();

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    public FluidP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem, ForgeCapabilities.FLUID_HANDLER);
        inputHandler = new InputFluidHandler();
        outputHandler = new OutputFluidHandler();
        emptyHandler = NULL_FLUID_HANDLER;
        inputStorage = new InputStorage();
        outputStorage = new OutputStorage();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    private class InputStorage extends P2PStorage {
        InputStorage() {
            super(AEKeyTypes.FLUIDS);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (isOutput() || !(what instanceof AEFluidKey fluidKey) || amount <= 0) {
                return 0;
            }

            return inputHandler.fill(fluidKey.toStack(Ints.saturatedCast(amount)), mode.getFluidAction());
        }
    }

    private class OutputStorage extends P2PStorage {
        OutputStorage() {
            super(AEKeyTypes.FLUIDS);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!isOutput() || !(what instanceof AEFluidKey fluidKey) || amount <= 0) {
                return 0;
            }

            var drained = outputHandler.drain(fluidKey.toStack(Ints.saturatedCast(amount)), mode.getFluidAction());
            return drained.getAmount();
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (!isOutput()) {
                return;
            }

            int tanks = outputHandler.getTanks();
            for (int tank = 0; tank < tanks; tank++) {
                var stack = outputHandler.getFluidInTank(tank);
                if (!stack.isEmpty()) {
                    out.add(AEFluidKey.of(stack), stack.getAmount());
                }
            }
        }
    }

    private class InputFluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int total = 0;

            var outputs = FluidP2PTunnelPart.this.getOutputs();
            final int outputTunnels = outputs.size();
            final int amount = resource.getAmount();

            if (outputTunnels == 0 || amount == 0) {
                return 0;
            }

            final int amountPerOutput = amount / outputTunnels;
            int overflow = amountPerOutput == 0 ? amount : amount % amountPerOutput;

            for (FluidP2PTunnelPart target : outputs) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final IFluidHandler output = capabilityGuard.get();
                    final int toSend = amountPerOutput + overflow;
                    final FluidStack fillWithFluidStack = resource.copy();
                    fillWithFluidStack.setAmount(toSend);

                    final int received = output.fill(fillWithFluidStack, action);

                    overflow = toSend - received;
                    total += received;
                }
            }

            if (action == FluidAction.EXECUTE) {
                deductTransportCost(total, AEKeyTypes.FLUIDS);
            }

            return total;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }

    }

    private class OutputFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().getTanks();
            }
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().getFluidInTank(tank);
            }
        }

        @Override
        public int getTankCapacity(int tank) {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().getTankCapacity(tank);
            }
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            try (CapabilityGuard input = getInputCapability()) {
                return input.get().isFluidValid(tank, stack);
            }
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            try (CapabilityGuard input = getInputCapability()) {
                FluidStack result = input.get().drain(resource, action);

                if (action.execute()) {
                    deductTransportCost(result.getAmount(), AEKeyTypes.FLUIDS);
                }

                return result;
            }
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            try (CapabilityGuard input = getInputCapability()) {
                FluidStack result = input.get().drain(maxDrain, action);

                if (action.execute()) {
                    deductTransportCost(result.getAmount(), AEKeyTypes.FLUIDS);
                }

                return result;
            }
        }
    }

    private static class NullFluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 0;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

}
