package dev.emi.emi.recipe.special;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

public class EmiAnvilEnchantRecipe implements EmiRecipe {
    private final Item tool;
    private final Enchantment enchantment;
    private final int level;
    private final ResourceLocation id;

    public EmiAnvilEnchantRecipe(Item tool, Enchantment enchantment, int level, ResourceLocation id) {
        this.tool = tool;
        this.enchantment = enchantment;
        this.level = level;
        this.id = id;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return VanillaEmiRecipeCategories.ANVIL_REPAIRING;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(EmiStack.of(tool), getBook());
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(EmiStack.of(tool));
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public int getDisplayWidth() {
        return 125;
    }

    @Override
    public int getDisplayHeight() {
        return 18;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(EmiTexture.PLUS, 27, 3);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 75, 1);
        widgets.addSlot(EmiStack.of(tool), 0, 0);
        widgets.addSlot(getBook(), 49, 0);
        widgets.addSlot(EmiStack.of(getTool()), 107, 0).recipeContext(this);
    }

    private ItemStack getTool() {
        ItemStack itemStack = tool.getDefaultInstance();
        itemStack.enchant(enchantment, level);
        return itemStack;
    }

    private EmiStack getBook() {
        ItemStack item = new ItemStack(Items.ENCHANTED_BOOK);
        CompoundTag tag = new CompoundTag();
        ListTag StoredEnchantments = new ListTag();
        CompoundTag enchant = new CompoundTag();
        String id = getTool().getTag().getList("Enchantments", Tag.TAG_COMPOUND).getCompound(0).getString("id");

        enchant.putString("id", id);
        enchant.putShort("lvl", (short) level);
        StoredEnchantments.add(enchant);
        tag.put("StoredEnchantments", StoredEnchantments);
        item.setTag(tag);
        return EmiStack.of(item);
    }
}
