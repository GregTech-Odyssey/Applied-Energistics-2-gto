/*
 * Adapted from EMI Trades by Prismwork and Flamarine.
 * Copyright (c) 2022-2023 Prismwork. Licensed under MIT; see LICENSE-EMITrades.
 * Modified for the GTO AE fork (Mojang mappings and world-independent display data).
 */
package io.github.prismwork.emitrades.recipe;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import io.github.prismwork.emitrades.EMITradesPlugin;
import io.github.prismwork.emitrades.util.EntityEmiStack;
import io.github.prismwork.emitrades.util.TradeProfile;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class VillagerTrade implements EmiRecipe {
    private final OutputType outputType;
    private final ObjectArrayList<EmiIngredient> inputs;
    private final ObjectArrayList<EmiStack> outputs;
    private final List<EmiIngredient> catalysts;
    private final ResourceLocation id;
    private final MutableComponent title;
    private final @Nullable Component levelTitle;

    public VillagerTrade(TradeProfile profile, VillagerTrades.ItemListing offer, int id) {
        this(profile, id, outputType(offer));
        if (offer instanceof VillagerTrades.EmeraldForItems factory) {
            inputs.add(0, EmiStack.of(factory.item, factory.cost));
            inputs.add(1, EmiStack.EMPTY);
            outputs.add(0, EmiStack.of(Items.EMERALD));
        } else if (offer instanceof VillagerTrades.ItemsForEmeralds factory) {
            inputs.add(0, EmiStack.of(Items.EMERALD, factory.emeraldCost));
            inputs.add(1, EmiStack.EMPTY);
            outputs.add(0, EmiStack.of(factory.itemStack, factory.numberOfItems));
        } else if (offer instanceof VillagerTrades.SuspiciousStewForEmerald factory) {
            inputs.add(0, EmiStack.of(Items.EMERALD, 1));
            inputs.add(1, EmiStack.EMPTY);
            ItemStack stack = new ItemStack(Items.SUSPICIOUS_STEW, 1);
            SuspiciousStewItem.saveMobEffect(stack, factory.effect, factory.duration);
            outputs.add(0, EmiStack.of(stack));
        } else if (offer instanceof VillagerTrades.ItemsAndEmeraldsToItems factory) {
            inputs.add(0, EmiStack.of(Items.EMERALD, factory.emeraldCost));
            inputs.add(1, EmiStack.of(factory.fromItem, factory.fromCount));
            outputs.add(0, EmiStack.of(factory.toItem, factory.toCount));
        } else if (offer instanceof VillagerTrades.EnchantedItemForEmeralds factory) {
            inputs.add(0, EmiStack.of(Items.EMERALD, Math.min(factory.baseEmeraldCost + 5, 64)));
            inputs.add(1, EmiStack.EMPTY);
            outputs.add(0, EmiStack.of(factory.itemStack));
        } else if (offer instanceof VillagerTrades.EmeraldsForVillagerTypeItem factory) {
            var items = new ReferenceOpenHashSet<Item>(
                    factory.trades.size());
            var stacks = new ObjectArrayList<EmiStack>(factory.trades.size());
            for (var item : factory.trades.values()) {
                if (items.add(item))
                    stacks.add(EmiStack.of(item));
            }
            inputs.add(0, new ListEmiIngredient(stacks, factory.cost));
            inputs.add(1, EmiStack.EMPTY);
            outputs.add(0, EmiStack.of(Items.EMERALD));
        } else if (offer instanceof VillagerTrades.TippedArrowForItemsAndEmeralds factory) {
            inputs.add(0, EmiStack.of(Items.EMERALD, factory.emeraldCost));
            inputs.add(1, EmiStack.of(factory.fromItem, factory.fromCount));
            outputs.add(0, EmiStack.of(factory.toItem, factory.toCount));
        } else if (offer instanceof VillagerTrades.EnchantBookForEmeralds) {
            inputs.add(0, EmiStack.of(Items.EMERALD, 5));
            inputs.add(1, EmiStack.of(Items.BOOK));
            outputs.add(0, EmiStack.of(Items.ENCHANTED_BOOK));
        } else if (offer instanceof VillagerTrades.TreasureMapForEmeralds factory) {
            inputs.add(0, EmiStack.of(Items.EMERALD, factory.emeraldCost));
            inputs.add(1, EmiStack.of(Items.COMPASS));
            outputs.add(0, EmiStack.of(Items.FILLED_MAP));
        } else if (offer instanceof VillagerTrades.DyedArmorForEmeralds factory) {
            inputs.add(0, EmiStack.of(Items.EMERALD, factory.value));
            inputs.add(1, EmiStack.EMPTY);
            outputs.add(0, EmiStack.of(factory.item));
        } else {
            inputs.add(0, EmiStack.EMPTY);
            inputs.add(1, EmiStack.EMPTY);
            outputs.add(0, EmiStack.EMPTY);
        }
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return catalysts;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return EMITradesPlugin.VILLAGER_TRADES;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        Font textRenderer = Minecraft.getInstance().font;
        int extraWidth = catalysts.isEmpty() ? 0 : 21;
        return (catalysts.isEmpty() || !EMITradesPlugin.CONFIG.enable3DVillagerModelInRecipes)
                ? Math.max(86, textRenderer.width(title) + 2)
                : Math.max(extraWidth + 85, extraWidth + textRenderer.width(title));
    }

    @Override
    public int getDisplayHeight() {
        return 28;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        Font textRenderer = Minecraft.getInstance().font;
        if (catalysts.isEmpty() || !EMITradesPlugin.CONFIG.enable3DVillagerModelInRecipes) {
            widgets.addText(title,
                    (getDisplayWidth() - textRenderer.width(title)) / 2, 0, 16777215, true);
            widgets.addSlot(inputs.get(0), getDisplayWidth() / 2 - 42, 10);
            widgets.addSlot(inputs.get(1), getDisplayWidth() / 2 - 22, 10);
            widgets.addTexture(EmiTexture.EMPTY_ARROW, getDisplayWidth() / 2 - 3, 10);
            SlotWidget outputSlot = new SlotWidget(outputs.get(0), getDisplayWidth() / 2 + 22, 10).recipeContext(this);
            wrapOutput(widgets, outputSlot);
        } else {
            SlotWidget villagerSlot = new SlotWidget(catalysts.get(0), 1, 6).drawBack(false);

            if (levelTitle != null)
                villagerSlot.appendTooltip(levelTitle);
            widgets.add(villagerSlot);
            widgets.addText(title,
                    21, 0, 16777215, true);
            widgets.addSlot(inputs.get(0), 21, 10);
            widgets.addSlot(inputs.get(1), 41, 10);
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 60, 10);
            SlotWidget outputSlot = new SlotWidget(outputs.get(0), 85, 10).recipeContext(this);
            wrapOutput(widgets, outputSlot);
        }
    }

    private void wrapOutput(WidgetHolder widgets, SlotWidget outputSlot) {
        if (outputType == OutputType.COLORED) {
            outputSlot = outputSlot.appendTooltip(
                    Component.translatable("emi.emitrades.random_colored").withStyle(ChatFormatting.YELLOW));
        } else if (outputType == OutputType.EFFECT) {
            outputSlot = outputSlot.appendTooltip(
                    Component.translatable("emi.emitrades.random_effect").withStyle(ChatFormatting.YELLOW));
        } else if (outputType == OutputType.STRUCTURE) {
            outputSlot = outputSlot.appendTooltip(
                    Component.translatable("emi.emitrades.random_structure").withStyle(ChatFormatting.YELLOW));
        } else if (outputType == OutputType.ENCHANTED) {
            var list = EMITradesPlugin.tradeableEnchantments();
            if (!list.isEmpty()) {
                outputSlot = outputSlot.appendTooltip(Component.translatable("emi.emitrades.enchantments.possible")
                        .withStyle(ChatFormatting.AQUA));
                for (Enchantment enchantment : list) {
                    outputSlot = outputSlot.appendTooltip(Component.literal("- ")
                            .append(Component.translatable(enchantment.getDescriptionId()))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        widgets.add(outputSlot);
    }

    private VillagerTrade(TradeProfile profile, int index, OutputType outputType) {
        this.outputType = outputType;
        inputs = new ObjectArrayList<>(2);
        outputs = new ObjectArrayList<>(1);
        catalysts = List.of(EntityEmiStack.ofSnapshot(profile.entity(), 12.0f));
        id = profile.recipeId(index);
        title = profile.title();
        levelTitle = profile.wandering() ? null
                : Component.translatable("emi.emitrades.profession.lvl." + profile.level())
                        .withStyle(ChatFormatting.YELLOW);
    }

    public VillagerTrade(TradeProfile profile, MerchantOffer offer, int id) {
        this(profile, id, OutputType.PLAIN);
        inputs.add(EmiStack.of(offer.getBaseCostA()));
        inputs.add(EmiStack.of(offer.getCostB()));
        outputs.add(EmiStack.of(offer.getResult()));
    }

    private static OutputType outputType(VillagerTrades.ItemListing offer) {
        if (offer instanceof VillagerTrades.DyedArmorForEmeralds)
            return OutputType.COLORED;
        if (offer instanceof VillagerTrades.TippedArrowForItemsAndEmeralds
                || offer instanceof VillagerTrades.SuspiciousStewForEmerald)
            return OutputType.EFFECT;
        if (offer instanceof VillagerTrades.TreasureMapForEmeralds)
            return OutputType.STRUCTURE;
        if (offer instanceof VillagerTrades.EnchantBookForEmeralds
                || offer instanceof VillagerTrades.EnchantedItemForEmeralds)
            return OutputType.ENCHANTED;
        return OutputType.PLAIN;
    }

    private enum OutputType {
        PLAIN, COLORED, EFFECT, STRUCTURE, ENCHANTED
    }
}
