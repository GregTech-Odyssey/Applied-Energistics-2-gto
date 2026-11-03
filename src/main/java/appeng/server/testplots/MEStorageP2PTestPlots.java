package appeng.server.testplots;

import static appeng.server.testplots.P2PPlotHelper.placeTunnel;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.SkyStoneTankBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.parts.p2p.FluidP2PTunnelPart;
import appeng.parts.p2p.ItemP2PTunnelPart;
import appeng.server.testworld.PlotBuilder;

/**
 * Tests that item and fluid P2P tunnels expose their storage as a direct {@code MEStorage}.
 */
public class MEStorageP2PTestPlots {

    @TestPlot("p2p_item_me_storage")
    public static void item(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        placeTunnel(plot, AEParts.ITEM_P2P_TUNNEL);
        var inChest = origin.west().west();
        var outChest = origin.east().east();
        plot.chest(inChest, new ItemStack(Items.BEDROCK, 16));
        plot.chest(outChest);

        plot.test(helper -> {
            var input = helper.getPart(origin.west(), Direction.WEST, ItemP2PTunnelPart.class);
            var output = helper.getPart(origin.east(), Direction.EAST, ItemP2PTunnelPart.class);
            var inserted = new AtomicLong(-1);
            var extracted = new AtomicLong(-1);
            helper.startSequence()
                    .thenWaitUntil(() -> {
                        helper.check(input.getFrequency() != 0, "Input not linked yet");
                        helper.check(output.getFrequency() == input.getFrequency(), "Frequencies differ");
                    })
                    .thenExecute(() -> {
                        var inputBus = (CableBusBlockEntity) input.getBlockEntity();
                        var outputBus = (CableBusBlockEntity) output.getBlockEntity();
                        var inputStorage = inputBus.getMEStorage(Direction.WEST);
                        var outputStorage = outputBus.getMEStorage(Direction.EAST);
                        inserted.set(inputStorage != null
                                ? inputStorage.insert(AEItemKey.of(Items.DIAMOND), 10, Actionable.MODULATE,
                                        IActionSource.empty())
                                : -1);
                        extracted.set(outputStorage != null
                                ? outputStorage.extract(AEItemKey.of(Items.BEDROCK), 8, Actionable.MODULATE,
                                        IActionSource.empty())
                                : -1);
                    })
                    .thenWaitUntil(() -> {
                        helper.check(inserted.get() == 10, "Inserted " + inserted.get() + " instead of 10 items");
                        helper.check(extracted.get() == 8, "Extracted " + extracted.get() + " instead of 8 items");
                        helper.check(
                                helper.countContainerContentAt(outChest).get(AEItemKey.of(Items.DIAMOND)) == 10,
                                "Output chest does not contain 10 diamonds");
                        helper.check(
                                helper.countContainerContentAt(inChest).get(AEItemKey.of(Items.BEDROCK)) == 8,
                                "Input chest does not contain 8 bedrock");
                    })
                    .thenSucceed();
        });
    }

    @TestPlot("p2p_fluid_me_storage")
    public static void fluid(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        placeTunnel(plot, AEParts.FLUID_P2P_TUNNEL);
        var inTankPos = origin.west().west();
        var outTankPos = origin.east().east();
        plot.block(inTankPos, AEBlocks.SKY_STONE_TANK);
        plot.block(outTankPos, AEBlocks.SKY_STONE_TANK);

        plot.test(helper -> {
            var input = helper.getPart(origin.west(), Direction.WEST, FluidP2PTunnelPart.class);
            var output = helper.getPart(origin.east(), Direction.EAST, FluidP2PTunnelPart.class);
            var filled = new AtomicLong(-1);
            var drained = new AtomicLong(-1);
            var inTank = new AtomicReference<SkyStoneTankBlockEntity>();
            helper.startSequence()
                    .thenWaitUntil(() -> {
                        helper.check(input.getFrequency() != 0, "Input not linked yet");
                        helper.check(output.getFrequency() == input.getFrequency(), "Frequencies differ");
                    })
                    .thenExecute(() -> {
                        var inputBus = (CableBusBlockEntity) input.getBlockEntity();
                        var outputBus = (CableBusBlockEntity) output.getBlockEntity();
                        var waterKey = AEFluidKey.of(Fluids.WATER);
                        var inputStorage = inputBus.getMEStorage(Direction.WEST);
                        var outputStorage = outputBus.getMEStorage(Direction.EAST);
                        filled.set(inputStorage != null
                                ? inputStorage.insert(waterKey, 1000, Actionable.MODULATE, IActionSource.empty())
                                : -1);

                        inTank.set((SkyStoneTankBlockEntity) helper.getBlockEntity(inTankPos));
                        inTank.get().getStorage().fill(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME * 4),
                                IFluidHandler.FluidAction.EXECUTE);
                        drained.set(outputStorage != null
                                ? outputStorage.extract(waterKey, 2000, Actionable.MODULATE, IActionSource.empty())
                                : -1);
                    })
                    .thenWaitUntil(() -> {
                        var outTank = (SkyStoneTankBlockEntity) helper.getBlockEntity(outTankPos);
                        helper.check(filled.get() == 1000, "Filled " + filled.get() + " instead of 1000 mB");
                        helper.check(drained.get() == 2000, "Drained " + drained.get() + " instead of 2000 mB");
                        helper.check(
                                outTank.getStorage().getFluidAmount() == 1000,
                                "Output tank has " + outTank.getStorage().getFluidAmount() + " mB instead of 1000");
                        helper.check(
                                inTank.get().getStorage().getFluidAmount() == 2000,
                                "Input tank has " + inTank.get().getStorage().getFluidAmount()
                                        + " mB instead of 2000");
                    })
                    .thenSucceed();
        });
    }
}
