package dev.emi.emi;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.config.EffectLocation;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.FluidUnit;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.handler.CookingRecipeHandler;
import dev.emi.emi.handler.CraftingRecipeHandler;
import dev.emi.emi.handler.InventoryRecipeHandler;
import dev.emi.emi.handler.StonecuttingRecipeHandler;
import appeng.mixins.emi.accessor.*;
import dev.emi.emi.recipe.*;
import dev.emi.emi.recipe.special.*;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.EmiTagKey;
import dev.emi.emi.stack.serializer.FluidEmiStackSerializer;
import dev.emi.emi.stack.serializer.ItemEmiStackSerializer;
import dev.emi.emi.stack.serializer.ListEmiIngredientSerializer;
import dev.emi.emi.stack.serializer.TagEmiIngredientSerializer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

@EmiEntrypoint
public class VanillaPlugin implements EmiPlugin {
    public static EmiRecipeCategory TAG = new EmiRecipeCategory(EmiPort.id("emi:tag"),
            EmiStack.of(Items.NAME_TAG), simplifiedRenderer(240, 208), EmiRecipeSorting.none());

    public static EmiRecipeCategory INGREDIENT = new EmiRecipeCategory(EmiPort.id("emi:ingredient"),
            EmiStack.of(Items.COMPASS), simplifiedRenderer(240, 208));
    public static EmiRecipeCategory RESOLUTION = new EmiRecipeCategory(EmiPort.id("emi:resolution"),
            EmiStack.of(Items.COMPASS), simplifiedRenderer(240, 208));

    static {
        CRAFTING = new EmiRecipeCategory(EmiPort.id("minecraft:crafting"),
                EmiStack.of(Items.CRAFTING_TABLE), simplifiedRenderer(240, 240),
                EmiRecipeSorting.compareOutputThenInput());
        SMELTING = new EmiRecipeCategory(EmiPort.id("minecraft:smelting"),
                EmiStack.of(Items.FURNACE), simplifiedRenderer(224, 240), EmiRecipeSorting.compareOutputThenInput());
        BLASTING = new EmiRecipeCategory(EmiPort.id("minecraft:blasting"),
                EmiStack.of(Items.BLAST_FURNACE), simplifiedRenderer(208, 240),
                EmiRecipeSorting.compareOutputThenInput());
        SMOKING = new EmiRecipeCategory(EmiPort.id("minecraft:smoking"),
                EmiStack.of(Items.SMOKER), simplifiedRenderer(192, 240), EmiRecipeSorting.compareOutputThenInput());
        CAMPFIRE_COOKING = new EmiRecipeCategory(EmiPort.id("minecraft:campfire_cooking"),
                EmiStack.of(Items.CAMPFIRE), simplifiedRenderer(176, 240), EmiRecipeSorting.compareOutputThenInput());
        STONECUTTING = new EmiRecipeCategory(EmiPort.id("minecraft:stonecutting"),
                EmiStack.of(Items.STONECUTTER), simplifiedRenderer(160, 240),
                EmiRecipeSorting.compareInputThenOutput());
        SMITHING = new EmiRecipeCategory(EmiPort.id("minecraft:smithing"),
                EmiStack.of(Items.SMITHING_TABLE), simplifiedRenderer(240, 224),
                EmiRecipeSorting.compareInputThenOutput());
        ANVIL_REPAIRING = new EmiRecipeCategory(EmiPort.id("emi:anvil_repairing"),
                EmiStack.of(Items.ANVIL), simplifiedRenderer(240, 224), EmiRecipeSorting.none());
        GRINDING = new EmiRecipeCategory(EmiPort.id("emi:grinding"),
                EmiStack.of(Items.GRINDSTONE), simplifiedRenderer(192, 224), EmiRecipeSorting.none());
        BREWING = new EmiRecipeCategory(EmiPort.id("minecraft:brewing"),
                EmiStack.of(Items.BREWING_STAND), simplifiedRenderer(224, 224), EmiRecipeSorting.none());
        WORLD_INTERACTION = new EmiRecipeCategory(EmiPort.id("emi:world_interaction"),
                EmiStack.of(Items.GRASS_BLOCK), simplifiedRenderer(208, 224), EmiRecipeSorting.none());
        EmiRenderable flame = (matrices, x, y, delta) -> {
            EmiTexture.FULL_FLAME.render(matrices, x + 1, y + 1, delta);
        };
        FUEL = new EmiRecipeCategory(EmiPort.id("emi:fuel"), flame, flame,
                EmiRecipeSorting.compareInputThenOutput());
        COMPOSTING = new EmiRecipeCategory(EmiPort.id("emi:composting"), EmiStack.of(Items.COMPOSTER),
                EmiStack.of(Items.COMPOSTER), EmiRecipeSorting.compareInputThenOutput());
        INFO = new EmiRecipeCategory(EmiPort.id("emi:info"),
                EmiStack.of(Items.WRITABLE_BOOK), simplifiedRenderer(208, 224), EmiRecipeSorting.none());
    }

    @Override
    public void initialize(EmiInitRegistry registry) {
        registry.addIngredientSerializer(ItemEmiStack.class, new ItemEmiStackSerializer());
        registry.addIngredientSerializer(FluidEmiStack.class, new FluidEmiStackSerializer());
        registry.addIngredientSerializer(TagEmiIngredient.class, new TagEmiIngredientSerializer());
        registry.addIngredientSerializer(ListEmiIngredient.class, new ListEmiIngredientSerializer());

        registry.addRegistryAdapter(
                EmiRegistryAdapter.simple(Item.class, dev.emi.emi.EmiPort.getItemRegistry(), EmiStack::of));
        registry.addRegistryAdapter(
                EmiRegistryAdapter.simple(Fluid.class, dev.emi.emi.EmiPort.getFluidRegistry(), EmiStack::of));
    }

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(CRAFTING);
        registry.addCategory(SMELTING);
        registry.addCategory(BLASTING);
        registry.addCategory(SMOKING);
        registry.addCategory(CAMPFIRE_COOKING);
        registry.addCategory(STONECUTTING);
        registry.addCategory(SMITHING);
        registry.addCategory(ANVIL_REPAIRING);
        registry.addCategory(GRINDING);
        registry.addCategory(BREWING);
        registry.addCategory(WORLD_INTERACTION);
        registry.addCategory(FUEL);
        registry.addCategory(COMPOSTING);
        registry.addCategory(INFO);
        registry.addCategory(TAG);
        registry.addCategory(INGREDIENT);
        registry.addCategory(RESOLUTION);

        registry.addWorkstation(CRAFTING, EmiStack.of(Items.CRAFTING_TABLE));
        registry.addWorkstation(SMELTING, EmiStack.of(Items.FURNACE));
        registry.addWorkstation(BLASTING, EmiStack.of(Items.BLAST_FURNACE));
        registry.addWorkstation(SMOKING, EmiStack.of(Items.SMOKER));
        registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.CAMPFIRE));
        registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.SOUL_CAMPFIRE));
        registry.addWorkstation(STONECUTTING, EmiStack.of(Items.STONECUTTER));
        registry.addWorkstation(SMITHING, EmiStack.of(Items.SMITHING_TABLE));
        registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.ANVIL));
        registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.CHIPPED_ANVIL));
        registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.DAMAGED_ANVIL));
        registry.addWorkstation(BREWING, EmiStack.of(Items.BREWING_STAND));
        registry.addWorkstation(GRINDING, EmiStack.of(Items.GRINDSTONE));
        registry.addWorkstation(COMPOSTING, EmiStack.of(Items.COMPOSTER));

        registry.addRecipeHandler(null, new InventoryRecipeHandler());
        registry.addRecipeHandler(MenuType.CRAFTING, new CraftingRecipeHandler());
        registry.addRecipeHandler(MenuType.FURNACE, new CookingRecipeHandler<>(SMELTING));
        registry.addRecipeHandler(MenuType.BLAST_FURNACE, new CookingRecipeHandler<>(BLASTING));
        registry.addRecipeHandler(MenuType.SMOKER, new CookingRecipeHandler<>(SMOKING));
        registry.addRecipeHandler(MenuType.STONECUTTER, new StonecuttingRecipeHandler());

        registry.addExclusionArea(CreativeModeInventoryScreen.class, (screen, consumer) -> {
            int left = ((HandledScreenAccessor) screen).getX();
            int top = ((HandledScreenAccessor) screen).getY();
            int width = ((HandledScreenAccessor) screen).getBackgroundWidth();
            int bottom = top + ((HandledScreenAccessor) screen).getBackgroundHeight();
            consumer.accept(new Bounds(left, top - 28, width, 28));
            consumer.accept(new Bounds(left, bottom, width, 28));
        });

        registry.addGenericExclusionArea((screen, consumer) -> {
            if (EmiConfig.effectLocation != EffectLocation.HIDDEN
                    && screen instanceof EffectRenderingInventoryScreen<?> inv) {
                Minecraft client = Minecraft.getInstance();
                Collection<MobEffectInstance> collection = client.player.getActiveEffects();
                if (!collection.isEmpty()) {
                    int k = 33;
                    if (collection.size() > 5) {
                        k = 132 / (collection.size() - 1);
                    }
                    int right = ((HandledScreenAccessor) inv).getX()
                            + ((HandledScreenAccessor) inv).getBackgroundWidth() + 2;
                    int rightWidth = inv.width - right;
                    if (rightWidth >= 32) {
                        int top = ((HandledScreenAccessor) inv).getY();
                        int height = (collection.size() - 1) * k + 32;
                        int left, width;
                        if (EmiConfig.effectLocation == EffectLocation.TOP) {
                            int size = collection.size();
                            top = ((HandledScreenAccessor) inv).getY() - 34;
                            if (((Object) screen) instanceof CreativeModeInventoryScreen) {
                                top -= 28;
                                top -= 22;
                            }
                            int xOff = 34;
                            if (size == 1) {
                                xOff = 122;
                            } else if (size > 5) {
                                xOff = (((HandledScreenAccessor) inv).getBackgroundWidth() - 32) / (size - 1);
                            }
                            width = Math.max(122, (size - 1) * xOff + 32);
                            left = ((HandledScreenAccessor) inv).getX()
                                    + (((HandledScreenAccessor) inv).getBackgroundWidth() - width) / 2;
                            height = 32;
                        } else {
                            left = switch (EmiConfig.effectLocation) {
                                case LEFT_COMPRESSED -> ((HandledScreenAccessor) inv).getX() - 2 - 32;
                                case LEFT -> ((HandledScreenAccessor) inv).getX() - 2 - 120;
                                default -> right;
                            };
                            width = switch (EmiConfig.effectLocation) {
                                case LEFT, RIGHT -> 120;
                                case LEFT_COMPRESSED, RIGHT_COMPRESSED -> 32;
                                default -> 32;
                            };
                        }
                        consumer.accept(new Bounds(left, top, width, height));
                    }
                }
            }
        });

        Comparison potionComparison = Comparison.compareData(stack -> PotionUtils.getPotion(stack.getNbt()));

        registry.setDefaultComparison(Items.POTION, potionComparison);
        registry.setDefaultComparison(Items.SPLASH_POTION, potionComparison);
        registry.setDefaultComparison(Items.LINGERING_POTION, potionComparison);
        registry.setDefaultComparison(Items.TIPPED_ARROW, potionComparison);
        registry.setDefaultComparison(Items.ENCHANTED_BOOK, dev.emi.emi.EmiPort.compareStrict());

        var hiddenTag = EmiTagKey.of(EmiPort.getItemRegistry(), EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
        var hiddenItems = new ReferenceOpenHashSet<Item>(hiddenTag.getList());
        var disabled = EmiPort.getDisabledItems().iterator();
        while (disabled.hasNext())
            hiddenItems.add(disabled.next());
        var dyeableItems = new ObjectArrayList<Item>();
        for (var item : EmiPort.getItemRegistry())
            if (item instanceof DyeableLeatherItem)
                dyeableItems.add(item);

        for (CraftingRecipe recipe : getRecipes(registry, RecipeType.CRAFTING)) {
            ResourceLocation id = dev.emi.emi.EmiPort.getId(recipe);
            if (recipe instanceof MapExtendingRecipe map) {
                EmiStack paper = EmiStack.of(Items.PAPER);
                addRecipeSafe(registry, () -> new EmiCraftingRecipe(List.of(
                        paper, paper, paper, paper,
                        EmiStack.of(Items.FILLED_MAP),
                        paper, paper, paper, paper),
                        EmiStack.of(Items.FILLED_MAP),
                        id, false), recipe);
            } else if (recipe instanceof ShapedRecipe shaped && recipe.canCraftInDimensions(3, 3)) {
                addRecipeSafe(registry, () -> new EmiShapedRecipe(shaped), recipe);
            } else if (recipe instanceof ShapelessRecipe shapeless && recipe.canCraftInDimensions(3, 3)) {
                addRecipeSafe(registry, () -> new EmiShapelessRecipe(shapeless), recipe);
            } else if (recipe instanceof ArmorDyeRecipe dye) {
                for (Item i : dyeableItems) {
                    if (!hiddenItems.contains(i)) {
                        addRecipeSafe(registry, () -> new EmiArmorDyeRecipe(i,
                                synthetic("crafting/dying", dev.emi.emi.EmiUtil.subId(i))), recipe);
                    }
                }
            } else if (recipe instanceof SuspiciousStewRecipe stew) {
                addRecipeSafe(registry, () -> new EmiSuspiciousStewRecipe(id), recipe);
            } else if (recipe instanceof ShulkerBoxColoring shulker) {
                for (DyeColor dye : DyeColor.values()) {
                    DyeItem dyeItem = DyeItem.byColor(dye);
                    ResourceLocation sid = synthetic("crafting/shulker_box_dying", dev.emi.emi.EmiUtil.subId(dyeItem));
                    addRecipeSafe(registry, () -> new EmiCraftingRecipe(
                            List.of(EmiStack.of(Items.SHULKER_BOX), EmiStack.of(dyeItem)),
                            EmiStack.of(ShulkerBoxBlock.getColoredItemStack(dye)), sid), recipe);
                }
            } else if (recipe instanceof ShieldDecorationRecipe shield) {
                addRecipeSafe(registry, () -> new EmiBannerShieldRecipe(id), recipe);
            } else if (recipe instanceof BookCloningRecipe book) {
                addRecipeSafe(registry, () -> new EmiBookCloningRecipe(id), recipe);
            } else if (recipe instanceof TippedArrowRecipe tipped) {
                dev.emi.emi.EmiPort.getPotionRegistry().holders().forEach(entry -> {
                    if (entry.value() == Potions.EMPTY) {
                        return;
                    }
                    EmiStack arrow = EmiStack.of(Items.ARROW);
                    addRecipeSafe(registry, () -> new EmiCraftingRecipe(List.of(
                            arrow, arrow, arrow, arrow,
                            EmiStack.of(EmiPort.setPotion(new ItemStack(Items.LINGERING_POTION),
                                    entry.value())),
                            arrow, arrow, arrow, arrow),
                            EmiStack.of(
                                    dev.emi.emi.EmiPort.setPotion(new ItemStack(Items.TIPPED_ARROW, 8), entry.value())),
                            synthetic("crafting/tipped_arrow",
                                    dev.emi.emi.EmiUtil
                                            .subId(EmiPort.getPotionRegistry().getKey(entry.value()))),
                            false), recipe);
                });
            } else if (recipe instanceof FireworkStarRecipe star) {
                addRecipeSafe(registry, () -> new EmiFireworkStarRecipe(id), recipe);
            } else if (recipe instanceof FireworkStarFadeRecipe star) {
                addRecipeSafe(registry, () -> new EmiFireworkStarFadeRecipe(id), recipe);
            } else if (recipe instanceof FireworkRocketRecipe rocket) {
                addRecipeSafe(registry, () -> new EmiFireworkRocketRecipe(id), recipe);
            } else if (recipe instanceof BannerDuplicateRecipe banner) {
                for (Item i : EmiBannerDuplicateRecipe.BANNERS) {
                    if (!hiddenItems.contains(i)) {
                        addRecipeSafe(registry, () -> new EmiBannerDuplicateRecipe(i,
                                synthetic("crafting/banner_copying", dev.emi.emi.EmiUtil.subId(i))), recipe);
                    }
                }
            } else if (recipe instanceof RepairItemRecipe tool) {
                for (Item i : EmiRepairItemRecipe.TOOLS) {
                    if (!hiddenItems.contains(i)) {
                        addRecipeSafe(registry, () -> new EmiRepairItemRecipe(i,
                                synthetic("crafting/repairing", dev.emi.emi.EmiUtil.subId(i))), recipe);
                    }
                }
            } else if (recipe instanceof MapCloningRecipe map) {
                addRecipeSafe(registry, () -> new EmiMapCloningRecipe(id), recipe);
            } else if (!(recipe instanceof CustomRecipe)) {
                try {
                    if (!recipe.getIngredients().isEmpty() && !dev.emi.emi.EmiPort.getOutput(recipe).isEmpty()
                            && recipe.canCraftInDimensions(3, 3)) {
                        boolean shapeless = recipe.canCraftInDimensions(1, recipe.getIngredients().size())
                                && recipe.canCraftInDimensions(recipe.getIngredients().size(), 1);
                        List<EmiIngredient> input;
                        if (shapeless) {
                            var ingredients = recipe.getIngredients();
                            var converted = new ObjectArrayList<EmiIngredient>(ingredients.size());
                            for (var ingredient : ingredients)
                                converted.add(EmiIngredient.of(ingredient));
                            input = converted;
                        } else {
                            int width = recipe.canCraftInDimensions(2, 3) ? recipe.canCraftInDimensions(1, 3) ? 1 : 2
                                    : 3;
                            input = Lists.newArrayList();
                            for (int i = 0; i < recipe.getIngredients().size(); i++) {
                                input.add(EmiIngredient.of(recipe.getIngredients().get(i)));
                                if ((i + 1) % width == 0) {
                                    for (int j = width; j < 3; j++) {
                                        input.add(EmiStack.EMPTY);
                                    }
                                }
                            }
                        }
                        EmiShapedRecipe.setRemainders(input, recipe);
                        addRecipeSafe(registry, () -> new EmiCraftingRecipe(input,
                                EmiStack.of(EmiPort.getOutput(recipe)), id, shapeless));
                    }
                } catch (Exception e) {
                    EmiReloadLog.warn("Exception when parsing vanilla crafting recipe " + id, e);
                }
            }
        }

        for (SmeltingRecipe recipe : getRecipes(registry, RecipeType.SMELTING)) {
            addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, SMELTING, 1, false), recipe);
        }
        for (BlastingRecipe recipe : getRecipes(registry, RecipeType.BLASTING)) {
            addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, BLASTING, 2, false), recipe);
        }
        for (SmokingRecipe recipe : getRecipes(registry, RecipeType.SMOKING)) {
            addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, SMOKING, 2, false), recipe);
        }
        for (CampfireCookingRecipe recipe : getRecipes(registry, RecipeType.CAMPFIRE_COOKING)) {
            addRecipeSafe(registry, () -> new EmiCookingRecipe(recipe, CAMPFIRE_COOKING, 1, true), recipe);
        }
        for (SmithingRecipe recipe : getRecipes(registry, RecipeType.SMITHING)) {
            // addRecipeSafe(registry, () -> new EmiSmithingRecipe(recipe), recipe);
            Minecraft client = Minecraft.getInstance();
            if (recipe instanceof SmithingTransformRecipeAccessor stra) {
                addRecipeSafe(registry,
                        () -> new EmiSmithingRecipe(EmiIngredient.of(stra.getTemplate()),
                                EmiIngredient.of(stra.getBase()),
                                EmiIngredient.of(stra.getAddition()),
                                EmiStack.of(EmiPort.getOutput(recipe)), dev.emi.emi.EmiPort.getId(recipe)),
                        recipe);
            } else if (recipe instanceof SmithingTrimRecipeAccessor stra) {
                addRecipeSafe(registry,
                        () -> new EmiSmithingTrimRecipe(EmiIngredient.of(stra.getTemplate()),
                                EmiIngredient.of(stra.getBase()),
                                EmiIngredient.of(stra.getAddition()),
                                EmiStack.of(EmiPort.getOutput(recipe)), recipe),
                        recipe);
            }
        }
        for (StonecutterRecipe recipe : getRecipes(registry, RecipeType.STONECUTTING)) {
            addRecipeSafe(registry, () -> new EmiStonecuttingRecipe(recipe), recipe);
        }

        safely("repair", () -> addRepair(registry, hiddenItems));
        safely("brewing", () -> EmiForgePlatform.addBrewingRecipes(registry));
        safely("world interaction", () -> addWorldInteraction(registry, hiddenItems, dyeableItems));
        safely("fuel", () -> addFuel(registry, hiddenItems));
        safely("composting", () -> addComposting(registry, hiddenItems));

        for (EmiTagKey<?> key : EmiTags.TAGS) {
            if (new TagEmiIngredient(key.raw(), 1).getEmiStacks().size() > 1) {
                addRecipeSafe(registry, () -> new EmiTagRecipe(key.raw()));
            }
        }
    }

    private static void addRepair(EmiRegistry registry, ReferenceOpenHashSet<Item> hiddenItems) {
        List<Enchantment> targetedEnchantments = Lists.newArrayList();
        List<Enchantment> universalEnchantments = Lists.newArrayList();
        for (Enchantment enchantment : dev.emi.emi.EmiPort.getEnchantmentRegistry()) {
            try {
                if (enchantment.canEnchant(ItemStack.EMPTY)) {
                    universalEnchantments.add(enchantment);
                    continue;
                }
            } catch (Throwable t) {
            }
            targetedEnchantments.add(enchantment);
        }
        for (Item i : dev.emi.emi.EmiPort.getItemRegistry()) {
            if (hiddenItems.contains(i)) {
                continue;
            }
            try {
                if (i.getMaxDamage() > 0) {
                    Ingredient repairIngredient = null;
                    if (i instanceof ArmorItem ai && ai.getMaterial() != null)
                        repairIngredient = ai.getMaterial().getRepairIngredient();
                    else if (i instanceof TieredItem ti)
                        repairIngredient = ti.getTier().getRepairIngredient();
                    if (repairIngredient != null && !repairIngredient.isEmpty()) {
                        var resource = repairIngredient;
                        ResourceLocation id = synthetic("anvil/repairing/material",
                                dev.emi.emi.EmiUtil.subId(i) + "/" + dev.emi.emi.EmiUtil
                                        .subId(resource.getItems()[0].getItem()));
                        addRecipeSafe(registry, () -> new EmiAnvilRecipe(EmiStack.of(i),
                                EmiIngredient.of(resource), id));
                    }
                }
                if (i.canBeDepleted()) {
                    addRecipeSafe(registry, () -> new EmiAnvilRepairItemRecipe(i,
                            synthetic("anvil/repairing/tool", dev.emi.emi.EmiUtil.subId(i))));
                    addRecipeSafe(registry, () -> new EmiGrindstoneRecipe(i,
                            synthetic("grindstone/repairing", dev.emi.emi.EmiUtil.subId(i))));
                }
            } catch (Throwable t) {
                EmiLog.error("Exception thrown registering repair recipes", t);
            }
            try {
                ItemStack defaultStack = i.getDefaultInstance();
                int acceptableEnchantments = 0;
                Consumer<Enchantment> consumer = e -> {
                    int max = e.getMaxLevel();
                    addRecipeSafe(registry, () -> new EmiAnvilEnchantRecipe(i, e, max,
                            synthetic("anvil/enchanting",
                                    dev.emi.emi.EmiUtil.subId(i) + "/"
                                            + dev.emi.emi.EmiUtil
                                                    .subId(EmiPort.getEnchantmentRegistry().getKey(e))
                                            + "/" + max)));
                };
                if (defaultStack.isEnchantable() && defaultStack.getItem().isEnchantable(defaultStack)) {
                    for (Enchantment e : targetedEnchantments) {
                        if (e.canEnchant(defaultStack) && EmiForgePlatform.isEnchantable(defaultStack, e)) {
                            consumer.accept(e);
                            acceptableEnchantments++;
                        }
                    }
                }
                if (acceptableEnchantments > 0) {
                    for (Enchantment e : universalEnchantments) {
                        if (e.canEnchant(defaultStack)) {
                            consumer.accept(e);
                            acceptableEnchantments++;
                        }
                    }
                    addRecipeSafe(registry, () -> new EmiGrindstoneDisenchantingRecipe(i,
                            synthetic("grindstone/disenchanting/tool", dev.emi.emi.EmiUtil.subId(i))));
                }
            } catch (Throwable t) {
                EmiReloadLog.warn("Exception thrown registering enchantment recipes", t);
            }
            if (i instanceof BlockItem bi && bi.getBlock() instanceof TallFlowerBlock tf
                    && dev.emi.emi.EmiPort.canTallFlowerDuplicate(tf)) {
                addRecipeSafe(registry,
                        () -> basicWorld(EmiStack.of(bi).setRemainder(EmiStack.of(bi)), EmiStack.of(Items.BONE_MEAL),
                                EmiStack.of(i),
                                synthetic("world/flower_duping", dev.emi.emi.EmiUtil.subId(i)), false));
            }
        }
        addRecipeSafe(registry, () -> new EmiAnvilRecipe(EmiStack.of(Items.ELYTRA), EmiStack.of(Items.PHANTOM_MEMBRANE),
                synthetic("anvil/repairing/material", dev.emi.emi.EmiUtil.subId(Items.ELYTRA) + "/"
                        + dev.emi.emi.EmiUtil.subId(Items.PHANTOM_MEMBRANE))));
        addRecipeSafe(registry, () -> new EmiAnvilRecipe(EmiStack.of(Items.SHIELD), EmiIngredient.of(ItemTags.PLANKS),
                synthetic("anvil/repairing/material",
                        dev.emi.emi.EmiUtil.subId(Items.SHIELD) + "/" + dev.emi.emi.EmiUtil.subId(Items.OAK_PLANKS))));

        for (Enchantment e : dev.emi.emi.EmiPort.getEnchantmentRegistry()) {
            if (!e.isCurse()) {
                int max = Math.min(10, e.getMaxLevel());
                int min = e.getMinLevel();
                while (min <= max) {
                    int level = min;
                    addRecipeSafe(registry, () -> new EmiGrindstoneDisenchantingBookRecipe(e, level,
                            synthetic("grindstone/disenchanting/book",
                                    dev.emi.emi.EmiUtil.subId(EmiPort.getEnchantmentRegistry().getKey(e))
                                            + "/" + level)));
                    min++;
                }
            }
        }
    }

    private static void addWorldInteraction(EmiRegistry registry, Set<Item> hiddenItems, List<Item> dyeableItems) {
        EmiStack concreteWater = EmiStack.of(Fluids.WATER);
        concreteWater.setRemainder(concreteWater);
        addConcreteRecipe(registry, Blocks.WHITE_CONCRETE_POWDER, concreteWater, Blocks.WHITE_CONCRETE);
        addConcreteRecipe(registry, Blocks.ORANGE_CONCRETE_POWDER, concreteWater, Blocks.ORANGE_CONCRETE);
        addConcreteRecipe(registry, Blocks.MAGENTA_CONCRETE_POWDER, concreteWater, Blocks.MAGENTA_CONCRETE);
        addConcreteRecipe(registry, Blocks.LIGHT_BLUE_CONCRETE_POWDER, concreteWater, Blocks.LIGHT_BLUE_CONCRETE);
        addConcreteRecipe(registry, Blocks.YELLOW_CONCRETE_POWDER, concreteWater, Blocks.YELLOW_CONCRETE);
        addConcreteRecipe(registry, Blocks.LIME_CONCRETE_POWDER, concreteWater, Blocks.LIME_CONCRETE);
        addConcreteRecipe(registry, Blocks.PINK_CONCRETE_POWDER, concreteWater, Blocks.PINK_CONCRETE);
        addConcreteRecipe(registry, Blocks.GRAY_CONCRETE_POWDER, concreteWater, Blocks.GRAY_CONCRETE);
        addConcreteRecipe(registry, Blocks.LIGHT_GRAY_CONCRETE_POWDER, concreteWater, Blocks.LIGHT_GRAY_CONCRETE);
        addConcreteRecipe(registry, Blocks.CYAN_CONCRETE_POWDER, concreteWater, Blocks.CYAN_CONCRETE);
        addConcreteRecipe(registry, Blocks.PURPLE_CONCRETE_POWDER, concreteWater, Blocks.PURPLE_CONCRETE);
        addConcreteRecipe(registry, Blocks.BLUE_CONCRETE_POWDER, concreteWater, Blocks.BLUE_CONCRETE);
        addConcreteRecipe(registry, Blocks.BROWN_CONCRETE_POWDER, concreteWater, Blocks.BROWN_CONCRETE);
        addConcreteRecipe(registry, Blocks.GREEN_CONCRETE_POWDER, concreteWater, Blocks.GREEN_CONCRETE);
        addConcreteRecipe(registry, Blocks.RED_CONCRETE_POWDER, concreteWater, Blocks.RED_CONCRETE);
        addConcreteRecipe(registry, Blocks.BLACK_CONCRETE_POWDER, concreteWater, Blocks.BLACK_CONCRETE);

        EmiIngredient axes = damagedTool(getPreferredTag(List.of(
                "minecraft:axes", "forge:tools/axes"), EmiStack.of(Items.IRON_AXE)), 1);
        for (Map.Entry<Block, Block> entry : AxeItemAccessor.getStrippedBlocks().entrySet()) {
            ResourceLocation id = synthetic("world/stripping", dev.emi.emi.EmiUtil.subId(entry.getKey()));
            addRecipeSafe(registry,
                    () -> basicWorld(EmiStack.of(entry.getKey()), axes, EmiStack.of(entry.getValue()), id));
        }
        for (Map.Entry<Block, Block> entry : WeatheringCopper.PREVIOUS_BY_BLOCK.get().entrySet()) {
            ResourceLocation id = synthetic("world/stripping", dev.emi.emi.EmiUtil.subId(entry.getKey()));
            addRecipeSafe(registry,
                    () -> basicWorld(EmiStack.of(entry.getKey()), axes, EmiStack.of(entry.getValue()), id));
        }
        for (Map.Entry<Block, Block> entry : HoneycombItem.WAX_OFF_BY_BLOCK.get().entrySet()) {
            ResourceLocation id = synthetic("world/stripping", dev.emi.emi.EmiUtil.subId(entry.getKey()));
            addRecipeSafe(registry,
                    () -> basicWorld(EmiStack.of(entry.getKey()), axes, EmiStack.of(entry.getValue()), id));
        }

        EmiIngredient shears = damagedTool(EmiStack.of(Items.SHEARS), 1);
        addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                .id(synthetic("world/shearing", "minecraft/pumpkin"))
                .leftInput(EmiStack.of(Items.PUMPKIN))
                .rightInput(shears, true)
                .output(EmiStack.of(Items.PUMPKIN_SEEDS, 4))
                .output(EmiStack.of(Items.CARVED_PUMPKIN))
                .build());
        EmiIngredient hoes = damagedTool(getPreferredTag(List.of(
                "minecraft:hoes", "forge:tools/hoes"), EmiStack.of(Items.IRON_HOE)), 1);
        for (Map.Entry<Block, Pair<Predicate<UseOnContext>, Consumer<UseOnContext>>> entry : HoeItemAccessor
                .getTillingActions().entrySet()) {
            Consumer<UseOnContext> consumer = entry.getValue().getSecond();
            if (EmiClient.HOE_ACTIONS.containsKey(consumer)) {
                Block b = entry.getKey();
                ResourceLocation id = synthetic("world/tilling", dev.emi.emi.EmiUtil.subId(b));
                var actions = EmiClient.HOE_ACTIONS.get(consumer);
                var list = new ObjectArrayList<EmiStack>(actions.size());
                for (var action : actions)
                    list.add(EmiStack.of(action));
                if (list.size() == 1) {
                    addRecipeSafe(registry, () -> basicWorld(EmiStack.of(b), hoes, list.get(0), id));
                } else if (list.size() == 2) {
                    addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                            .id(id)
                            .leftInput(EmiStack.of(b))
                            .rightInput(hoes, true)
                            .output(list.get(0))
                            .output(list.get(1))
                            .build());
                } else {
                    EmiReloadLog.warn("Encountered hoe action of peculiar size " + list.size() + ", skipping.");
                }
            }
        }

        EmiIngredient shovels = damagedTool(getPreferredTag(List.of(
                "minecraft:shovels", "forge:tools/shovels"), EmiStack.of(Items.IRON_SHOVEL)), 1);
        for (Map.Entry<Block, BlockState> entry : ShovelItemAccessor.getPathStates().entrySet()) {
            Block result = entry.getValue().getBlock();
            ResourceLocation id = synthetic("world/flattening", dev.emi.emi.EmiUtil.subId(entry.getKey()));
            addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), shovels, EmiStack.of(result), id));
        }

        EmiIngredient honeycomb = EmiStack.of(Items.HONEYCOMB);
        for (Map.Entry<Block, Block> entry : HoneycombItem.WAXABLES.get().entrySet()) {
            ResourceLocation id = synthetic("world/waxing", dev.emi.emi.EmiUtil.subId(entry.getKey()));
            addRecipeSafe(registry,
                    () -> basicWorld(EmiStack.of(entry.getKey()), honeycomb, EmiStack.of(entry.getValue()), id, false));
        }

        for (Item i : dyeableItems) {
            if (hiddenItems.contains(i)) {
                continue;
            }
            EmiStack cauldron = EmiStack.of(Items.CAULDRON);
            EmiStack waterThird = EmiStack.of(Fluids.WATER, FluidUnit.BOTTLE);
            int uniq = dev.emi.emi.EmiUtil.RANDOM.nextInt();
            addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                    .id(synthetic("world/cauldron_washing", dev.emi.emi.EmiUtil.subId(i)))
                    .leftInput(EmiStack.EMPTY, s -> new GeneratedSlotWidget(r -> {
                        ItemStack stack = i.getDefaultInstance();
                        ((DyeableLeatherItem) i).setColor(stack, r.nextInt(0xFFFFFF + 1));
                        return EmiStack.of(stack);
                    }, uniq, s.getBounds().x(), s.getBounds().y()))
                    .rightInput(cauldron, true)
                    .rightInput(waterThird, false)
                    .output(EmiStack.of(i))
                    .supportsRecipeTree(false)
                    .build());
        }

        EmiStack water = EmiStack.of(Fluids.WATER, FluidUnit.BUCKET);
        EmiStack lava = EmiStack.of(Fluids.LAVA, FluidUnit.BUCKET);
        EmiStack waterCatalyst = water.copy().setRemainder(water);
        EmiStack lavaCatalyst = lava.copy().setRemainder(lava);

        addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                .id(synthetic("world/fluid_spring", "minecraft/water"))
                .leftInput(waterCatalyst)
                .rightInput(waterCatalyst, false)
                .output(EmiStack.of(Fluids.WATER, FluidUnit.BUCKET))
                .build());
        addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                .id(synthetic("world/fluid_interaction", "minecraft/cobblestone"))
                .leftInput(waterCatalyst)
                .rightInput(lavaCatalyst, false)
                .output(EmiStack.of(Items.COBBLESTONE))
                .build());
        addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                .id(synthetic("world/fluid_interaction", "minecraft/stone"))
                .leftInput(waterCatalyst)
                .rightInput(lavaCatalyst, false)
                .output(EmiStack.of(Items.STONE))
                .build());
        addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                .id(synthetic("world/fluid_interaction", "minecraft/obsidian"))
                .leftInput(lava)
                .rightInput(waterCatalyst, false)
                .output(EmiStack.of(Items.OBSIDIAN))
                .build());

        EmiStack soulSoil = EmiStack.of(Items.SOUL_SOIL);
        soulSoil.setRemainder(soulSoil);
        EmiStack blueIce = EmiStack.of(Items.BLUE_ICE);
        blueIce.setRemainder(blueIce);

        addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
                .id(synthetic("world/fluid_interaction", "minecraft/basalt"))
                .leftInput(lavaCatalyst)
                .rightInput(soulSoil, false, s -> s.appendTooltip(EmiPort
                        .translatable("tooltip.emi.fluid_interaction.basalt.soul_soil", ChatFormatting.GREEN)))
                .rightInput(blueIce, false, s -> s.appendTooltip(EmiPort
                        .translatable("tooltip.emi.fluid_interaction.basalt.blue_ice", ChatFormatting.GREEN)))
                .output(EmiStack.of(Items.BASALT))
                .build());

        dev.emi.emi.EmiPort.getFluidRegistry().holders().forEach(entry -> {
            Fluid fluid = entry.value();
            Item bucket = fluid.getBucket();
            if (fluid.isSource(fluid.defaultFluidState()) && !fluid.defaultFluidState().createLegacyBlock().isAir()
                    && bucket != Items.AIR && fluid instanceof FlowingFluid) {
                addRecipeSafe(registry,
                        () -> basicWorld(EmiStack.of(Items.BUCKET), EmiStack.of(fluid, FluidUnit.BUCKET),
                                EmiStack.of(bucket),
                                synthetic("emi", "bucket_filling/" + dev.emi.emi.EmiUtil.subId(fluid)), false));
            }
        });

        addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.GLASS_BOTTLE), water,
                EmiStack.of(EmiPort.setPotion(new ItemStack(Items.POTION), Potions.WATER)),
                synthetic("world/unique", "minecraft/water_bottle")));

        EmiStack waterBottle = EmiStack.of(EmiPort.setPotion(new ItemStack(Items.POTION), Potions.WATER))
                .setRemainder(EmiStack.of(Items.GLASS_BOTTLE));
        EmiStack mud = EmiStack.of(Items.MUD);
        addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.DIRT), waterBottle, mud,
                synthetic("world/unique", "minecraft/mud"), false));
    }

    private static EmiIngredient damagedTool(EmiIngredient tool, int damage) {
        for (EmiStack stack : tool.getEmiStacks()) {
            ItemStack is = stack.getItemStack().copy();
            is.setDamageValue(1);
            stack.setRemainder(EmiStack.of(is));
        }
        return tool;
    }

    private static EmiIngredient getPreferredTag(List<String> candidates, EmiIngredient fallback) {
        for (String id : candidates) {
            EmiIngredient potential = EmiIngredient
                    .of(TagKey.create(EmiPort.getItemRegistry().key(), dev.emi.emi.EmiPort.id(id)));
            if (!potential.isEmpty()) {
                return potential;
            }
        }
        return fallback;
    }

    private static void addFuel(EmiRegistry registry, ReferenceOpenHashSet<Item> hiddenItems) {
        var fuels = EmiForgePlatform.getFuelMap();
        var fuelMap = new Reference2IntOpenHashMap<Item>(fuels.size());
        fuels.forEach((item, time) -> fuelMap.put(item, time.intValue()));
        compressRecipesToTags(new ReferenceOpenHashSet<>(fuelMap.keySet()), (a, b) -> {
            return Integer.compare(fuelMap.getInt(a), fuelMap.getInt(b));
        }, tag -> {
            EmiIngredient stack = EmiIngredient.of(tag.raw());
            Item item = stack.getEmiStacks().get(0).getItemStack().getItem();
            int time = fuelMap.getInt(item);
            registry.addRecipe(
                    new EmiFuelRecipe(stack, time, synthetic("fuel/tag", dev.emi.emi.EmiUtil.subId(tag.id()))));
        }, item -> {
            if (!hiddenItems.contains(item)) {
                int time = fuelMap.getInt(item);
                registry.addRecipe(new EmiFuelRecipe(EmiStack.of(item), time,
                        synthetic("fuel/item", dev.emi.emi.EmiUtil.subId(item))));
            }
        });
    }

    private static void addComposting(EmiRegistry registry, ReferenceOpenHashSet<Item> hiddenItems) {
        var compostables = new ReferenceOpenHashSet<Item>(ComposterBlock.COMPOSTABLES.size());
        for (var item : ComposterBlock.COMPOSTABLES.keySet())
            compostables.add(item.asItem());
        compressRecipesToTags(compostables, (a, b) -> {
            return Float.compare(ComposterBlock.COMPOSTABLES.getFloat(a), ComposterBlock.COMPOSTABLES.getFloat(b));
        }, tag -> {
            EmiIngredient stack = EmiIngredient.of(tag.raw());
            Item item = stack.getEmiStacks().get(0).getItemStack().getItem();
            float chance = ComposterBlock.COMPOSTABLES.getFloat(item);
            registry.addRecipe(new EmiCompostingRecipe(stack, chance,
                    synthetic("composting/tag", dev.emi.emi.EmiUtil.subId(tag.id()))));
        }, item -> {
            if (!hiddenItems.contains(item)) {
                float chance = ComposterBlock.COMPOSTABLES.getFloat(item);
                registry.addRecipe(new EmiCompostingRecipe(EmiStack.of(item), chance,
                        synthetic("composting/item", dev.emi.emi.EmiUtil.subId(item))));
            }
        });
    }

    private static void compressRecipesToTags(ReferenceOpenHashSet<Item> stacks, Comparator<Item> comparator,
            Consumer<EmiTagKey<Item>> tagConsumer, Consumer<Item> itemConsumer) {
        var handled = new ReferenceOpenHashSet<Item>(stacks.size());
        outer: for (EmiTagKey<Item> key : EmiTags.getTags(EmiPort.getItemRegistry())) {
            List<Item> items = key.getList();
            if (items.size() < 2) {
                continue;
            }
            Item base = items.get(0);
            if (!stacks.contains(base)) {
                continue;
            }
            for (int i = 1; i < items.size(); i++) {
                Item item = items.get(i);
                if (!stacks.contains(item) || comparator.compare(base, item) != 0) {
                    continue outer;
                }
            }
            if (handled.containsAll(items)) {
                continue;
            }
            handled.addAll(items);
            tagConsumer.accept(key);
        }
        for (Item item : stacks) {
            if (handled.contains(item)) {
                continue;
            }
            itemConsumer.accept(item);
        }
    }

    private static ResourceLocation synthetic(String type, String name) {
        return dev.emi.emi.EmiPort.id("emi", "/" + type + "/" + name);
    }

    private static <C extends Container, T extends Recipe<C>> Iterable<T> getRecipes(EmiRegistry registry,
            RecipeType<T> type) {
        return registry.getRecipeManager().getAllRecipesFor(type);
    }

    private static void safely(String name, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            EmiReloadLog.warn("Exception thrown when reloading " + name + " step in vanilla EMI plugin", t);
        }
    }

    private static void addRecipeSafe(EmiRegistry registry, Supplier<EmiRecipe> supplier) {
        try {
            registry.addRecipe(supplier.get());
        } catch (Throwable e) {
            EmiReloadLog.warn("Exception thrown when parsing EMI recipe (no ID available)", e);
        }
    }

    private static void addRecipeSafe(EmiRegistry registry, Supplier<EmiRecipe> supplier, Recipe<?> recipe) {
        try {
            registry.addRecipe(supplier.get());
        } catch (Throwable e) {
            EmiReloadLog.warn("Exception thrown when parsing vanilla recipe " + EmiPort.getId(recipe), e);
        }
    }

    private static EmiRenderable simplifiedRenderer(int u, int v) {
        return (raw, x, y, delta) -> {
            EmiDrawContext context = EmiDrawContext.wrap(raw);
            context.drawTexture(EmiRenderHelper.WIDGETS, x, y, u, v, 16, 16);
        };
    }

    private static void addConcreteRecipe(EmiRegistry registry, Block powder, EmiStack water, Block result) {
        addRecipeSafe(registry, () -> basicWorld(EmiStack.of(powder), water, EmiStack.of(result),
                synthetic("world/concrete", EmiUtil.subId(result))));
    }

    private static EmiRecipe basicWorld(EmiIngredient left, EmiIngredient right, EmiStack output, ResourceLocation id) {
        return basicWorld(left, right, output, id, true);
    }

    private static EmiRecipe basicWorld(EmiIngredient left, EmiIngredient right, EmiStack output, ResourceLocation id,
            boolean catalyst) {
        return EmiWorldInteractionRecipe.builder()
                .id(id)
                .leftInput(left)
                .rightInput(right, catalyst)
                .output(output)
                .build();
    }
}
