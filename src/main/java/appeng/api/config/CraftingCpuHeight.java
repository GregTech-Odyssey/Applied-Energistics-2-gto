package appeng.api.config;

/**
 * How tall the crafting CPU and crafting status screens are drawn. The rows shown are relative to the 6 rows the
 * screens were originally designed for: {@code SHORT} keeps those, {@code FULL} uses every row that fits the window,
 * and {@code TALL} sits in between.
 */
public enum CraftingCpuHeight {

    SHORT,
    TALL,
    FULL
}
