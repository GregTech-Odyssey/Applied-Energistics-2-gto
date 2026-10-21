package dev.emi.emi.jemi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.jemi.impl.JemiIngredientAcceptor;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiTagKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;

import appeng.api.stacks.AEItemKey;

class JemiConversionCacheTest {
    private boolean hadItemAdapter;
    private EmiRegistryAdapter<?> previousItemAdapter;

    @BeforeEach
    void registerItemAdapterType() {
        var adapters = EmiTags.ADAPTERS_BY_CLASS.map();
        hadItemAdapter = adapters.containsKey(Item.class);
        previousItemAdapter = adapters.put(Item.class, null);
    }

    @AfterEach
    void restoreItemAdapterType() {
        var adapters = EmiTags.ADAPTERS_BY_CLASS.map();
        if (hadItemAdapter)
            adapters.put(Item.class, previousItemAdapter);
        else
            adapters.remove(Item.class);
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reusesNormalizationAcrossRecipesWithoutSharingMutableAlternatives() {
        var firstKey = mock(AEItemKey.class);
        var secondKey = mock(AEItemKey.class);
        when(firstKey.getItem()).thenReturn(Items.STICK);
        when(secondKey.getItem()).thenReturn(Items.PAPER);
        var cache = new JemiConversionCache(mock(IIngredientManager.class));
        var rawTag = TagKey.create(Registries.ITEM, new ResourceLocation("test", "alternatives"));
        var tagKey = mock(EmiTagKey.class);
        when(tagKey.id()).thenReturn(rawTag.location());
        when(tagKey.raw()).thenReturn(rawTag);
        try (var tags = mockStatic(EmiTags.class); var keys = mockStatic(EmiTagKey.class)) {
            keys.when(() -> EmiTagKey.of(rawTag)).thenReturn(tagKey);
            tags.when(() -> EmiTags.getIngredient(eq(Item.class), anyList(), eq(3L)))
                    .thenAnswer(call -> new ListEmiIngredient(
                            List.of(new TagEmiIngredient(rawTag, call.getArgument(1), 1)), 3));
            var first = cache.getIngredient(stacks(firstKey, secondKey, 3));
            first.setAmount(9).setChance(0.5f);
            first.getEmiStacks().getFirst().setRemainder(new ItemEmiStack(firstKey, 2));
            first.getEmiStacks().getFirst().setAmount(7);

            var second = cache.getIngredient(stacks(firstKey, secondKey, 3));
            assertEquals(3, second.getAmount());
            assertEquals(1, second.getChance());
            assertEquals(1, second.getEmiStacks().getFirst().getAmount());
            assertTrue(second.getEmiStacks().getFirst().getRemainder().isEmpty());
            assertSame(firstKey, second.getEmiStacks().getFirst().getAEKey());
            assertNotSame(first.getEmiStacks().getFirst(), second.getEmiStacks().getFirst());
            tags.verify(() -> EmiTags.getIngredient(eq(Item.class), anyList(), eq(3L)), times(1));
            verify(firstKey, never()).toStack(anyInt());
            verify(secondKey, never()).toStack(anyInt());

            new JemiConversionCache(mock(IIngredientManager.class)).getIngredient(stacks(firstKey, secondKey, 3));
            tags.verify(() -> EmiTags.getIngredient(eq(Item.class), anyList(), eq(3L)), times(2));
        }
    }

    @Test
    void cacheKeysKeepExactVariantsOrderAndCountsAfterSourceMutation() {
        var firstKey = mock(AEItemKey.class);
        var secondKey = mock(AEItemKey.class);
        // Two canonical variants of the same item must remain distinct.
        when(firstKey.getItem()).thenReturn(Items.STICK);
        when(secondKey.getItem()).thenReturn(Items.STICK);
        var cache = new JemiConversionCache(mock(IIngredientManager.class));
        try (var tags = mockStatic(EmiTags.class)) {
            tags.when(() -> EmiTags.getIngredient(eq(Item.class), anyList(), anyLong()))
                    .thenAnswer(call -> new ListEmiIngredient(call.getArgument(1), call.getArgument(2)));
            var source = stacks(firstKey, secondKey, 2);
            cache.getIngredient(source);
            source.getFirst().setAmount(4);
            var mixed = cache.getIngredient(source);
            assertEquals(1, mixed.getAmount());
            assertEquals(4, mixed.getEmiStacks().getFirst().getAmount());
            var original = cache.getIngredient(stacks(firstKey, secondKey, 2));
            assertEquals(2, original.getAmount());
            assertSame(secondKey, original.getEmiStacks().get(1).getAEKey());
            cache.getIngredient(stacks(secondKey, firstKey, 2));
            cache.getIngredient(stacks(firstKey, firstKey, 2));
            tags.verify(() -> EmiTags.getIngredient(eq(Item.class), anyList(), anyLong()), times(4));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void customTypeMetadataIsReusedAndTypedConversionKeepsItsDeclaredType() {
        IIngredientType<String> type = mock(IIngredientType.class);
        IIngredientHelper<String> helper = mock(IIngredientHelper.class);
        IIngredientRenderer<String> renderer = mock(IIngredientRenderer.class);
        var manager = mock(IIngredientManager.class);
        when(manager.getIngredientHelper(type)).thenReturn(helper);
        when(manager.getIngredientRenderer(type)).thenReturn(renderer);
        when(helper.isValidIngredient(anyString())).thenReturn(true);
        var cache = new JemiConversionCache(manager);
        var first = cache.getStack(type, "first");
        cache.getStack(type, "second");
        assertSame(EmiStack.EMPTY, cache.getStack(type, null));
        verify(manager, times(1)).getIngredientHelper(type);
        verify(manager, times(1)).getIngredientRenderer(type);
        var typed = mock(ITypedIngredient.class);
        when(manager.createTypedIngredient(type, "first")).thenReturn(Optional.of(typed));
        var runtime = mock(IJeiRuntime.class);
        when(runtime.getIngredientManager()).thenReturn(manager);
        var previous = JemiPlugin.runtime;
        JemiPlugin.runtime = runtime;
        try {
            assertSame(typed, JemiUtil.getTyped(first).orElseThrow());
            verify(manager, never()).getIngredientTypeChecked(any(Object.class));
        } finally {
            JemiPlugin.runtime = previous;
        }
    }

    @Test
    void itemCoercionDoesNotCreateAJeiItemStackEvenWithATooltipCallback() {
        var stack = mock(EmiStack.class);
        when(stack.getKey()).thenReturn(Items.STICK);
        var acceptor = new JemiIngredientAcceptor(RecipeIngredientRole.INPUT);
        acceptor.stacks.add(stack);
        var callback = mock(IRecipeSlotTooltipCallback.class);
        try (var util = mockStatic(JemiUtil.class)) {
            acceptor.coerceStacks(callback, null);
            util.verifyNoInteractions();
            verifyNoInteractions(callback);
            verify(stack, never()).getItemStack();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void fluidTooltipAmountCorrectionStillRuns() {
        var stack = mock(EmiStack.class);
        when(stack.getKey()).thenReturn(Fluids.WATER);
        when(stack.getName()).thenReturn(Component.literal("Water"));
        when(stack.getAmount()).thenReturn(1000L);
        var typed = mock(ITypedIngredient.class);
        var acceptor = new JemiIngredientAcceptor(RecipeIngredientRole.INPUT);
        acceptor.stacks.add(stack);
        IRecipeSlotTooltipCallback callback = (slot, tooltip) -> tooltip.set(0, Component.literal("9,000 mB"));
        try (var util = mockStatic(JemiUtil.class)) {
            util.when(() -> JemiUtil.getTyped(stack)).thenReturn(Optional.of(typed));
            acceptor.coerceStacks(callback, null);
            verify(stack).setAmount(9000);
        }
    }

    @Test
    void removesOnlyJeiItemAndBlockTagCategories() {
        assertTrue(JemiPlugin.isTagCategory(new ResourceLocation("minecraft", "tag_recipes/item")));
        assertTrue(JemiPlugin.isTagCategory(new ResourceLocation("minecraft", "tag_recipes/block")));
        assertFalse(JemiPlugin.isTagCategory(new ResourceLocation("minecraft", "tag_recipes/fluid")));
        assertFalse(JemiPlugin.isTagCategory(new ResourceLocation("other", "tag_recipes/item")));
        assertFalse(JemiPlugin.isTagCategory(new ResourceLocation("minecraft", "crafting")));
    }

    private static ObjectArrayList<EmiStack> stacks(AEItemKey first, AEItemKey second, long amount) {
        return new ObjectArrayList<>(List.of(new ItemEmiStack(first, amount), new ItemEmiStack(second, amount)));
    }
}
