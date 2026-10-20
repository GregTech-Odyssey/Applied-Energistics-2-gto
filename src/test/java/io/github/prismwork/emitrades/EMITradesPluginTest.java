package io.github.prismwork.emitrades;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.prismwork.emitrades.recipe.VillagerTrade;
import io.github.prismwork.emitrades.util.EntityEmiStack;
import io.github.prismwork.emitrades.util.TradeEntitySnapshot;
import io.github.prismwork.emitrades.util.TradeProfile;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

import appeng.api.stacks.AEItemKey;

class EMITradesPluginTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void samplingTerminatesWhenEveryOfferIsUniqueAndPassesTheMerchant() {
        var trader = mock(AbstractVillager.class);
        var calls = new AtomicInteger();
        VillagerTrades.ItemListing listing = (entity, random) -> {
            assertSame(trader, entity);
            return offer(calls.incrementAndGet(), null);
        };

        var offers = TradeOfferSampler.sample(listing, trader, RandomSource.create(0));

        assertEquals(16, calls.get());
        assertEquals(16, offers.size());
    }

    @Test
    void samplingSkipsNullAndEmptyOffersAndKeepsCountAndNbtVariants() {
        var calls = new AtomicInteger();
        VillagerTrades.ItemListing listing = (entity, random) -> switch (calls.getAndIncrement() % 5) {
            case 0 -> null;
            case 1 -> new MerchantOffer(new ItemStack(Items.EMERALD), ItemStack.EMPTY, 1, 1, 0);
            case 2 -> offer(1, null);
            case 3 -> offer(2, null);
            default -> offer(1, "variant");
        };

        var offers = TradeOfferSampler.sample(listing, mock(AbstractVillager.class), RandomSource.create(0));

        assertEquals(16, calls.get());
        assertEquals(3, offers.size());
        assertEquals(1, offers.get(0).getBaseCostA().getCount());
        assertEquals(2, offers.get(1).getBaseCostA().getCount());
        assertEquals("variant", offers.get(2).getResult().getTag().getString("test"));
    }

    @Test
    void copiesPreserveEntityIdentityAppearanceAndStackMetadataWithoutReadingTheWorld() {
        var entity = mock(Entity.class);
        doReturn(EntityType.WANDERING_TRADER).when(entity).getType();
        when(entity.getName()).thenReturn(Component.literal("Trader"));
        when(entity.saveWithoutId(any())).thenAnswer(invocation -> {
            CompoundTag tag = invocation.getArgument(0);
            tag.putString("appearance", "test");
            return tag;
        });
        var snapshot = TradeEntitySnapshot.of(entity);
        var stack = EntityEmiStack.ofSnapshot(snapshot, 12);
        stack.setAmount(3).setChance(0.5f);

        var copy = stack.copy();

        assertSame(snapshot, copy.getKey());
        assertSame(stack.getNbt(), copy.getNbt());
        assertEquals("test", copy.getNbt().getString("appearance"));
        assertEquals("Trader", copy.getName().getString());
        assertEquals(new ResourceLocation("minecraft", "wandering_trader"), copy.getId());
        assertEquals(3, copy.getAmount());
        assertEquals(0.5f, copy.getChance());
        assertFalse(copy.isEmpty());
        verify(entity, never()).level();
    }

    @Test
    void professionIdsRetainVanillaCompatibilityAndSeparateModNamespaces() {
        var vanilla = new TradeProfile(new ResourceLocation("minecraft", "farmer"), 1, null, false);
        var first = new TradeProfile(new ResourceLocation("first", "farmer"), 1, null, false);
        var second = new TradeProfile(new ResourceLocation("second", "farmer"), 1, null, false);

        assertEquals(new ResourceLocation("emitrades", "villager_trades/farmer_0"), vanilla.recipeId(0));
        assertNotEquals(first.recipeId(0), second.recipeId(0));
        assertNotEquals(vanilla.recipeId(0), first.recipeId(0));
        assertEquals("entity.minecraft.villager.first.farmer",
                ((TranslatableContents) first.title().getContents()).getKey());
    }

    @Test
    void everyVanillaFactoryHasANonemptyPreviewWithoutCallingServerTradeGeneration() {
        var profile = new TradeProfile(new ResourceLocation("minecraft", "farmer"), 1, null, false);
        // Ordinary JUnit does not apply AE's Item mixin. Stub only its canonical key lookup;
        // the recipe adapters and EMI item stacks still execute normally.
        try (var keys = mockStatic(AEItemKey.class)) {
            keys.when(() -> AEItemKey.of(any(ItemLike.class), nullable(CompoundTag.class))).thenAnswer(invocation -> {
                var key = mock(AEItemKey.class);
                ItemLike item = invocation.getArgument(0);
                when(key.getItem()).thenReturn(item.asItem());
                when(key.getTag()).thenReturn(invocation.getArgument(1));
                return key;
            });
            VillagerTrades.TRADES.values().forEach(levels -> levels.values().forEach(listings -> {
                for (var listing : listings)
                    assertPreview(profile, listing);
            }));
            VillagerTrades.WANDERING_TRADER_TRADES.values().forEach(listings -> {
                for (var listing : listings)
                    assertPreview(profile, listing);
            });
        }
    }

    private static void assertPreview(TradeProfile profile, VillagerTrades.ItemListing listing) {
        var recipe = new VillagerTrade(profile, listing, 0);
        assertEquals(2, recipe.getInputs().size());
        assertFalse(recipe.getInputs().getFirst().isEmpty(), listing.getClass().getName());
        assertFalse(recipe.getOutputs().getFirst().isEmpty(), listing.getClass().getName());
    }

    private static MerchantOffer offer(int count, String variant) {
        var result = new ItemStack(Items.BREAD);
        if (variant != null)
            result.getOrCreateTag().putString("test", variant);
        return new MerchantOffer(new ItemStack(Items.EMERALD, count), result, 1, 1, 0);
    }
}
