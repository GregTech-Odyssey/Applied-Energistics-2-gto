package dev.emi.emi.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;

class EmiRecipeIndexTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void clearWorkstations() {
        EmiRecipes.byWorkstation.clear();
    }

    @Test
    void indexesCatalystsAndDeduplicatesRepeatedIngredients() {
        var input = new TestStack(new Object(), null);
        var catalyst = new TestStack(new Object(), null);
        var output = new TestStack(new Object(), null);
        var workstation = new TestStack(new Object(), null);
        var category = mock(EmiRecipeCategory.class);
        var recipe = mock(EmiRecipe.class);
        var id = new ResourceLocation("test", "recipe");
        when(recipe.getId()).thenReturn(id);
        when(recipe.getCategory()).thenReturn(category);
        when(recipe.getInputs()).thenReturn(List.of(input, input.copy()));
        when(recipe.getCatalysts()).thenReturn(List.of(catalyst));
        when(recipe.getOutputs()).thenReturn(List.of(output));

        var index = new EmiRecipeIndex(List.of(category), Map.of(category, List.of(workstation)), List.of(recipe));

        assertEquals(List.of(recipe), index.getRecipesByInput(input.copy()));
        assertEquals(List.of(recipe), index.getRecipesByInput(catalyst.copy()));
        assertEquals(List.of(recipe), index.getRecipesByOutput(output.copy()));
        assertEquals(List.of(recipe), index.getRecipes(category));
        assertSame(recipe, index.getRecipe(id));
        assertEquals(List.of(recipe), EmiRecipes.byWorkstation.get(workstation));
        assertTrue(index.getRecipesByInput(new TestStack(new Object(), null)).isEmpty());
    }

    @Test
    void repeatedRegistrationAndOverlappingCatalystsDoNotDuplicateIndexResults() {
        var stack = new TestStack(new Object(), null);
        var category = mock(EmiRecipeCategory.class);
        var first = mock(EmiRecipe.class);
        var second = mock(EmiRecipe.class);
        for (var recipe : List.of(first, second)) {
            when(recipe.getCategory()).thenReturn(category);
            when(recipe.getInputs()).thenReturn(List.of(stack, stack.copy()));
            when(recipe.getCatalysts()).thenReturn(List.of(stack.copy()));
            when(recipe.getOutputs()).thenReturn(List.of(stack.copy(), stack.copy()));
        }
        var index = new EmiRecipeIndex(List.of(category), Map.of(), List.of(first, second, first));
        assertEquals(List.of(first, second), index.getRecipesByInput(stack));
        assertEquals(List.of(first, second), index.getRecipesByOutput(stack));
    }

    @Test
    void strictComparisonKeepsNbtVariantsSeparate() {
        var key = new Object();
        var firstTag = new CompoundTag();
        firstTag.putInt("variant", 1);
        var secondTag = new CompoundTag();
        secondTag.putInt("variant", 2);
        var first = new TestStack(key, firstTag);
        var identical = new TestStack(key, firstTag.copy());
        var different = new TestStack(key, secondTag);

        assertEquals(Comparison.compareNbt().getHash(first), Comparison.compareNbt().getHash(identical));
        assertTrue(first.isEqual(different, Comparison.DEFAULT_COMPARISON));
    }

    private static final class TestStack extends EmiStack {
        private final Object key;
        private final CompoundTag tag;

        TestStack(Object key, CompoundTag tag) {
            this.key = key;
            this.tag = tag;
        }

        @Override
        public EmiStack copy() {
            return new TestStack(key, tag);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public CompoundTag getNbt() {
            return tag;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("test", "stack");
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
        }

        @Override
        public List<Component> getTooltipText() {
            return List.of();
        }

        @Override
        public Component getName() {
            return Component.literal("test");
        }
    }
}
