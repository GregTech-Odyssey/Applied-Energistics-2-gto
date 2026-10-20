/*
 * Adapted from EMI Trades by Prismwork and Flamarine.
 * Copyright (c) 2022-2023 Prismwork. Licensed under MIT; see LICENSE-EMITrades.
 * Modified for the GTO AE fork: bounded sampling and detached display data.
 */
package io.github.prismwork.emitrades;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.prismwork.emitrades.config.EMITradesConfig;
import io.github.prismwork.emitrades.recipe.VillagerTrade;
import io.github.prismwork.emitrades.util.EntityEmiStack;
import io.github.prismwork.emitrades.util.TradeEntitySnapshot;
import io.github.prismwork.emitrades.util.TradeProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@EmiEntrypoint
public final class EMITradesPlugin implements EmiPlugin {
    public static final Logger LOGGER = LoggerFactory.getLogger("EMI Trades");
    public static final EmiRecipeCategory VILLAGER_TRADES = new EmiRecipeCategory(
            new ResourceLocation("emitrades", "villager_trades"), EmiStack.of(Items.EMERALD));
    public static EMITradesConfig.Config CONFIG = new EMITradesConfig.Config();
    private static final ResourceLocation WANDERING_TRADER = new ResourceLocation("minecraft", "wandering_trader");
    private static final ObjectArrayList<Enchantment> TRADEABLE_ENCHANTMENTS = new ObjectArrayList<>();

    @Override
    public void register(EmiRegistry registry) {
        var level = Minecraft.getInstance().level;
        if (level == null)
            return;
        CONFIG = EMITradesConfig.load();
        registry.addCategory(VILLAGER_TRADES);
        TRADEABLE_ENCHANTMENTS.clear();
        for (var enchantment : BuiltInRegistries.ENCHANTMENT) {
            if (enchantment.isTradeable())
                TRADEABLE_ENCHANTMENTS.add(enchantment);
        }
        var random = RandomSource.create(0);
        for (var profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            var offers = VillagerTrades.TRADES.get(profession);
            if (offers == null || offers.isEmpty())
                continue;
            var villager = EntityType.VILLAGER.create(level);
            if (villager == null)
                continue;
            villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(5));
            var workstation = TradeEntitySnapshot.of(villager);
            registry.addWorkstation(VILLAGER_TRADES, EntityEmiStack.ofSnapshot(workstation, 8));
            int id = 0;
            for (int tradeLevel = 1; tradeLevel <= 5; tradeLevel++) {
                var listings = offers.get(tradeLevel);
                if (listings == null)
                    continue;
                villager.setVillagerData(villager.getVillagerData().setLevel(tradeLevel));
                var profile = new TradeProfile(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession), tradeLevel,
                        tradeLevel == 5 ? workstation : TradeEntitySnapshot.of(villager), false);
                id = addTrades(registry, listings, profile, villager, random, id);
            }
        }
        var trader = EntityType.WANDERING_TRADER.create(level);
        if (trader != null) {
            var snapshot = TradeEntitySnapshot.of(trader);
            registry.addWorkstation(VILLAGER_TRADES, EntityEmiStack.ofSnapshot(snapshot, 8));
            int id = 0;
            for (var iterator = it.unimi.dsi.fastutil.ints.Int2ObjectMaps
                    .fastIterator(VillagerTrades.WANDERING_TRADER_TRADES); iterator.hasNext();) {
                var entry = iterator.next();
                var profile = new TradeProfile(WANDERING_TRADER, entry.getIntKey(), snapshot, true);
                id = addTrades(registry, entry.getValue(), profile, trader, random, id);
            }
        }
    }

    private static int addTrades(EmiRegistry registry, VillagerTrades.ItemListing[] listings, TradeProfile profile,
            AbstractVillager trader, RandomSource random, int id) {
        for (var listing : listings) {
            if (isVanillaFactory(listing)) {
                registry.addRecipe(new VillagerTrade(profile, listing, id++));
            } else {
                // Third-party factories can require server-only state, return null or have unlimited variants.
                try {
                    for (var offer : TradeOfferSampler.sample(listing, trader, random)) {
                        registry.addRecipe(new VillagerTrade(profile, offer, id++));
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("Unable to preview trade factory {} for {}", listing.getClass().getName(),
                            profile.profession(), exception);
                }
            }
        }
        return id;
    }

    private static boolean isVanillaFactory(VillagerTrades.ItemListing offer) {
        // Subclasses can override getOffer; only the actual vanilla factories use field-based previews.
        var type = offer.getClass();
        return type == VillagerTrades.EmeraldForItems.class || type == VillagerTrades.ItemsForEmeralds.class
                || type == VillagerTrades.SuspiciousStewForEmerald.class
                || type == VillagerTrades.ItemsAndEmeraldsToItems.class
                || type == VillagerTrades.EnchantedItemForEmeralds.class
                || type == VillagerTrades.EmeraldsForVillagerTypeItem.class
                || type == VillagerTrades.TippedArrowForItemsAndEmeralds.class
                || type == VillagerTrades.EnchantBookForEmeralds.class
                || type == VillagerTrades.TreasureMapForEmeralds.class
                || type == VillagerTrades.DyedArmorForEmeralds.class;
    }

    public static ObjectArrayList<Enchantment> tradeableEnchantments() {
        return TRADEABLE_ENCHANTMENTS;
    }
}
