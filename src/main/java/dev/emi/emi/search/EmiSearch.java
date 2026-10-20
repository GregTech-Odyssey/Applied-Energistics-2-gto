package dev.emi.emi.search;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.data.EmiAlias;
import dev.emi.emi.data.EmiData;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.screen.EmiScreenManager;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.searchtree.SuffixArray;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class EmiSearch {
    public static final Pattern TOKENS = Pattern.compile(
            "-?[@#$]?" // Any query can be negated or prefixed with type
                    + "(" // Query contents
                    + "\\/(\\\\.|[^\\\\\\/])+\\/" // Any regex contents, for example `/some thing/`
                    + "|"
                    + "\\\"(\\.|[^\\\"])+\\\"" // Any quoted contents, for example, `"some thing"`
                    + "|"
                    + "[^\\s|]+" // Any raw contents, split on space
                    + "|"
                    + "\\|" // Literal OR symbol
                    + "|"
                    + "\\&" // Literal AND symbol (currently ignored since queries AND by deafult, but parsed)
                    + ")");
    private static volatile SearchWorker currentWorker = null;
    public static volatile Thread searchThread = null;
    public static volatile List<? extends EmiIngredient> stacks = EmiStackList.stacks;
    public static volatile CompiledQuery compiledQuery;
    public static Set<EmiStack> bakedStacks;
    public static SuffixArray<SearchStack> names, tooltips, mods;
    public static SuffixArray<EmiStack> aliases;

    public static volatile boolean searchBaked;
    public static volatile boolean searchBaking;
    private static final Object BAKE_LOCK = new Object();
    private static volatile String bakedKey;
    private static volatile List<EmiStack> bakedSource;

    public static void bakeAsync() {
        if (searchBaked && EmiStackList.stacks == bakedSource && searchKey().equals(bakedKey))
            return;
        var worker = new Thread(EmiSearch::bakeSearch, "EMI Search Bake");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.setDaemon(true);
        worker.start();
    }

    private static void bakeSearch() {
        synchronized (BAKE_LOCK) {
            var key = searchKey();
            if (searchBaked && EmiStackList.stacks == bakedSource && key.equals(bakedKey))
                return;
            boolean appendItemModId = EmiConfig.appendItemModId;
            searchBaking = true;
            try {
                var source = EmiStackList.stacks;
                bake();
                bakedSource = source;
                bakedKey = key;
                searchBaked = true;
            } catch (Throwable error) {
                EmiLog.error("Error when baking search:", error);
            } finally {
                EmiConfig.appendItemModId = appendItemModId;
                searchBaking = false;
            }
        }
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null)
            client.execute(EmiSearch::update);
    }

    private static String searchKey() {
        var client = net.minecraft.client.Minecraft.getInstance();
        return client.getLanguageManager().getSelected() + '|'
                + String.join(",", client.getResourcePackRepository().getSelectedIds());
    }

    public static void bake() {
        SuffixArray<SearchStack> names = new SuffixArray<>();
        SuffixArray<SearchStack> tooltips = new SuffixArray<>();
        SuffixArray<SearchStack> mods = new SuffixArray<>();
        SuffixArray<EmiStack> aliases = new SuffixArray<>();
        var bakedStacks = new ReferenceOpenHashSet<EmiStack>(EmiStackList.stacks.size());
        boolean old = EmiConfig.appendItemModId;
        EmiConfig.appendItemModId = false;
        for (EmiStack stack : EmiStackList.stacks) {
            try {
                SearchStack searchStack = new SearchStack(stack);
                bakedStacks.add(stack);
                Component name = NameQuery.getText(stack);
                if (name != null) {
                    names.add(searchStack, name.getString().toLowerCase());
                }
                List<Component> tooltip = stack.getTooltipText();
                if (tooltip != null) {
                    for (int i = 1; i < tooltip.size(); i++) {
                        Component text = tooltip.get(i);
                        if (text != null) {
                            tooltips.add(searchStack, text.getString().toLowerCase());
                        }
                    }
                }
                ResourceLocation id = stack.getId();
                if (id != null) {
                    mods.add(searchStack, EmiUtil.getModName(id.getNamespace()).toLowerCase());
                    mods.add(searchStack, id.getNamespace().toLowerCase());
                    names.add(searchStack, id.getPath().toLowerCase());
                }
                if (stack.getItemStack().getItem() == Items.ENCHANTED_BOOK) {
                    for (Enchantment e : EnchantmentHelper.getEnchantments(stack.getItemStack()).keySet()) {
                        ResourceLocation eid = EmiPort.getEnchantmentRegistry().getKey(e);
                        if (eid != null && !eid.getNamespace().equals("minecraft")) {
                            mods.add(searchStack, EmiUtil.getModName(eid.getNamespace()).toLowerCase());
                        }
                    }
                }
            } catch (Exception e) {
                EmiLog.error("EMI caught an exception while baking search for " + stack, e);
            }
        }
        for (Supplier<EmiAlias> supplier : EmiData.aliases) {
            EmiAlias alias = supplier.get();
            for (String key : alias.keys()) {
                if (!I18n.exists(key)) {
                    EmiReloadLog.warn("Untranslated alias " + key);
                }
                String text = I18n.get(key).toLowerCase();
                for (EmiIngredient ing : alias.stacks()) {
                    for (EmiStack stack : ing.getEmiStacks()) {
                        aliases.add(stack.copy().comparison(EmiPort.compareStrict()), text);
                    }
                }
            }
        }
        for (EmiAlias.Baked alias : EmiStackList.registryAliases) {
            for (Component text : alias.text()) {
                for (EmiIngredient ing : alias.stacks()) {
                    for (EmiStack stack : ing.getEmiStacks()) {
                        aliases.add(stack.copy().comparison(EmiPort.compareStrict()), text.getString().toLowerCase());
                    }
                }
            }
        }
        EmiConfig.appendItemModId = old;
        names.generate();
        tooltips.generate();
        mods.generate();
        aliases.generate();
        EmiSearch.names = names;
        EmiSearch.tooltips = tooltips;
        EmiSearch.mods = mods;
        EmiSearch.aliases = aliases;
        EmiSearch.bakedStacks = bakedStacks;
    }

    public static void update() {
        search(EmiScreenManager.search.getValue());
    }

    public static void search(String query) {
        try {
            var source = EmiScreenManager.getSearchSource();
            if (!searchBaked) {
                EmiSearch.stacks = source;
                return;
            }
            EmiSearch.CompiledQuery compiled = new EmiSearch.CompiledQuery(query);
            EmiSearch.compiledQuery = compiled;
            if (compiled.isEmpty()) {
                EmiSearch.stacks = source;
                return;
            }
            List<EmiIngredient> stacks = new ObjectArrayList<>(source.size());
            source.forEach(stack -> {
                List<EmiStack> ess = stack.getEmiStacks();
                if (ess.size() == 1) {
                    if (compiled.test(ess.getFirst())) {
                        stacks.add(stack);
                    }
                }
            });
            EmiSearch.stacks = stacks;
        } catch (Exception e) {
            EmiLog.error("Error when attempting to search:", e);
        }
    }

    public static void apply(SearchWorker worker, List<? extends EmiIngredient> stacks) {
        synchronized (EmiSearch.class) {
            if (worker == currentWorker) {
                EmiSearch.stacks = stacks;
                currentWorker = null;
                searchThread = null;
            }
        }
    }

    public static class CompiledQuery {
        public final Query fullQuery;

        public CompiledQuery(String query) {
            List<Query> full = Lists.newArrayList();
            List<Query> queries = Lists.newArrayList();
            Matcher matcher = TOKENS.matcher(query);
            while (matcher.find()) {
                String q = matcher.group();
                boolean negated = q.startsWith("-");
                if (negated) {
                    q = q.substring(1);
                }
                switch (q) {
                    case "" -> {
                        continue;
                    }
                    case "&" -> {
                        // Default behavior
                        continue;
                        // Default behavior
                    }
                    case "|" -> {
                        if (!queries.isEmpty()) {
                            full.add(new LogicalAndQuery(queries));
                            queries = Lists.newArrayList();
                        }
                        continue;
                    }
                }
                QueryType type = QueryType.fromString(q);
                Function<String, Query> constructor = type.queryConstructor;
                Function<String, Query> regexConstructor = type.regexQueryConstructor;
                if (type == QueryType.DEFAULT) {
                    List<Function<String, Query>> constructors = Lists.newArrayList();
                    List<Function<String, Query>> regexConstructors = Lists.newArrayList();
                    constructors.add(constructor);
                    regexConstructors.add(regexConstructor);

                    if (EmiConfig.searchTooltipByDefault) {
                        constructors.add(QueryType.TOOLTIP.queryConstructor);
                        regexConstructors.add(QueryType.TOOLTIP.regexQueryConstructor);
                    }
                    if (EmiConfig.searchModNameByDefault) {
                        constructors.add(QueryType.MOD.queryConstructor);
                        regexConstructors.add(QueryType.MOD.regexQueryConstructor);
                    }
                    if (EmiConfig.searchTagsByDefault) {
                        constructors.add(QueryType.TAG.queryConstructor);
                        regexConstructors.add(QueryType.TAG.regexQueryConstructor);
                    }
                    // TODO add config
                    constructors.add(AliasQuery::new);
                    if (constructors.size() > 1) {
                        constructor = name -> {
                            var alternatives = new ObjectArrayList<Query>(constructors.size());
                            for (var factory : constructors)
                                alternatives.add(factory.apply(name));
                            return new LogicalOrQuery(alternatives);
                        };
                        regexConstructor = name -> {
                            var alternatives = new ObjectArrayList<Query>(regexConstructors.size());
                            for (var factory : regexConstructors)
                                alternatives.add(factory.apply(name));
                            return new LogicalOrQuery(alternatives);
                        };
                    }
                }
                addQuery(q.substring(type.prefix.length()), negated, queries, constructor, regexConstructor);
            }
            if (!queries.isEmpty()) {
                full.add(new LogicalAndQuery(queries));
            }
            if (!full.isEmpty()) {
                fullQuery = new LogicalOrQuery(full);
            } else {
                fullQuery = null;
            }
        }

        public boolean isEmpty() {
            return fullQuery == null;
        }

        public boolean test(EmiStack stack) {
            if (fullQuery == null) {
                return true;
            } else if (EmiSearch.bakedStacks.contains(stack)) {
                return fullQuery.matches(stack);
            } else {
                return fullQuery.matchesUnbaked(stack);
            }
        }

        private static void addQuery(String s, boolean negated, List<Query> queries, Function<String, Query> normal,
                Function<String, Query> regex) {
            Query q;
            if (s.length() > 1 && s.startsWith("/") && s.endsWith("/")) {
                q = regex.apply(s.substring(1, s.length() - 1));
            } else if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
                q = normal.apply(s.substring(1, s.length() - 1));
            } else {
                q = normal.apply(s);
            }
            q.negated = negated;
            queries.add(q);
        }
    }

    private static class SearchWorker implements Runnable {
        private final String query;
        private final List<? extends EmiIngredient> source;

        public SearchWorker(String query, List<? extends EmiIngredient> source) {
            this.query = query;
            this.source = source;
        }

        @Override
        public void run() {
            try {
                CompiledQuery compiled = new CompiledQuery(query);
                compiledQuery = compiled;
                if (compiled.isEmpty()) {
                    apply(this, source);
                    return;
                }
                List<EmiIngredient> stacks = Lists.newArrayList();
                int processed = 0;
                for (EmiIngredient stack : source) {
                    if (processed++ >= 1024) {
                        processed = 0;
                        if (this != currentWorker) {
                            return;
                        }
                    }
                    List<EmiStack> ess = stack.getEmiStacks();
                    // TODO properly support ingredients?
                    if (ess.size() == 1) {
                        EmiStack es = ess.get(0);
                        if (compiled.test(es)) {
                            stacks.add(stack);
                        }
                    }
                }
                apply(this, List.copyOf(stacks));
            } catch (Exception e) {
                EmiLog.error("Error when attempting to search:", e);
            }
        }
    }
}
