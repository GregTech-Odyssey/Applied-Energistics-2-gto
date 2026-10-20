package dev.emi.emi.runtime;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class RecipeShareLink {
	public static final String PREFIX = "https://emi-bookmark.invalid/r/";

	private RecipeShareLink() {
	}

	public static String create(EmiRecipe recipe) {
		if (recipe == null || recipe.getId() == null) {
			return "";
		}
		String id = recipe.getId().toString();
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
		return PREFIX + payload;
	}

	public static boolean handleClick(@Nullable Style style) {
		if (style == null || style.getClickEvent() == null) {
			return false;
		}
		ClickEvent click = style.getClickEvent();
		if (click.getAction() != ClickEvent.Action.OPEN_URL) {
			return false;
		}
		return importLink(click.getValue());
	}

	public static boolean importLink(String value) {
		EmiRecipe recipe = recipeFromLink(value);
		if (recipe == null) {
			return false;
		}
		boolean added = RecipeFavoriteActions.saveSharedRecipe(recipe);
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			String name = recipeName(recipe);
			if (added) {
				client.player.displayClientMessage(EmiPort.literal("Added EMI bookmark: " + name).withStyle(ChatFormatting.GREEN), false);
			} else {
				client.player.displayClientMessage(EmiPort.literal("EMI bookmark already exists: " + name).withStyle(ChatFormatting.YELLOW), false);
			}
		}
		return true;
	}

	public static Component decorateChatMessage(Component message) {
		if (message == null) {
			return Component.empty();
		}
		String raw = message.getString();
		int start = raw.indexOf(PREFIX);
		if (start < 0) {
			return message;
		}
		int end = start;
		while (end < raw.length() && !Character.isWhitespace(raw.charAt(end))) {
			end++;
		}
		String link = raw.substring(start, end);
		EmiRecipe recipe = recipeFromLink(link);
		if (recipe == null) {
			return message;
		}

		MutableComponent result = Component.literal(raw.substring(0, start));
		MutableComponent clickable = EmiPort.literal("[Click to add bookmark: " + recipeName(recipe) + "]");
		clickable.setStyle(Style.EMPTY
			.withColor(ChatFormatting.GOLD)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link)));
		result.append(clickable);
		if (end < raw.length()) {
			result.append(Component.literal(raw.substring(end)));
		}
		return result;
	}

	private static @Nullable EmiRecipe recipeFromLink(String value) {
		if (value == null || !value.startsWith(PREFIX)) {
			return null;
		}
		try {
			String payload = value.substring(PREFIX.length());
			int query = payload.indexOf('?');
			if (query >= 0) {
				payload = payload.substring(0, query);
			}
			String idText = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
			ResourceLocation id = new ResourceLocation(idText);
			return EmiApi.getRecipeManager().getRecipe(id);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static String recipeName(EmiRecipe recipe) {
		if (recipe == null || recipe.getOutputs().isEmpty() || recipe.getOutputs().get(0).isEmpty()) {
			return recipe != null && recipe.getId() != null ? recipe.getId().toString() : "recipe";
		}
		return recipe.getOutputs().get(0).getName().getString();
	}
}
