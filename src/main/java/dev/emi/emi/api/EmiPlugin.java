package dev.emi.emi.api;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiRegistry;

/**
 * The primary method of communicating with EMI.
 * Plugins are loaded at runtime to provide information like stacks and recipes.
 * In order to be loaded on Forge, plugins must have the {@link EmiEntrypoint} annotation.
 */
public interface EmiPlugin {

	/**
	 * The entrypoint to register information to EMI before standard registration begins.
	 * This is used for underlying information that affects registration.
	 * This includes ingredient serialization and grouping that affects all recipes.
	 */
	default void initialize(EmiInitRegistry registry) {
	}

	/**
	 * The core method through which information is registered for EMI.
	 * This includes recipe categories, recipes, recipe handlers.
	 * @see {@link EmiRegistry}
	 */
	void register(EmiRegistry registry);
}
