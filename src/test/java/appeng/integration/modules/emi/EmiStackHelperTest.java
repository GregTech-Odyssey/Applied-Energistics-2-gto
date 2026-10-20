package appeng.integration.modules.emi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyIntMap;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;

class EmiStackHelperTest {
    @Test
    void largeAmountsDoNotOverflowTheItemStackRenderingBridge() {
        var key = mock(AEItemKey.class);
        var rendered = mock(ItemStack.class);
        when(key.toStack(Integer.MAX_VALUE)).thenReturn(rendered);
        assertSame(rendered, new ItemEmiStack(key, 3_000_000_000L).getItemStack());
        assertSame(rendered, new ItemEmiStack(key, Long.MAX_VALUE).getItemStack());
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void conversionReusesCanonicalKeyAndPreservesAmountAndNbt() {
        var tag = new CompoundTag();
        tag.putInt("variant", 7);
        var key = mock(AEItemKey.class);
        when(key.getItem()).thenReturn(Items.STICK);
        when(key.getTag()).thenReturn(tag);
        var original = new GenericStack(key, 1234);
        var emi = EmiStackHelper.toEmiStack(original);
        assertSame(key, EmiStackHelper.toAEKey(emi));
        assertEquals(original, EmiStackHelper.toGenericStack(emi));
        assertSame(Items.STICK, emi.getKey());
        assertEquals(tag, emi.getNbt());
        assertNull(EmiStackHelper.toAEKey(EmiStack.EMPTY));
    }

    @Test
    void selectionKeepsFirstTieAndAcceptsLowestExplicitPriority() {
        var stick = new GenericStack(mock(AEKey.class), 1);
        var paper = new GenericStack(mock(AEKey.class), 2);
        var priorities = new AEKeyIntMap<AEKey>(2);
        assertSame(stick, EncodingHelper.findBestIngredient(priorities, List.of(stick, paper)));
        priorities.put(paper.what(), -1);
        assertSame(paper, EncodingHelper.findBestIngredient(priorities, List.of(stick, paper)));
        priorities.put(stick.what(), -1);
        assertSame(stick, EncodingHelper.findBestIngredient(priorities, List.of(stick, paper)));
    }
}
