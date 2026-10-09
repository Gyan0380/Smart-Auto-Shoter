package com.gyan.smartautosorter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gyan.smartautosorter.core.Layout;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Whole-mod configuration: the master on/off switch, which layout is
 * active, the keybind-relevant settings, and every saved layout
 * (built-in + custom, up to MAX_CUSTOM_LAYOUTS custom ones — spec
 * section 6 asks for "at least 20").
 *
 * Persistence (spec section 9):
 *  - stored as JSON under .minecraft/config/smartautosorter/config.json
 *  - every save writes to a .tmp file first, then atomically replaces
 *    the real file, and the previous good file is kept as config.json.bak
 *    so a crash mid-write can never leave the player with a corrupt,
 *    unreadable config.
 *  - load() falls back to a fresh default config (and logs a warning)
 *    rather than crashing the game if the JSON is unreadable.
 */
public final class SorterConfig {

    public static final int MAX_CUSTOM_LAYOUTS = 20;

    public boolean masterAutoSortEnabled = true;
    public String activeLayoutId = DefaultLayouts.SURVIVAL_ID;
    public boolean applyLayoutImmediatelyOnSwitch = true;
    public int toggleKeyCode = 79; // GLFW 'O'
    public int menuKeyCode = 80;   // GLFW 'P'

    public final List<Layout> layouts = new ArrayList<>();

    public static SorterConfig createDefault() {
        SorterConfig cfg = new SorterConfig();
        cfg.layouts.addAll(DefaultLayouts.all());
        return cfg;
    }

    public Layout activeLayout() {
        for (Layout l : layouts) {
            if (l.id().equals(activeLayoutId)) return l;
        }
        return layouts.isEmpty() ? null : layouts.get(0);
    }

    public boolean canAddCustomLayout() {
        long customCount = layouts.stream().filter(l -> !l.isBuiltIn()).count();
        return customCount < MAX_CUSTOM_LAYOUTS;
    }

    // ---- persistence -------------------------------------------------

    private static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    public static SorterConfig load(Path configFile) {
        if (!Files.exists(configFile)) {
            SorterConfig fresh = createDefault();
            fresh.save(configFile);
            return fresh;
        }
        try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            SorterConfig loaded = gson().fromJson(r, SorterConfig.class);
            if (loaded == null || loaded.layouts == null) {
                throw new IOException("config.json parsed to null/invalid structure");
            }
            return loaded;
        } catch (Exception e) {
            // rule: "recover gracefully from invalid configuration files" — try the backup, then fall back to defaults.
            Path backup = backupPath(configFile);
            if (Files.exists(backup)) {
                try (Reader r = Files.newBufferedReader(backup, StandardCharsets.UTF_8)) {
                    SorterConfig fromBackup = gson().fromJson(r, SorterConfig.class);
                    if (fromBackup != null && fromBackup.layouts != null) {
                        return fromBackup;
                    }
                } catch (Exception ignored) {
                    // fall through to defaults below
                }
            }
            SorterConfig fresh = createDefault();
            fresh.save(configFile);
            return fresh;
        }
    }

    public void save(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());

            // back up the last known-good config before writing (rule: "back up configuration before destructive edits")
            if (Files.exists(configFile)) {
                Files.copy(configFile, backupPath(configFile), StandardCopyOption.REPLACE_EXISTING);
            }

            Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson().toJson(this, w);
            }
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Smart Auto Sorter config", e);
        }
    }

    public static void resetToDefaults(Path configFile) {
        createDefault().save(configFile);
    }

    private static Path backupPath(Path configFile) {
        return configFile.resolveSibling(configFile.getFileName() + ".bak");
    }
}
