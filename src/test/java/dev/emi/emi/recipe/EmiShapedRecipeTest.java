package dev.emi.emi.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import appeng.api.stacks.AEItemKey;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;

class EmiShapedRecipeTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void vanillaRecipesEvaluateEachAlternativeOnAnOwnedStackWithoutSimulatingOtherSlots() {
        for (CraftingRecipe recipe : List.of(mock(ShapedRecipe.class), mock(ShapelessRecipe.class))) {
            var key = mock(AEItemKey.class);
            var readOnly = mock(ItemStack.class);
            var writable = mock(ItemStack.class);
            var remainder = mock(ItemStack.class);
            var displayed = mock(EmiStack.class);
            when(key.getReadOnlyStack()).thenReturn(readOnly);
            when(key.toStack(1)).thenReturn(writable);
            when(readOnly.hasCraftingRemainingItem()).thenReturn(true);
            when(writable.getCraftingRemainingItem()).thenReturn(remainder);
            var first = new ItemEmiStack(key, 1);
            var second = new ItemEmiStack(key, 1);
            try (var stacks = mockStatic(EmiStack.class)) {
                stacks.when(() -> EmiStack.of(remainder)).thenReturn(displayed);
                EmiShapedRecipe.setRemainders(List.of(first, second), recipe);
            }
            assertSame(displayed, first.getRemainder());
            assertSame(displayed, second.getRemainder());
            verify(key, times(2)).toStack(1);
            verify(writable, times(2)).getCraftingRemainingItem();
            verify(readOnly, never()).getCraftingRemainingItem();
            verify(recipe, never()).getRemainingItems(any());
        }
    }

    @Test
    void countSensitiveAlternativesAndEmptyRemaindersRemainDistinct() {
        var key = mock(AEItemKey.class);
        when(key.getItem()).thenReturn(Items.STICK);
        var ordinary = mock(ItemStack.class);
        var counted = mock(ItemStack.class);
        when(key.getReadOnlyStack()).thenReturn(ordinary);
        when(key.toStack(2)).thenReturn(counted);
        when(counted.hasCraftingRemainingItem()).thenReturn(true);
        when(counted.getCraftingRemainingItem()).thenReturn(ItemStack.EMPTY);
        var first = new ItemEmiStack(key, 1);
        var second = new ItemEmiStack(key, 2);
        EmiShapedRecipe.setRemainders(List.of(new ListEmiIngredient(List.of(first, second), 1)),
                mock(ShapedRecipe.class));
        assertSame(EmiStack.EMPTY, first.getRemainder());
        assertSame(EmiStack.EMPTY, second.getRemainder());
        verify(key, never()).toStack(1);
        verify(ordinary, never()).getCraftingRemainingItem();
        verify(counted).getCraftingRemainingItem();
    }

    @Test
    void customShapedRecipeStillReceivesTheFullGridForEveryAlternative() {
        var recipe = mock(ShapedRecipe.class, withSettings().extraInterfaces(Runnable.class));
        var first = mock(EmiStack.class);
        var second = mock(EmiStack.class);
        var other = mock(EmiStack.class);
        var firstInput = mock(ItemStack.class);
        var secondInput = mock(ItemStack.class);
        var otherInput = mock(ItemStack.class);
        when(first.getEmiStacks()).thenReturn(List.of(first));
        when(second.getEmiStacks()).thenReturn(List.of(second));
        when(first.getItemStack()).thenReturn(firstInput);
        when(second.getItemStack()).thenReturn(secondInput);
        when(other.getItemStack()).thenReturn(otherInput);
        when(firstInput.hasCraftingRemainingItem()).thenReturn(true);
        when(other.getEmiStacks()).thenReturn(List.of(other));
        var firstRemainder = mock(ItemStack.class);
        var secondRemainder = mock(ItemStack.class);
        var firstDisplayed = mock(EmiStack.class);
        var secondDisplayed = mock(EmiStack.class);
        when(recipe.getRemainingItems(any())).thenAnswer(call -> {
            CraftingContainer grid = call.getArgument(0);
            assertSame(otherInput, grid.getItem(1));
            var remainders = NonNullList.withSize(9, ItemStack.EMPTY);
            remainders.set(0, grid.getItem(0) == firstInput ? firstRemainder : secondRemainder);
            return remainders;
        });
        try (var stacks = mockStatic(EmiStack.class)) {
            stacks.when(() -> EmiStack.of(firstRemainder)).thenReturn(firstDisplayed);
            stacks.when(() -> EmiStack.of(secondRemainder)).thenReturn(secondDisplayed);
            EmiShapedRecipe.setRemainders(List.<EmiIngredient>of(
                    new ListEmiIngredient(List.of(first, second), 1), other), recipe);
        }
        verify(recipe, times(2)).getRemainingItems(any());
        verify(first).setRemainder(firstDisplayed);
        verify(second).setRemainder(secondDisplayed);
        verify(firstInput, never()).getCraftingRemainingItem();
    }

    @Test
    void bucketRemaindersMatchTheDefaultRecipeAndToolDamageNeverTouchesTheReadOnlyTemplate() {
        var bucketKey = mock(AEItemKey.class);
        var bucket = new ItemStack(Items.MILK_BUCKET);
        when(bucketKey.getItem()).thenReturn(Items.MILK_BUCKET);
        when(bucketKey.getReadOnlyStack()).thenReturn(bucket);
        when(bucketKey.toStack(1)).thenAnswer(call -> bucket.copy());
        var toolKey = mock(AEItemKey.class);
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(7);
        var tag = new CompoundTag();
        tag.putInt("variant", 9);
        tool.getOrCreateTag().put("custom", tag);
        when(toolKey.getItem()).thenReturn(Items.IRON_PICKAXE);
        var toolCheck = mock(ItemStack.class);
        when(toolCheck.hasCraftingRemainingItem()).thenReturn(true);
        when(toolKey.getReadOnlyStack()).thenReturn(toolCheck);
        when(toolKey.toStack(1)).thenAnswer(call -> {
            var writable = spy(tool.copy());
            doReturn(true).when(writable).hasCraftingRemainingItem();
            doAnswer(remainder -> {
                var result = writable.copy();
                result.setDamageValue(result.getDamageValue() + 1);
                return result;
            }).when(writable).getCraftingRemainingItem();
            return writable;
        });
        var recipe = new ShapedRecipe(new ResourceLocation("test", "remainders"), "", CraftingBookCategory.MISC,
                2, 1, NonNullList.withSize(2, Ingredient.EMPTY), ItemStack.EMPTY);
        var grid = EmiUtil.getCraftingInventory();
        grid.setItem(0, bucketKey.toStack(1));
        grid.setItem(1, toolKey.toStack(1));
        var expected = recipe.getRemainingItems(grid);
        var bucketInput = new ItemEmiStack(bucketKey, 1);
        var toolInput = new ItemEmiStack(toolKey, 1);
        try (var stacks = mockStatic(EmiStack.class)) {
            stacks.when(() -> EmiStack.of(any(ItemStack.class))).thenAnswer(call -> {
                ItemStack remainder = call.getArgument(0);
                int slot = remainder.is(Items.BUCKET) ? 0 : 1;
                assertTrue(ItemStack.matches(expected.get(slot), remainder));
                return slot == 0 ? bucketInput.copy() : toolInput.copy();
            });
            EmiShapedRecipe.setRemainders(List.of(bucketInput, toolInput), recipe);
            stacks.verify(() -> EmiStack.of(any(ItemStack.class)), times(2));
        }
        assertEquals(7, tool.getDamageValue());
        assertEquals(9, tool.getTag().getCompound("custom").getInt("variant"));
        verify(toolCheck, never()).getCraftingRemainingItem();
    }
}
