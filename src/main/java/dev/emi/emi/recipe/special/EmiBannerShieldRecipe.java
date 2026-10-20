package dev.emi.emi.recipe.special;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiPatternCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class EmiBannerShieldRecipe extends EmiPatternCraftingRecipe {
    public static final List<Item> BANNERS = List.of(
            Items.WHITE_BANNER, Items.ORANGE_BANNER, Items.MAGENTA_BANNER, Items.LIGHT_BLUE_BANNER,
            Items.YELLOW_BANNER, Items.LIME_BANNER, Items.PINK_BANNER, Items.GRAY_BANNER,
            Items.LIGHT_GRAY_BANNER, Items.CYAN_BANNER, Items.PURPLE_BANNER, Items.BLUE_BANNER,
            Items.BROWN_BANNER, Items.GREEN_BANNER, Items.RED_BANNER, Items.BLACK_BANNER);
    private static final List<EmiStack> EMI_BANNERS = BANNERS.stream().map(i -> EmiStack.of(i))
            .collect(Collectors.toList());
    public static final EmiStack SHIELD = EmiStack.of(Items.SHIELD);

    @SuppressWarnings("unchecked")
    public EmiBannerShieldRecipe(ResourceLocation id) {
        super(ingredients(), EmiStack.of(Items.SHIELD), id);
    }

    private static ObjectArrayList<EmiIngredient> ingredients() {
        var ingredients = new ObjectArrayList<EmiIngredient>(EMI_BANNERS.size() + 1);
        ingredients.add(SHIELD);
        ingredients.addAll(EMI_BANNERS);
        return ingredients;
    }

    @Override
    public SlotWidget getInputWidget(int slot, int x, int y) {
        if (slot == 0) {
            return new SlotWidget(SHIELD, x, y);
        } else if (slot == 1) {
            return new GeneratedSlotWidget(r -> getPattern(r, null), unique, x, y);
        }
        return new SlotWidget(EmiStack.EMPTY, x, y);
    }

    @Override
    public SlotWidget getOutputWidget(int x, int y) {
        return new GeneratedSlotWidget(r -> getPattern(r, Items.SHIELD), unique, x, y);
    }

    public EmiStack getPattern(Random random, Item item) {
        int base = random.nextInt(BANNERS.size());
        if (item == null) {
            item = BANNERS.get(base);
        }
        ItemStack stack = new ItemStack(item);
        int patterns = 1 + Math.max(random.nextInt(5), random.nextInt(3));
        BannerPattern.Builder pattern = new BannerPattern.Builder();
        for (int i = 0; i < patterns; i++) {
            pattern = EmiPort.addRandomBanner(pattern, random);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Patterns", pattern.toListTag());
        if (item == Items.SHIELD) {
            tag.putInt("Base", ((BannerItem) BANNERS.get(base)).getColor().getId());
        }
        BlockItem.setBlockEntityData(stack, BlockEntityType.BANNER, tag);
        // stack.setNbt(tag);
        return EmiStack.of(stack);
    }
}
