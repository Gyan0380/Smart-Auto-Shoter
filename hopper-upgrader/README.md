# Hopper Upgrader

Hopper Upgrader adds faster transfers and five extra storage slots to Paper hoppers. Each upgraded hopper can link its bonus storage to as many as three nearby containers.

## Getting started

1. Copy `HopperUpgrader-1.0.0.jar` into the server's `plugins` folder, then restart Paper.
2. Hold a copper hoe, crouch, and right-click a hopper. This upgrades the hopper and opens its menu. Repeat the same action to open the menu later.
3. The first row in the menu is the hopper's real five-slot inventory. Items placed there keep normal hopper behavior, so farms and other hopper chains continue to work. Shift-clicking from your inventory fills these slots first.
4. The second row is the five-slot bonus buffer. Use **Link an output**, then crouch-right-click a chest, barrel, furnace, or hopper directly beside the upgraded hopper. You can link one output on each of the four horizontal sides. The first linked output fills first. When it has no more room for an item, the plugin continues to the second, then third and fourth outputs. If every output is full, remaining items stay in the hopper. Bonus-buffer items follow the same order. Click an output in the menu to unlink it.
5. If no outputs are linked, bonus-buffer items go to a container below the hopper. Items in the normal hopper slots always follow standard Minecraft hopper routing.

By default, the plugin checks upgraded hoppers every two ticks and moves up to four items per check from a container above into the hopper's normal inventory. This supplements the usual hopper transfer. Furnace inputs go into the input slot and fuel goes into the fuel slot. Transfers raise Paper's inventory-move event so protection plugins can cancel them.

Use `/hopperupgrader` for the in-game instructions. `/hu` and `/hupgrader` are aliases. Upgrades, bonus items, and links are saved in `plugins/HopperUpgrader/hoppers.yml` and remain after a restart.

## Settings

Edit `plugins/HopperUpgrader/config.yml` and restart the server to change these values:

```yaml
transfer:
  interval-ticks: 2
  items-per-cycle: 4
links:
  max-per-hopper: 4
```

`interval-ticks` sets how often the plugin checks each upgraded hopper. `items-per-cycle` sets the transfer limit for each check. The plugin keeps the interval at one tick or more, the item limit between 1 and 64, and the number of linked destinations between 1 and 4.

## Build

The project targets Paper 26.3 and Java 25. With Gradle 9.7.0 installed, run:

```text
gradle --no-daemon -p hopper-upgrader clean build
```

The JAR is `hopper-upgrader/build/libs/HopperUpgrader-1.0.0.jar`. The GitHub Actions build also stores it as an artifact.

## Permission

`hopperupgrader.use` defaults to all players. The help command is available to everyone.
