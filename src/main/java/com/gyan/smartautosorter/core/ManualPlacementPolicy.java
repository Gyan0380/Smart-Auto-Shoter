package com.gyan.smartautosorter.core;

/** Governs rule #8/#9: what happens when a player manually moves an item away from its preferred slot. */
public enum ManualPlacementPolicy {
    /** Once placed by the player, the item is locked in place until explicitly unlocked. Never auto-moved again. */
    PRESERVE_ALWAYS,
    /** The item stays put for now, but moves back to its preferred slot next time that slot frees up. */
    PREFER_EVENTUALLY,
    /** Manual placement is ignored; the sorter will move it back on the very next sort pass. */
    IGNORE_MANUAL
}
