package com.gyan.smartautosorter.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A named, colored sorting profile (PvP/Survival/Mining/Building or a
 * custom one). Holds the item -> preferred-slot rules and the manual
 * placement policy to use while this layout is active.
 */
public final class Layout {
    private final String id;
    private String name;
    private String colorHex;
    private ManualPlacementPolicy manualPlacementPolicy;
    /** itemId -> rule. LinkedHashMap keeps insertion/display order stable for the editor UI. */
    private final Map<String, ItemRule> rules = new LinkedHashMap<>();
    /** Category name -> ordered inventory slots assigned by the player. */
    private final Map<String, List<Integer>> categorySlots = new LinkedHashMap<>();
    private boolean builtIn;

    public Layout(String id, String name, String colorHex) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex;
        this.manualPlacementPolicy = ManualPlacementPolicy.PREFER_EVENTUALLY;
    }

    public static Layout newCustom(String name, String colorHex) {
        return new Layout(UUID.randomUUID().toString(), name, colorHex);
    }

    public Layout copy(String newName) {
        Layout copy = new Layout(UUID.randomUUID().toString(), newName, this.colorHex);
        copy.manualPlacementPolicy = this.manualPlacementPolicy;
        copy.rules.putAll(this.rules);
        this.categorySlots.forEach((category, slots) -> copy.categorySlots.put(category, new ArrayList<>(slots)));
        copy.builtIn = false;
        return copy;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String colorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public ManualPlacementPolicy manualPlacementPolicy() {
        return manualPlacementPolicy;
    }

    public void setManualPlacementPolicy(ManualPlacementPolicy policy) {
        this.manualPlacementPolicy = policy;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void markBuiltIn() {
        this.builtIn = true;
    }

    public void putRule(ItemRule rule) {
        rules.put(rule.itemId(), rule);
    }

    public void removeRule(String itemId) {
        rules.remove(itemId);
    }

    public ItemRule ruleFor(String itemId) {
        return rules.get(itemId);
    }

    public List<ItemRule> allRules() {
        return new ArrayList<>(rules.values());
    }

    public Map<String, List<Integer>> categorySlots() {
        return categorySlots;
    }

    public void assignCategorySlot(String category, int slot) {
        if (slot < 0 || slot >= 36) return;
        categorySlots.values().forEach(slots -> slots.remove(Integer.valueOf(slot)));
        List<Integer> slots = categorySlots.computeIfAbsent(category, key -> new ArrayList<>());
        if (!slots.contains(slot)) slots.add(slot);
    }

    public void unassignCategorySlot(int slot) {
        categorySlots.values().forEach(slots -> slots.remove(Integer.valueOf(slot)));
    }

    public static String categoryForItem(String itemId) {
        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        String name = id.substring(id.indexOf(':') + 1);
        if (name.contains("sword") || name.contains("bow") || name.contains("shield") || name.contains("trident") || name.contains("mace")) return "WEAPONS";
        if (name.contains("pickaxe") || name.contains("axe") || name.contains("shovel") || name.contains("hoe") || name.contains("shears") || name.contains("fishing_rod") || name.contains("flint_and_steel")) return "TOOLS";
        if (name.contains("bread") || name.contains("beef") || name.contains("porkchop") || name.contains("chicken") || name.contains("mutton") || name.contains("rabbit") || name.contains("apple") || name.contains("carrot") || name.contains("potato") || name.contains("stew") || name.contains("fish") || name.contains("melon_slice") || name.contains("cookie") || name.contains("berries") || name.contains("pie")) return "FOOD";
        if (name.contains("stone") || name.contains("dirt") || name.contains("planks") || name.contains("brick") || name.contains("glass") || name.contains("cobblestone") || name.contains("sand") || name.contains("log") || name.contains("wood") || name.contains("concrete") || name.contains("terracotta") || name.contains("wool") || name.contains("ore") || name.contains("deepslate") || name.contains("slab") || name.contains("stairs") || name.contains("fence") || name.contains("wall") || name.contains("leaves") || name.contains("block")) return "BLOCKS";
        return "OTHER";
    }
}
