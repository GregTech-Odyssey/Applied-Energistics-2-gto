package dev.emi.emi.api.stack;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ListEmiIngredientTest {
    @Test
    void copiesReuseAlternativesWithoutReExpandingAndKeepAmountsIndependent() {
        var stack = mock(EmiStack.class);
        var ingredient = mock(EmiIngredient.class);
        when(ingredient.getEmiStacks()).thenReturn(List.of(stack));
        var original = new ListEmiIngredient(List.of(ingredient), 4);
        original.setChance(0.5f);
        clearInvocations(ingredient);

        var copy = original.copy().setAmount(8).setChance(0.25f);
        assertSame(original.getEmiStacks(), copy.getEmiStacks());
        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals(original, copy);
        assertEquals(4, original.getAmount());
        assertEquals(0.5f, original.getChance());
        assertEquals(8, copy.getAmount());
        assertEquals(0.25f, copy.getChance());
        verifyNoInteractions(ingredient);
    }
}
