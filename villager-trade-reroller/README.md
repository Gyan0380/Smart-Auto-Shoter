# Villager Trade Reroller

Paper plugin for Minecraft 26.x. Select an enchanted book in a GUI, target an **untraded** villager, and the plugin cycles its offers until that exact enchantment and level appears.

## Commands

- `/traderoll` or `/traderoll select` — target the villager you're looking at (within 6 blocks), then open the book selector.
- `/traderoll stop` — stop the active reroll task.

## Important

- This first version targets **librarian enchanted-book trades**.
- The villager must not have been traded with. The plugin refuses villagers with trading XP or used offers, because their profession/trades are locked by normal gameplay.
- The plugin changes an eligible villager to Librarian when `set-librarian-profession` is enabled.
- Rerolling changes the villager's offers repeatedly. Use it only on the villager you selected.
- A match is checked by enchantment key and exact level. The plugin stops as soon as it finds the selected book.
- This project compiles against the Paper 26.2 API line; test it on your exact 26.3 server before production use.

## Build

Run `gradle build` in this directory. The JAR is created in `build/libs/`.
