package dev.emi.emi.api.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;

class EmiPlayerInventoryTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reservationsDoNotLeakBetweenSuccessFailureAndAvailabilityQueries() {
        var stack = mock(EmiStack.class);
        when(stack.getKey()).thenReturn(new Object());
        when(stack.getAmount()).thenReturn(1L);
        when(stack.getEmiStacks()).thenReturn(List.of(stack));
        when(stack.getRemainder()).thenReturn(EmiStack.EMPTY);
        var oneInput = mock(EmiRecipe.class);
        when(oneInput.getInputs()).thenReturn(List.of(stack));
        var twoInputs = mock(EmiRecipe.class);
        when(twoInputs.getInputs()).thenReturn(List.of(stack, stack));
        try (var api = mockStatic(EmiApi.class)) {
            var inventory = new EmiPlayerInventory(List.of(stack));
            assertTrue(inventory.canCraft(oneInput));
            assertFalse(inventory.canCraft(twoInputs));
            assertEquals(List.of(true, false), inventory.getCraftAvailability(twoInputs));
            assertTrue(inventory.canCraft(oneInput));
            assertFalse(inventory.canCraft(oneInput, 2));
            assertTrue(inventory.canCraft(oneInput));
        }
    }

    @Test
    void craftingRemaindersDoNotBypassInputQuantityChecks() {
        var stack = mock(EmiStack.class);
        when(stack.getKey()).thenReturn(new Object());
        when(stack.getAmount()).thenReturn(1L);
        when(stack.getEmiStacks()).thenReturn(List.of(stack));
        when(stack.getRemainder()).thenReturn(stack);
        var recipe = mock(EmiRecipe.class);
        when(recipe.getInputs()).thenReturn(List.of(stack));
        try (var api = mockStatic(EmiApi.class)) {
            var inventory = new EmiPlayerInventory(List.of(stack));
            assertTrue(inventory.canCraft(recipe));
            assertFalse(inventory.canCraft(recipe, 2));
            assertEquals(List.of(true), inventory.getCraftAvailability(recipe));
        }
    }
}
