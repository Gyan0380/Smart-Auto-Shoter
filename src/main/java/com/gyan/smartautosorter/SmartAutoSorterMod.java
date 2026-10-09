package com.gyan.smartautosorter;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. This mod is client-only (see fabric.mod.json
 * "environment": "client") because it only ever sorts the local
 * player's own inventory via normal client->server slot-click packets —
 * it never touches server-side inventory state directly (spec section 8:
 * "Respect server-authoritative inventory state").
 */
public final class SmartAutoSorterMod implements ModInitializer {

    public static final String MOD_ID = "smartautosorter";
    public static final Logger LOGGER = LoggerFactory.getLogger("Smart Auto Sorter");

    @Override
    public void onInitialize() {
        LOGGER.info("Smart Auto Sorter initialized");
    }
}
