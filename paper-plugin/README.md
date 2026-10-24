# CustomEnchants 1.1.0

A Paper plugin with six custom enchant books, an XP shop, an expanded admin menu, and optional TPA commands.

| Book | Tool | Effect | Default price |
|---|---|---|---:|
| Lumberjack | Axe or pickaxe | Breaks connected logs of the same type and nearby leaves; leaf drops go into inventory when space is available, with overflow dropped at the block | 10 levels |
| Vein Miner | Pickaxe | Breaks connected blocks of the same ore type | 20 levels |
| 3x3 Miner | Pickaxe | Breaks a 3×3 plane perpendicular to the direction you're aiming; looking up or down clears a horizontal plane | 30 levels |
| Long Arm | Mining tools | Adds configurable block interaction reach while held in the main hand; it does not change entity or combat reach | 15 levels |
| Less Damage | Damageable tools and armor | Chance to negate durability damage, otherwise reduce it; small chance to restore 1 durability | 40 levels |
| Healer | Damageable tools and armor | Sneak + right-click while holding the enchanted item to repair it using XP points | 25 levels |

Tools display a glint and lore for each custom enchant. Different custom enchants can share a tool up to the configurable limit (default 6); duplicate enchants are blocked. Sneak while mining to temporarily disable all effects. Extra blocks each consume one durability point, with Unbreaking applied; Lumberjack leaves and the snow layers above them don't consume durability. Area mining skips liquids, bedrock, and portal blocks. If it breaks a side block of an active Nether portal frame, that portal is cleared; optional corner blocks do not deactivate it. Breaking extra blocks applies normal physics, so sand and gravel fall when unsupported and nearby fluids update.

Application XP costs rise per existing custom enchant: defaults are 2 levels for the first, 4 for the second, and 6 for the third. Apply by placing the book on the cursor and clicking a compatible tool in inventory, with an anvil, or using `/enchantshop apply <enchant>`.

## Commands

- `/enchantshop` or `/eshop` opens the shop.
- `/nightvision on` enables night vision for the player; `/nightvision off` removes it. Both options are available to all players.
- `/enchantshop give <player> <lumberjack|veinminer|3x3miner|longarm|lessdamage|healer>` gives a book (admin).
- `/enchantadmin` opens the settings GUI (admin). Click books to show/hide them, use +/- to change prices, and adjust application XP costs and maximum enchants per tool. Shift-click changes values in larger steps. Toggle the shop, this plugin's TPA commands, and player night vision from the same menu. Disabling night vision removes effects granted by this plugin from online players and blocks the command.
- `/tpa <player>` requests to teleport to a player.
- `/tpahere <player>` requests a player to teleport to you. `/tpahear` is also accepted.
- `/tpaccept [player]`, `/tpadeny [player]`, `/tpacancel`; `/tpacancle` is an accepted spelling alias.

TPA acceptance starts a warmup (default 5 seconds), and successful teleport starts a cooldown (default 5 seconds). Movement cancels the warmup by default. The destination player is notified and both players hear a configurable teleport sound. If another plugin already owns a TPA alias, Paper leaves that alias with the other plugin. The admin toggle disables this plugin's TPA feature; it does not toggle or change other plugins.

## Configuration

The plugin creates and updates its own `plugins/CustomEnchants/config.yml`. Settings include `prices.*`, `application.base-cost`, `application.additional-cost`, `application.max-per-tool`, `long-arm.block-range-bonus`, `less-damage.*`, `healer.xp-per-durability`, `limits.lumberjack`, `limits.veinminer`, `lumberjack.leaf-radius`, `shop.enabled`, `shop.items.*.enabled`, `nightvision.enabled`, and `tpa.*`. TPA settings include request timeout, warmup, cooldown, cancel-on-move, and sound. The admin GUI saves changes immediately. It does not edit other plugins' configuration files.

## Build and install

Requires JDK 25 and Gradle 9.7.0 (the version used by the included build workflow). From the repository root, run `gradle --no-daemon -p paper-plugin clean build`; the JAR is `paper-plugin/build/libs/CustomEnchants-1.1.0.jar`. Install it in the server's `plugins` directory and restart Paper 26.3 on Java 25.

## Permissions and compatibility

- `customenchants.shop` and `customenchants.use` default to everyone.
- `customenchants.admin` defaults to operators.
- `customenchants.tpa` defaults to everyone.

Geyser/Floodgate are optional; enchant behavior is server-side. Paper 26.3 is the target, not Spigot. Extra blocks fire `BlockBreakEvent` for protection plugins, but do not individually award XP or run every vanilla block-break effect. Test on a staging server before production use; live server behavior has not been independently tested.

