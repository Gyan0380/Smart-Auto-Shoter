# CustomEnchants 1.1.0

A Paper plugin with six custom enchant books, an XP shop, an expanded admin menu, and optional TPA commands.

| Book | Tool | Effect | Default price |
|---|---|---|---:|
| Lumberjack | Axe or pickaxe | Breaks connected logs; nearby leaves break and their drops go directly into inventory | 10 levels |
| Vein Miner | Pickaxe | Breaks connected blocks of the same ore type | 20 levels |
| 3x3 Miner | Pickaxe | Breaks a 3×3 plane perpendicular to the direction you're aiming; looking up or down clears a horizontal plane | 30 levels |
| Long Arm | Tools and weapons | Adds configurable block/entity interaction range while held in the main hand | 15 levels |
| Less Damage | Damageable tools and armor | Chance to negate durability damage, otherwise reduce it; small chance to restore 1 durability | 40 levels |
| Healer | Damageable tools and armor | Sneak + right-click while holding the enchanted item to repair it using XP points | 25 levels |

Tools display a glint and lore for each custom enchant. Different custom enchants can share a tool up to the configurable limit (default 6); duplicate enchants are blocked. Sneak while mining to temporarily disable all effects. Extra blocks each consume one durability point, with Unbreaking applied; Lumberjack leaves don't consume durability.

Application XP costs rise per existing custom enchant: defaults are 2 levels for the first, 4 for the second, and 6 for the third. Apply by placing the book on the cursor and clicking a compatible tool in inventory, with an anvil, or using `/enchantshop apply <enchant>`.

## Commands

- `/enchantshop` or `/eshop` opens the shop.
- `/enchantshop give <player> <lumberjack|veinminer|3x3miner|longarm|lessdamage|healer>` gives a book (admin).
- `/enchantadmin` opens the settings GUI (admin). Click books to show/hide them, use +/- to change prices, and adjust application XP costs and maximum enchants per tool. Shift-click changes values in larger steps. Toggle the shop and this plugin's TPA commands from the same menu.
- `/tpa <player>` requests to teleport to a player.
- `/tpahere <player>` requests a player to teleport to you. `/tpahear` is also accepted.
- `/tpaccept [player]`, `/tpadeny [player]`, `/tpacancel`; `/tpacancle` is an accepted spelling alias.

TPA acceptance starts a warmup (default 5 seconds), and successful teleport starts a cooldown (default 5 seconds). Movement cancels the warmup by default. The destination player is notified and both players hear a configurable teleport sound. If another plugin already owns a TPA alias, Paper leaves that alias with the other plugin. The admin toggle disables this plugin's TPA feature; it does not toggle or change other plugins.

## Configuration

The plugin creates `plugins/CustomEnchants/config.yml`. Settings include `prices.*`, `application.base-cost`, `application.additional-cost`, `application.max-per-tool`, `long-arm.*`, `less-damage.*`, `healer.xp-per-durability`, `shop.enabled`, `shop.items.*.enabled`, and `tpa.*`. TPA settings include request timeout, warmup, cooldown, cancel-on-move, and sound. The admin GUI saves changes immediately. Existing server configuration is not needed or modified by this plugin.

## Build and install

Requires JDK 25 and Gradle 8.14 or later. Run `gradle build`; the JAR is `build/libs/CustomEnchants-1.1.0.jar`. Install the JAR in the server's `plugins` directory and restart Paper 26.3 on Java 25.

## Permissions and compatibility

- `customenchants.shop` and `customenchants.use` default to everyone.
- `customenchants.admin` defaults to operators.
- `customenchants.tpa` defaults to everyone.

Geyser/Floodgate are optional; enchant behavior is server-side. Paper 26.3 is the target, not Spigot. Extra blocks fire `BlockBreakEvent` for protection plugins, but do not individually award XP or run every vanilla block-break effect. Test on a staging server before production use; live server behavior has not been independently tested.

