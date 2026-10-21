package dev.emi.emi.runtime;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.registry.*;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.HashMap;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class EmiReloadManager {
    private static int loadedResourcesMask = 0;
    private static volatile boolean clear = false, restart = false;
    // 0 - empty, 1 - reloading, 2 - loaded, -1 - error
    private static volatile int status = 0;
    private static Thread thread;
    public static volatile Component reloadStep = EmiPort.literal("");
    public static volatile long reloadWorry = Long.MAX_VALUE;

    public static void reloadTags() {
        loadedResourcesMask |= 1;
        if (loadedResourcesMask == 3) {
            EmiLog.info("Recipes synchronized, reloading EMI");
            loadedResourcesMask = 0;
            reload();
        } else {
            EmiLog.info("Recipes synchronized, waiting for tags to reload EMI...");
        }
    }

    public static void reloadRecipes() {
        loadedResourcesMask |= 2;
        if (loadedResourcesMask == 3) {
            EmiLog.info("Tags synchronized, reloading EMI");
            loadedResourcesMask = 0;
            reload();
        } else {
            EmiLog.info("Tags synchronized, waiting for recipes to reload EMI...");
        }
    }

    public static void clear() {
        synchronized (EmiReloadManager.class) {
            loadedResourcesMask = 0;
            clear = true;
            status = 0;
            reloadWorry = Long.MAX_VALUE;
            if (thread != null && thread.isAlive()) {
                restart = true;
            } else {
                thread = new Thread(new ReloadWorker(), "EMI Loader");
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    public static void reload() {
        synchronized (EmiReloadManager.class) {
            step(EmiPort.translatable("emi.reload.starting"));
            status = 1;
            if (thread != null && thread.isAlive()) {
                restart = true;
            } else {
                clear = false;
                thread = new Thread(new ReloadWorker(), "EMI Loader");
                thread.setDaemon(false);
                thread.start();
            }
        }
    }

    public static void step(Component text) {
        step(text, 5_000);
    }

    public static void step(Component text, long worry) {
        EmiLog.info(text.getString());
        reloadStep = text;
        reloadWorry = System.currentTimeMillis() + worry;
    }

    public static boolean isLoaded() {
        return status == 2 && (thread == null || !thread.isAlive());
    }

    public static int getStatus() {
        return status;
    }

    private static class ReloadWorker implements Runnable {

        @Override
        public void run() {
            int retries = 3;
            outer:
            do {
                CompletableFuture<Void> jeiLoad = null;
                try {
                    if (!clear) {
                        EmiLog.info("Starting EMI reload...");
                    }
                    long reloadStart = System.currentTimeMillis();
                    restart = false;
                    step(EmiPort.translatable("emi.reload.clearing"));
                    EmiRecipes.clear();
                    EmiStackList.clear();
                    EmiIngredientSerializers.clear();
                    EmiExclusionAreas.clear();
                    EmiDragDropHandlers.clear();
                    EmiStackProviders.clear();
                    EmiRecipeFiller.clear();
                    EmiHidden.clear();
                    EmiTags.ADAPTERS_BY_CLASS.map().clear();
                    EmiTags.ADAPTERS_BY_REGISTRY.clear();
                    EmiScreenBase.clearScreenBoundsProviders();
                    if (clear) {
                        clear = false;
                        continue;
                    }
                    Minecraft client = Minecraft.getInstance();
                    if (client.level == null) {
                        EmiReloadLog.warn("World is null");
                        break;
                    } else if (client.level.getRecipeManager() == null) {
                        EmiReloadLog.warn("Recipe Manager is null");
                        break;
                    }
                    var level = client.level;
                    var plugins = new ObjectArrayList<>(EmiForgePlatform.getPlugins());
                    plugins.sort(Comparator.comparingInt(ReloadWorker::entrypointPriority));

                    JemiPlugin jemi = null;
                    if (EmiForgePlatform.isModLoaded("jei")) {
                        jemi = new JemiPlugin();
                        jeiLoad = JemiPlugin.startDeferredJeiAsync();
                    }
                    EmiInitRegistry initRegistry = new EmiInitRegistryImpl();
                    for (EmiPluginContainer container : plugins) {
                        step(EmiPort.translatable("emi.reload.initializing_plugin", container.id()), 5_000);
                        long start = System.currentTimeMillis();
                        try {
                            container.plugin().initialize(initRegistry);
                        } catch (Throwable e) {
                            EmiReloadLog.warn("Exception initializing plugin provided by " + container.id(), e);
                            if (restart) {
                                continue outer;
                            }
                            continue;
                        }
                        EmiLog.info("Initialized plugin from " + container.id() + " in " + (System.currentTimeMillis() - start) + "ms");
                    }
                    EmiHidden.reload();

                    step(EmiPort.translatable("emi.reload.tags"));
                    EmiTags.reload();

                    step(EmiPort.translatable("emi.reload.constructing_index"));
                    EmiComparisonDefaults.comparisons = new HashMap<>();
                    EmiStackList.reload();
                    if (restart) {
                        continue;
                    }
                    EmiRegistry registry = new EmiRegistryImpl();

                    for (EmiPluginContainer container : plugins) {
                        if (container.plugin() instanceof JemiPlugin)
                            continue;
                        step(EmiPort.translatable("emi.reload.loading_plugin", container.id()), 10_000);
                        long start = System.currentTimeMillis();
                        try {
                            container.plugin().register(registry);
                        } catch (Throwable e) {
                            EmiReloadLog.warn("Exception loading plugin provided by " + container.id(), e);
                            if (restart) {
                                continue outer;
                            }
                            continue;
                        }
                        EmiLog.info("Reloaded plugin from " + container.id() + " in " + (System.currentTimeMillis() - start) + "ms");
                        if (restart) {
                            continue outer;
                        }
                    }
                    if (restart) {
                        continue;
                    }
                    // Only this worker writes to the EMI registry. Join JEI before JEMI conversion.
                    if (jeiLoad != null) {
                        step(EmiPort.translatable("emi.reload.jei.waiting"), 20_000);
                        jeiLoad.join();
                    }
                    if (restart || client.level != level)
                        continue;
                    if (jemi != null) {
                        long start = System.currentTimeMillis();
                        jemi.register(registry);
                        EmiLog.info("Converted JEMI recipes in " + (System.currentTimeMillis() - start) + "ms");
                    }
                    if (restart || client.level != level)
                        continue;
                    step(EmiPort.translatable("emi.reload.baking_index"));
                    EmiStackList.bake();
                    step(EmiPort.translatable("emi.reload.late_recipes"), 10_000);
                    Consumer<EmiRecipe> registerLateRecipe = registry::addRecipe;
                    for (Consumer<Consumer<EmiRecipe>> consumer : EmiRecipes.lateRecipes) {
                        try {
                            consumer.accept(registerLateRecipe);
                        } catch (Exception e) {
                            EmiReloadLog.warn("Exception loading late recipes for plugins:", e);
                            if (restart) {
                                continue outer;
                            }
                        }
                    }
                    step(EmiPort.translatable("emi.reload.baking_recipes"), 15_000);
                    EmiRecipes.bake();
                    BoM.reload();
                    EmiPersistentData.load();
                    step(EmiPort.translatable("emi.reload.baking_search"), 15_000);
                    EmiSearch.bakeAsync();
                    step(EmiPort.translatable("emi.reload.finishing"));
                    EmiScreenManager.search.update();
                    EmiScreenManager.forceRecalculate();
                    EmiReloadLog.bake();
                    EmiLog.info("Reloaded EMI in " + (System.currentTimeMillis() - reloadStart) + "ms");
                    status = 2;
                } catch (Throwable e) {
                    if (restart || clear)
                        continue;
                    EmiReloadLog.warn("Critical error occured during reload:", e);
                    status = -1;
                    if (retries-- > 0) {
                        restart = true;
                    }
                } finally {
                    // A failed/restarted native load must drain its workers before clearing shared state again.
                    awaitCompletion(jeiLoad);
                }
            } while (restart);
            thread = null;
        }

        private static void awaitCompletion(CompletableFuture<?> task) {
            if (task != null)
                task.handle((result, error) -> null).join();
        }

        private final static int entrypointPriority(EmiPluginContainer container) {
            return container.id().equals("emi") ? 0 : 1;
        }
    }
}
