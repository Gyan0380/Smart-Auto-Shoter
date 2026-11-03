# Villager Trade Reroller

Paper plugin for Minecraft 26.x. Select an untraded villager with any trading profession, choose one of its currently available trade results in the GUI, and confirm before the plugin rerolls that villager's offers until the selected result appears.

## Commands

- `/traderoll` or `/traderoll select` — target the villager you're looking at (within 6 blocks), then open its trade selector.
- `/traderoll stop` — stop your active reroll task.

## Features

- Supports the current offers of all trading professions, including Farmer, Fisherman, Shepherd, Fletcher, Librarian, Cartographer, Cleric, Armorer, Weaponsmith, Toolsmith, Butcher, and Leatherworker.
- Displays the selected villager's available trade results in an item-based GUI.
- Selecting a trade opens a separate confirmation screen. Rerolling starts only after pressing **CONFIRM**.
- Matches the selected result item, including item metadata (for example, the exact enchanted book).
- Keeps the villager's existing profession; it does not turn every villager into a Librarian.

## Important limits

- The villager must not have been traded with. The plugin refuses villagers with trading XP or used offers because Minecraft locks their profession/trades through normal gameplay.
- The selector shows the villager's **currently available offers**. A villager's later-level trade tiers are not listed until the villager naturally unlocks them.
- A search matches the selected result item, not the exact emerald/input cost or trade slot.
- Rerolling repeatedly changes the selected villager's offers. Use it only on the villager you intended to select.
- Test on your exact Paper 26.3 server before using it on a main world.

## Build

Run `gradle build` in this directory. The JAR is created in `build/libs/`.
