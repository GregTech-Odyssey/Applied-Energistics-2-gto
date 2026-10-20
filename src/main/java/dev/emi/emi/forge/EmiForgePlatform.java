package dev.emi.emi.forge;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import appeng.mixins.emi.accessor.BakedModelManagerAccessor;
import dev.emi.emi.recipe.EmiBrewingRecipe;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.runtime.EmiLog;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.commons.lang3.text.WordUtils;
import org.objectweb.asm.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class EmiForgePlatform {
	private EmiForgePlatform() {
	}

	@SuppressWarnings("deprecation")
	public static String getModName(String namespace) {
		Optional<? extends ModContainer> container = ModList.get().getModContainerById(namespace);
		if (container.isPresent()) {
			return container.get().getModInfo().getDisplayName();
		}
		container = ModList.get().getModContainerById(namespace.replace('_', '-'));
		if (container.isPresent()) {
			return container.get().getModInfo().getDisplayName();
		}
		return WordUtils.capitalizeFully(namespace.replace('_', ' '));
	}

	public static Path getConfigDirectory() {
		return FMLPaths.CONFIGDIR.get();
	}

	public static boolean isDevelopmentEnvironment() {
		return !FMLLoader.isProduction();
	}

	public static boolean isModLoaded(String id) {
		return ModList.get().isLoaded(id);
	}

	public static List<String> getAllModNames() {
		return ModList.get().getMods().stream().map(m -> m.getDisplayName()).toList();
	}

	public static List<String> getModsWithPlugins() {
		List<String> mods = Lists.newArrayList();
		Type entrypointType = Type.getType(EmiEntrypoint.class);
		for (ModFileScanData data : ModList.get().getAllScanData()) {
			for (ModFileScanData.AnnotationData annot : data.getAnnotations()) {
				try {
					if (entrypointType.equals(annot.annotationType())) {
						mods.add(data.getIModInfoData().getFirst().getMods().getFirst().getModId());
					}
				} catch (Throwable t) {
					EmiLog.error("Exception constructing entrypoint:");
					t.printStackTrace();
				}
			}
		}
		return mods;
	}

	public static List<EmiPluginContainer> getPlugins() {
		List<EmiPluginContainer> containers = Lists.newArrayList();
		Type entrypointType = Type.getType(EmiEntrypoint.class);
		for (ModFileScanData data : ModList.get().getAllScanData()) {
			for (ModFileScanData.AnnotationData annot : data.getAnnotations()) {
				try {
					if (entrypointType.equals(annot.annotationType())) {
						Class<?> clazz = Class.forName(annot.memberName());
						if (EmiPlugin.class.isAssignableFrom(clazz)) {
							Class<? extends EmiPlugin> pluginClass = clazz.asSubclass(EmiPlugin.class);
							EmiPlugin plugin = pluginClass.getConstructor().newInstance();
							String id = data.getIModInfoData().get(0).getMods().get(0).getModId();
							containers.add(new EmiPluginContainer(plugin, id));
						} else {
							EmiLog.error("EmiEntrypoint " + annot.memberName() + " does not implement EmiPlugin");
						}
					}
				} catch (Throwable t) {
					EmiLog.error("Exception constructing entrypoint:", t);
				}
			}
		}
		return containers;
	}

	public static void addBrewingRecipes(EmiRegistry registry) {
		for (Ingredient ingredient : PotionBrewing.ALLOWED_CONTAINERS) {
			for (ItemStack stack : ingredient.getItems()) {
				String pid = EmiUtil.subId(stack.getItem());
				for (PotionBrewing.Mix<Potion> recipe : PotionBrewing.POTION_MIXES) {
					try {
						if (recipe.ingredient.getItems().length > 0) {
							ResourceLocation id = EmiPort.id("emi", "/brewing/" + pid
								+ "/" + EmiUtil.subId(recipe.ingredient.getItems()[0].getItem())
								+ "/" + EmiUtil.subId(EmiPort.getPotionRegistry().getKey(recipe.from.get()))
								+ "/" + EmiUtil.subId(EmiPort.getPotionRegistry().getKey(recipe.to.get())));
							registry.addRecipe(new EmiBrewingRecipe(
								EmiStack.of(EmiPort.setPotion(stack.copy(), recipe.from.get())), EmiIngredient.of(recipe.ingredient),
								EmiStack.of(EmiPort.setPotion(stack.copy(), recipe.to.get())), id));
						}
					} catch (Exception e) {
						EmiLog.error("Error registering brewing recipe", e);
					}
				}
			}
		}

		for (PotionBrewing.Mix<Item> recipe : PotionBrewing.CONTAINER_MIXES) {
			try {
				if (recipe.ingredient.getItems().length > 0) {
					String gid = EmiUtil.subId(recipe.ingredient.getItems()[0].getItem());
					String iid = EmiUtil.subId(recipe.from.get());
					String oid = EmiUtil.subId(recipe.to.get());
					Consumer<Holder<Potion>> potionRecipeGen = entry -> {
						Potion potion = entry.value();
						if (potion == Potions.EMPTY) {
							return;
						}
						if (PotionBrewing.isBrewablePotion(potion)) {
							ResourceLocation id = EmiPort.id("emi", "brewing/item/"
								+ EmiUtil.subId(entry.unwrapKey().get().location()) + "/" + gid + "/" + iid + "/" + oid);
							registry.addRecipe(new EmiBrewingRecipe(
								EmiStack.of(EmiPort.setPotion(new ItemStack(recipe.from.get()), potion)), EmiIngredient.of(recipe.ingredient),
								EmiStack.of(EmiPort.setPotion(new ItemStack(recipe.to.get()), potion)), id));
						}
					};
					if ((recipe.from.get() instanceof PotionItem)) {
						EmiPort.getPotionRegistry().holders().forEach(potionRecipeGen);
					} else {
						potionRecipeGen.accept(EmiPort.getPotionRegistry().wrapAsHolder(Potions.AWKWARD));
					}

				}
			} catch (Exception e) {
				EmiLog.error("Error registering brewing recipe", e);
			}
		}
		for (IBrewingRecipe ibr : net.minecraftforge.common.brewing.BrewingRecipeRegistry.getRecipes()) {
			try {
				if (ibr instanceof BrewingRecipe recipe) {
					for (ItemStack is : recipe.getInput().getItems()) {
						EmiStack input = EmiStack.of(is);
						EmiIngredient ingredient = EmiIngredient.of(recipe.getIngredient());
						EmiStack output = EmiStack.of(recipe.getOutput(is, recipe.getIngredient().getItems()[0]));
						ResourceLocation id = EmiPort.id("emi", "brewing/forge/"
							+ EmiUtil.subId(input.getId()) + "/"
							+ EmiUtil.subId(ingredient.getEmiStacks().get(0).getId()) + "/"
							+ EmiUtil.subId(output.getId()));
						registry.addRecipe(new EmiBrewingRecipe(input, ingredient, output, id));
					}
				}
			} catch (Exception e) {
				EmiLog.error("Error registering brewing recipe", e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static List<String> getAllModAuthors() {
		return ModList.get().getMods().stream().flatMap(m -> {
			Optional<Object> opt = m.getConfig().getConfigElement("authors");
			if (opt.isPresent()) {
				Object obj = opt.get();
				if (obj instanceof String authors) {
					return Lists.newArrayList(authors.split("\\,")).stream().map(s -> s.trim());
				} else if (obj instanceof List<?> list) {
					if (list.size() > 0 && list.get(0) instanceof String) {
						List<String> authors = (List<String>) list;
						return authors.stream();
					}
				}
			}
			return Stream.empty();
		}).distinct().toList();
	}

	public static List<ClientTooltipComponent> getItemTooltip(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		return ForgeHooksClient.gatherTooltipComponents(stack, Screen.getTooltipFromItem(client, stack), stack.getTooltipImage(), 0, Integer.MAX_VALUE, Integer.MAX_VALUE, client.font);
	}

	public static Component getFluidName(Fluid fluid, CompoundTag nbt) {
		return new FluidStack(fluid, 1000, nbt).getDisplayName();
	}

	public static List<Component> getFluidTooltip(Fluid fluid, CompoundTag nbt) {
        var name = getFluidName(fluid, nbt);
        if (Minecraft.getInstance().options.advancedItemTooltips) {
            return List.of(name, Component.literal(fluid.getFluidType().toString())
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        return List.of(name);
	}

	public static boolean isFloatyFluid(FluidEmiStack stack) {
		FluidStack fs = new FluidStack(stack.getKeyOfType(Fluid.class), 1000, stack.getNbt());
		return fs.getFluid().getFluidType().isLighterThanAir();
	}

	public static void renderFluid(FluidEmiStack stack, PoseStack matrices, int x, int y, float delta) {
		renderFluid(stack, matrices, x, y, delta, 0, 0, 16, 16);
	}

	public static void renderFluid(FluidEmiStack stack, PoseStack matrices, int x, int y, float delta, int xOff, int yOff, int width, int height) {
		FluidStack fs = new FluidStack(stack.getKeyOfType(Fluid.class), 1000, stack.getNbt());
		IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fs.getFluid());
		ResourceLocation texture = ext.getStillTexture(fs);
		int color = ext.getTintColor(fs);
		Minecraft client = Minecraft.getInstance();
		TextureAtlasSprite sprite = client.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
		EmiRenderHelper.drawTintedSprite(matrices, sprite, color, x, y, xOff, yOff, width, height);
	}

	public static boolean canBatch(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		ItemRenderer ir = client.getItemRenderer();
		BakedModel model = ir.getModel(stack, client.level, null, 0);
		return model != null && model.getClass() == SimpleBakedModel.class;
	}

	public static Map<Item, Integer> getFuelMap() {
		Object2IntMap<Item> fuelMap = new Object2IntOpenHashMap<>();
		for (Item item : EmiPort.getItemRegistry()) {
			int time = ForgeHooks.getBurnTime(item.getDefaultInstance(), null);
			if (time > 0) {
				fuelMap.put(item, time);
			}
		}
		return fuelMap;
	}

	public static BakedModel getBakedTagModel(ResourceLocation id) {
		Minecraft client = Minecraft.getInstance();
		return ((BakedModelManagerAccessor) client.getModelManager()).getModels()
			.getOrDefault(id, client.getModelManager().getMissingModel());
	}

	public static boolean isEnchantable(ItemStack stack, Enchantment enchantment) {
		ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
		enchantedBook.enchant(enchantment, enchantment.getMaxLevel());
		return stack.isBookEnchantable(enchantedBook);
	}
}
