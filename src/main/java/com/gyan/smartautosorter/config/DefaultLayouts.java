package com.gyan.smartautosorter.config;

import com.gyan.smartautosorter.core.ItemRule;
import com.gyan.smartautosorter.core.Layout;

import java.util.List;

/**
 * The four built-in colored profiles from spec section 2/5. Slot numbers
 * use vanilla player-inventory indexing: 0-8 = hotbar, 9-35 = main
 * inventory, 36-39 = armor, 40 = offhand (standard Fabric/Minecraft
 * PlayerInventory layout — double check this hasn't changed in 26.2
 * before relying on it, see README).
 *
 * These are starting points, not permanent hardcoding — rule #: "Do not
 * hardcode every item's destination permanently. Allow the player to
 * customize item-to-slot assignments." Every slot here is editable
 * through the Layout Editor screen; this class only seeds sensible
 * defaults the first time the mod runs.
 */
public final class DefaultLayouts {

    public static final String PVP_ID = "builtin_pvp";
    public static final String SURVIVAL_ID = "builtin_survival";
    public static final String MINING_ID = "builtin_mining";
    public static final String BUILDING_ID = "builtin_building";

    private DefaultLayouts() {}

    public static List<Layout> all() {
        return List.of(pvp(), survival(), mining(), building());
    }

    public static Layout pvp() {
        Layout l = new Layout(PVP_ID, "PvP", "#E53935"); // red
        l.markBuiltIn();
        put(l, "minecraft:netherite_sword", 0);
        put(l, "minecraft:shield", 1);
        put(l, "minecraft:bow", 2);
        put(l, "minecraft:crossbow", 3);
        put(l, "minecraft:ender_pearl", 4);
        put(l, "minecraft:golden_apple", 5);
        put(l, "minecraft:enchanted_golden_apple", 6);
        put(l, "minecraft:splash_potion", 7);
        put(l, "minecraft:cooked_beef", 8);
        return l;
    }

    public static Layout survival() {
        Layout l = new Layout(SURVIVAL_ID, "Survival", "#43A047"); // green
        l.markBuiltIn();
        put(l, "minecraft:wooden_sword", 0);
        put(l, "minecraft:stone_pickaxe", 1);
        put(l, "minecraft:stone_axe", 2);
        put(l, "minecraft:stone_shovel", 3);
        put(l, "minecraft:bread", 4);
        put(l, "minecraft:torch", 8);
        put(l, "minecraft:crafting_table", 7);
        return l;
    }

    public static Layout mining() {
        Layout l = new Layout(MINING_ID, "Mining", "#1E88E5"); // blue
        l.markBuiltIn();
        put(l, "minecraft:diamond_pickaxe", 0);
        put(l, "minecraft:iron_pickaxe", 1);
        put(l, "minecraft:torch", 8);
        put(l, "minecraft:cobblestone", 7);
        put(l, "minecraft:cooked_porkchop", 4);
        return l;
    }

    public static Layout building() {
        Layout l = new Layout(BUILDING_ID, "Building", "#FDD835"); // gold
        l.markBuiltIn();
        put(l, "minecraft:stone_bricks", 0);
        put(l, "minecraft:scaffolding", 1);
        put(l, "minecraft:oak_planks", 2);
        put(l, "minecraft:glass", 3);
        put(l, "minecraft:ladder", 4);
        return l;
    }

    private static void put(Layout l, String itemId, int slot) {
        l.putRule(new ItemRule(itemId, slot, 0, false));
    }
}
