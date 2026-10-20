/* GTO adaptation of EMI Trades. MIT; see LICENSE-EMITrades. */
package io.github.prismwork.emitrades;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Bounded, world-independent sampling logic, separate from client plugin initialization. */
final class TradeOfferSampler {
    private static final int SAMPLE_ATTEMPTS = 16;

    static ObjectArrayList<MerchantOffer> sample(VillagerTrades.ItemListing listing, AbstractVillager trader,
            RandomSource random) {
        var offers = new ObjectArrayList<MerchantOffer>(SAMPLE_ATTEMPTS);
        for (int attempt = 0; attempt < SAMPLE_ATTEMPTS; attempt++) {
            var offer = listing.getOffer(trader, random);
            if (offer == null || offer.getResult().isEmpty())
                continue;
            boolean duplicate = false;
            for (var previous : offers) {
                if (sameOffer(previous, offer)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate)
                offers.add(offer);
        }
        return offers;
    }

    private static boolean sameOffer(MerchantOffer a, MerchantOffer b) {
        return ItemStack.matches(a.getBaseCostA(), b.getBaseCostA())
                && ItemStack.matches(a.getCostB(), b.getCostB())
                && ItemStack.matches(a.getResult(), b.getResult());
    }

}
