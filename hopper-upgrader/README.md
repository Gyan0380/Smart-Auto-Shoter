# Hopper Upgrader

Hopper Upgrader adds a five-slot buffer and faster item routing to regular Paper hoppers. You can link one upgraded hopper to as many as three containers.

## Getting started

1. Copy `HopperUpgrader-1.0.0.jar` into the server's `plugins` folder, then restart Paper.
2. Hold a copper hoe, crouch, and right-click a hopper. The first use upgrades it and opens its menu; repeat the same action to open the menu later.
3. Shift-click items from your inventory into the five bonus slots. You can also pick up a stack and place it in a bonus slot. An upgraded hopper pulls items from a container above it into this buffer.
4. To add a destination, click **Link an output**, then crouch and right-click a chest, barrel, furnace, or another hopper. Repeat for up to three destinations. The hopper shares its buffered items among the linked containers. Click a listed destination in the menu to unlink it.
5. With no linked destinations, buffered items go to a container below the hopper. The hopper's regular five slots and normal Minecraft transfer behavior continue to work.

By default, the plugin checks upgraded hoppers every two ticks and moves up to four items per check. Furnace inputs go into the input slot, and fuel goes into the fuel slot. Transfers raise Paper's inventory-move event so protection plugins can cancel them.

Use `/hopperupgrader` for the in-game instructions. `/hu` and `/hupgrader` are aliases. Upgrades, bonus items, and links are saved in `plugins/HopperUpgrader/hoppers.yml` and remain after a restart.

## Settings

Edit `plugins/HopperUpgrader/config.yml` and restart the server to change these values:

```yaml
transfer:
  interval-ticks: 2
  items-per-cycle: 4
links:
  max-per-hopper: 3
```

`interval-ticks` sets how often the plugin checks each upgraded hopper. `items-per-cycle` sets the transfer limit for each check. The plugin keeps the interval at one tick or more, the item limit between 1 and 64, and the number of linked destinations between 1 and 3.

## Build

The project targets Paper 26.3 and Java 25. With Gradle 9.7.0 installed, run:

```text
gradle --no-daemon -p hopper-upgrader clean build
```

The JAR is `hopper-upgrader/build/libs/HopperUpgrader-1.0.0.jar`. The GitHub Actions build also stores it as an artifact.

## Permission

`hopperupgrader.use` defaults to all players. The help command is available to everyone.
