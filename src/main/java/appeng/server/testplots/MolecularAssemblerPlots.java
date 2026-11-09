package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.AutoCraftingMenu;
import appeng.server.testworld.PlotBuilder;

public final class MolecularAssemblerPlots {

    private static final BlockPos ASSEMBLER_POS = new BlockPos(0, 1, 0);

    private MolecularAssemblerPlots() {
    }

    /**
     * A push whose ingredients do not actually assemble must be rejected on arrival. Accepting one leaves the assembler
     * with a full grid and a plan it can never complete, so {@code hasMats()} stays false and the node sleeps forever:
     * full ingredients, no crafting, and nothing to wake it up again.
     */
    @TestPlot("molecular_assembler_rejects_unassemblable_push")
    public static void rejectsUnassemblablePush(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.blockEntity(ASSEMBLER_POS, AEBlocks.MOLECULAR_ASSEMBLER, assembler -> {
            var level = assembler.getLevel();
            var pattern = encodeLogToPlanks(level);

            // Must be refused: the ingredients do not assemble, and accepting them would strand the grid.
            if (assembler.pushPattern(pattern, dirtTable(), Direction.UP)) {
                throw new IllegalStateException("assembler accepted a push whose ingredients cannot assemble");
            }

            // Still usable for real work once junk is refused.
            if (!assembler.pushPattern(pattern, logTable(), Direction.UP)) {
                throw new IllegalStateException("assembler refused the good push after refusing the junk one");
            }
        });
        plot.test(helper -> helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    var assembler = (MolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER_POS);
                    // A completed job clears the grid; the leftover dirt from the refused push must not be there.
                    for (int slot = 0; slot < 9; slot++) {
                        var stack = assembler.getInternalInventory().getStackInSlot(slot);
                        helper.check(stack.isEmpty() || stack.is(Items.OAK_PLANKS) || stack.is(Items.OAK_LOG),
                                "slot " + slot + " holds junk from the refused push: " + stack);
                    }
                })
                .thenSucceed());
    }

    /**
     * Loading a save can restore a plan next to a grid it can no longer assemble. Nothing would ever wake the node
     * again, and pushPattern() refuses new work while the grid is occupied, so the plan has to be dropped on load.
     */
    @TestPlot("molecular_assembler_recovers_from_stale_plan_on_load")
    public static void recoversFromStalePlanOnLoad(PlotBuilder plot) {
        plot.creativeEnergyCell("0 0 0");
        plot.blockEntity(ASSEMBLER_POS, AEBlocks.MOLECULAR_ASSEMBLER, assembler -> {
        });
        plot.test(helper -> helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    var assembler = (MolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER_POS);
                    var pattern = encodeLogToPlanks(assembler.getLevel());

                    // The tag a real save would hold: a plan for oak logs next to a grid that is no longer logs.
                    var tag = new CompoundTag();
                    var patternTag = new CompoundTag();
                    pattern.getDefinition().toStack().save(patternTag);
                    tag.put("myPlan", patternTag);
                    tag.putInt("pushDirection", Direction.UP.ordinal());
                    assembler.loadTag(tag);
                    assembler.getInternalInventory().setItemDirect(0, new ItemStack(Items.DIRT));

                    // Asking for tick info is what happens when the node comes back online: a stale plan must go.
                    assembler.getTickingRequest(helper.getGridNode(ASSEMBLER_POS));
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    var assembler = (MolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER_POS);
                    helper.check(assembler.getCurrentPattern() == null,
                            "assembler kept a restored plan for a grid it cannot assemble");
                    for (int slot = 0; slot < 9; slot++) {
                        helper.check(assembler.getInternalInventory().getStackInSlot(slot).isEmpty(),
                                "slot " + slot + " still occupied after stale plan recovery");
                    }
                })
                .thenSucceed());
    }

    @TestPlot("molecular_assembler_recovers_fluid_from_stale_plan")
    public static void recoversFluidFromStalePlan(PlotBuilder plot) {
        var providerPos = ASSEMBLER_POS.above();
        var chestPos = ASSEMBLER_POS.north();
        plot.creativeEnergyCell("0 0 0");
        plot.block(ASSEMBLER_POS, AEBlocks.MOLECULAR_ASSEMBLER);
        plot.block(providerPos, AEBlocks.PATTERN_PROVIDER);
        plot.block(chestPos, Blocks.CHEST);
        plot.test(helper -> helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
                    var returnInv = provider.getLogic().getReturnInv();
                    // Leave one return slot so its fluid capacity also limits the total accepted amount.
                    for (int slot = 0; slot < returnInv.size() - 1; slot++) {
                        returnInv.setStack(slot, new GenericStack(AEItemKey.of(Items.STONE), 1));
                    }
                    returnInv.setCapacity(AEKeyTypes.FLUIDS, 0);

                    var assembler = (MolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER_POS);
                    var pattern = encodeLogToPlanks(assembler.getLevel());
                    var tag = new CompoundTag();
                    tag.put("myPlan", pattern.getDefinition().toStack().save(new CompoundTag()));
                    tag.putInt("pushDirection", Direction.UP.ordinal());
                    assembler.loadTag(tag);
                    assembler.getInternalInventory().setItemDirect(0,
                            GenericStack.wrapInItemStack(AEFluidKey.of(Fluids.WATER), 1000));
                    assembler.getTickingRequest(helper.getGridNode(ASSEMBLER_POS));
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    var assembler = (MolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER_POS);
                    var held = GenericStack.unwrapItemStack(assembler.getInternalInventory().getStackInSlot(9));
                    helper.check(held != null && held.what() == AEFluidKey.of(Fluids.WATER) && held.amount() == 1000,
                            "blocked fluid return did not retain all 1000 mB in the assembler");
                    var chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
                    helper.check(chest.isEmpty(), "fluid return inserted a wrapper into an item-only chest");

                    var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
                    provider.getLogic().getReturnInv().setCapacity(AEKeyTypes.FLUIDS, 400);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
                    var returnInv = provider.getLogic().getReturnInv();
                    var returned = returnInv.getStack(returnInv.size() - 1);
                    helper.check(returned != null && returned.what() == AEFluidKey.of(Fluids.WATER)
                            && returned.amount() == 400, "partial return did not insert exactly 400 mB of water");

                    var assembler = (MolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER_POS);
                    var saved = assembler.saveWithoutMetadata();
                    assembler.loadTag(saved);
                    assembler.getTickingRequest(helper.getGridNode(ASSEMBLER_POS));
                    var held = GenericStack.unwrapItemStack(assembler.getInternalInventory().getStackInSlot(9));
                    helper.check(held != null && held.what() == AEFluidKey.of(Fluids.WATER) && held.amount() == 600,
                            "partial return did not preserve the remaining 600 mB across save/load");
                    returnInv.setCapacity(AEKeyTypes.FLUIDS, 1000);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
                    var returnInv = provider.getLogic().getReturnInv();
                    var returned = returnInv.getStack(returnInv.size() - 1);
                    helper.check(returned != null && returned.what() == AEFluidKey.of(Fluids.WATER)
                            && returned.amount() == 1000, "retry did not return exactly 1000 mB of water in total");
                    var chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
                    helper.check(chest.isEmpty(), "fluid return inserted a wrapper into an item-only chest");
                    var assembler = (MolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER_POS);
                    helper.check(assembler.getCurrentPattern() == null, "recovery kept the stale plan");
                    helper.check(assembler.getInternalInventory().isEmpty(), "recovery left ingredients behind");
                    helper.check(
                            assembler.pushPattern(encodeLogToPlanks(assembler.getLevel()), logTable(), Direction.UP),
                            "assembler refused a valid push after returning the fluid");
                })
                .thenSucceed());
    }

    private static IPatternDetails encodeLogToPlanks(Level level) {
        var sparseInputs = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            sparseInputs[i] = ItemStack.EMPTY;
        }
        sparseInputs[0] = new ItemStack(Items.OAK_LOG);

        var container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        for (int i = 0; i < 9; i++) {
            container.setItem(i, sparseInputs[i]);
        }
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level)
                .orElseThrow(() -> new RuntimeException("no oak log recipe"));

        var encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, sparseInputs,
                new ItemStack(Items.OAK_PLANKS, 4), false, false);
        return PatternDetailsHelper.decodePattern(encoded, level);
    }

    private static KeyCounter[] logTable() {
        var table = new KeyCounter[] { new KeyCounter() };
        table[0].add(AEItemKey.of(Items.OAK_LOG), 1);
        return table;
    }

    private static KeyCounter[] dirtTable() {
        var table = new KeyCounter[] { new KeyCounter() };
        table[0].add(AEItemKey.of(Items.DIRT), 1);
        return table;
    }
}
