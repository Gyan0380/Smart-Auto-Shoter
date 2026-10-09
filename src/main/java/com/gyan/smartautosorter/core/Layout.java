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
}
