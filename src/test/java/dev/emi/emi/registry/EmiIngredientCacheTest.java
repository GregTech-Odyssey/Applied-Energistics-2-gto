package dev.emi.emi.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEItemKey;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.runtime.EmiTagKey;

class EmiIngredientCacheTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearCache() {
        EmiIngredientCache.clear();
    }

    @Test
    void repeatedConversionReusesMatchingButIsolatesNestedTagStacksAndAmounts() {
        var source = mock(Ingredient.class);
        when(source.getItems()).thenReturn(new ItemStack[] { new ItemStack(Items.STICK, 3),
                new ItemStack(Items.PAPER, 3) });
        var key = mock(AEItemKey.class);
        when(key.getItem()).thenReturn(Items.STICK);
        var stack = new ItemEmiStack(key, 1);
        var rawTag = TagKey.create(Registries.ITEM, new ResourceLocation("test", "alternatives"));
        var tagKey = mock(EmiTagKey.class);
        when(tagKey.id()).thenReturn(rawTag.location());
        when(tagKey.raw()).thenReturn(rawTag);
        try (var tags = mockStatic(EmiTags.class); var keys = mockStatic(EmiTagKey.class);
                var stacks = mockStatic(EmiStack.class)) {
            keys.when(() -> EmiTagKey.of(rawTag)).thenReturn(tagKey);
            stacks.when(() -> EmiStack.of(any(ItemStack.class))).thenReturn(stack);
            var tag = new TagEmiIngredient(rawTag, List.of(stack), 1);
            var template = new ListEmiIngredient(List.of(tag), 3);
            tags.when(() -> EmiTags.getIngredient(eq(Item.class), anyList(), eq(3L))).thenReturn(template);

            var first = EmiIngredient.of(source);
            var second = EmiIngredient.of(source, 7);
            first.getEmiStacks().getFirst().setRemainder(new ItemEmiStack(key, 2));
            first.setChance(0.5f);

            assertEquals(3, first.getAmount());
            assertEquals(7, second.getAmount());
            assertEquals(1, second.getChance());
            assertSame(key, second.getEmiStacks().getFirst().getAEKey());
            assertNotSame(first.getEmiStacks().getFirst(), second.getEmiStacks().getFirst());
            assertTrue(second.getEmiStacks().getFirst().getRemainder().isEmpty());
            assertTrue(template.getEmiStacks().getFirst().getRemainder().isEmpty());
            assertInstanceOf(TagEmiIngredient.class, ((ListEmiIngredient) second).getIngredients().getFirst());
            verify(source, times(1)).getItems();
            tags.verify(() -> EmiTags.getIngredient(eq(Item.class), anyList(), eq(3L)), times(1));

            EmiIngredientCache.clear();
            EmiIngredient.of(source);
            verify(source, times(2)).getItems();
        }
    }

    @Test
    void mixedCountsNormalizeToOneAndCustomIngredientsRemainUncached() {
        var source = mock(Ingredient.class, withSettings().extraInterfaces(Runnable.class));
        when(source.getItems()).thenReturn(new ItemStack[] { new ItemStack(Items.STICK, 3),
                new ItemStack(Items.PAPER, 2) });
        try (var tags = mockStatic(EmiTags.class); var stacks = mockStatic(EmiStack.class)) {
            stacks.when(() -> EmiStack.of(any(ItemStack.class))).thenReturn(EmiStack.EMPTY);
            tags.when(() -> EmiTags.getIngredient(eq(Item.class), anyList(), eq(1L))).thenReturn(EmiStack.EMPTY);
            assertSame(EmiStack.EMPTY, EmiIngredient.of(source));
            assertSame(EmiStack.EMPTY, EmiIngredient.of(source));
            verify(source, times(2)).getItems();
            tags.verify(() -> EmiTags.getIngredient(eq(Item.class), anyList(), eq(1L)), times(2));
        }
    }

    @Test
    void taggedAlternativesKeepTheirKeysAndComparisonChangesDiscardConvertedTemplates() {
        var source = mock(Ingredient.class);
        var firstInput = new ItemStack(Items.STICK, 2);
        var secondInput = new ItemStack(Items.STICK, 3);
        var firstTag = new CompoundTag();
        firstTag.putInt("variant", 1);
        var secondTag = new CompoundTag();
        secondTag.putInt("variant", 2);
        firstInput.setTag(firstTag);
        secondInput.setTag(secondTag);
        when(source.getItems()).thenReturn(new ItemStack[] { firstInput, secondInput });
        var firstKey = mock(AEItemKey.class);
        var secondKey = mock(AEItemKey.class);
        when(firstKey.getItem()).thenReturn(Items.STICK);
        when(secondKey.getItem()).thenReturn(Items.STICK);
        when(firstKey.getTag()).thenReturn(firstTag);
        when(secondKey.getTag()).thenReturn(secondTag);
        var originalComparisons = EmiComparisonDefaults.comparisons;
        EmiComparisonDefaults.comparisons = new Reference2ObjectOpenHashMap<>();
        try (var stacks = mockStatic(EmiStack.class)) {
            stacks.when(() -> EmiStack.of(firstInput)).thenReturn(new ItemEmiStack(firstKey, 2));
            stacks.when(() -> EmiStack.of(secondInput)).thenReturn(new ItemEmiStack(secondKey, 3));
            var registry = new EmiRegistryImpl();
            registry.setDefaultComparison(Items.STICK, Comparison.compareNbt());
            var converted = EmiIngredient.of(source);
            assertEquals(1, converted.getAmount());
            assertEquals(2, converted.getEmiStacks().size());
            assertSame(firstKey, converted.getEmiStacks().get(0).getAEKey());
            assertSame(secondKey, converted.getEmiStacks().get(1).getAEKey());
            assertEquals(firstTag, converted.getEmiStacks().get(0).getNbt());
            assertEquals(secondTag, converted.getEmiStacks().get(1).getNbt());
            assertEquals(2, converted.getEmiStacks().get(0).getAmount());
            assertEquals(3, converted.getEmiStacks().get(1).getAmount());
            registry.setDefaultComparison(Items.STICK, Comparison.DEFAULT_COMPARISON);
            assertEquals(1, EmiIngredient.of(source).getEmiStacks().size());
            verify(source, times(2)).getItems();
        } finally {
            EmiComparisonDefaults.comparisons = originalComparisons;
        }
    }
}
