# HopperUpgrader 1.0.0

A standalone Paper plugin that gives hoppers five bonus storage slots, faster item movement, and up to three linked output containers.

## Use it

1. Install `HopperUpgrader-1.0.0.jar` in the Paper server's `plugins` folder and restart.
2. Hold a **copper hoe**, sneak, and right-click a hopper. This upgrades it and opens its menu. Upgrades and stored items are saved across restarts.
3. Shift-click items from your inventory into the five bonus slots, or pick up an item and click a bonus slot. The upgraded hopper pulls from a container directly above it and stores overflow here.
4. Click **Link an output**, then sneak-right-click a chest, barrel, furnace, or hopper. Repeat for up to three outputs. Stored items are sent round-robin to the linked containers. Click a listed output in the menu to remove that link.
5. Run `/hopperupgrader` in chat for a short reminder. `/hu` and `/hupgrader` are aliases.

The physical hopper inventory and ordinary Minecraft hopper behavior remain intact. The bonus slots are separate from the vanilla five slots. The plugin's faster transfer loop moves up to four items every two ticks by default (configurable). Vanilla hopper automation remains available alongside it.

## Configuration

`plugins/HopperUpgrader/config.yml`:

```yaml
transfer:
  interval-ticks: 2
  items-per-cycle: 4
links:
  max-per-hopper: 3
```

`interval-ticks` is clamped to at least one tick, `items-per-cycle` to 1–64, and outputs to 1–3 per hopper. Existing hopper upgrade data is stored in `plugins/HopperUpgrader/hoppers.yml`.

## Build

Requires Java 25 and Gradle 9.7.0:

```text
gradle --no-daemon -p hopper-upgrader clean build
```

The plugin JAR is `hopper-upgrader/build/libs/HopperUpgrader-1.0.0.jar`. Paper 26.3 is the target. Test on a staging server before installing on a live world.

## Permission

`hopperupgrader.use` defaults to all players. The help command is available to all players.

