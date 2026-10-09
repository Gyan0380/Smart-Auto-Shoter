# Smart Auto Sorter — Fabric mod for Minecraft Java 26.2

## Status: source project, NOT compiled or tested. Read this before building.

### The exact blocker
This was written in a sandboxed environment with:
- **no internet access** (cannot reach Fabric's Maven, Mojang's version
  manifest, or Modrinth to download Loom, the Minecraft 26.2 jar,
  mappings, or Fabric API — every one of those is required just to run
  `./gradlew build`), and
- **no JDK compiler at all** (`javac`, `jshell`, and `jar` are all
  absent — only a bare `java` runtime, which can't even compile a
  single file).

So nothing here was compiled, nothing was run, no `.jar` was produced,
and no test actually executed — despite `src/test` containing real
JUnit tests written against the real sorting logic. I'm telling you this
plainly instead of claiming otherwise, per your own instructions in the
spec and because I'd rather you trust what I say was tested.

### What this means for you
Run these yourself, in order, in an environment with internet access
and a JDK 25 toolchain:

```bash
./gradlew test    # runs src/test — real, Minecraft-independent logic tests
./gradlew build   # produces build/libs/smart-auto-sorter-1.0.0.jar
```

Before the first build, open `gradle.properties` and double-check
`fabric_version` against <https://modrinth.com/mod/fabric-api/versions>
filtered to Minecraft 26.2 — I could not hit that site from here to pin
the exact build string, and Fabric API version strings change often.
`minecraft_version`, `loader_version`, and `loom_version` I verified via
web search against Fabric's own 26.2 announcement post, so those should
be accurate as of when this was written.

## What was actually tested (and how)
The sorting rules themselves (spec section 4, all 10 rules) live in
`core/SortEngine.java`, which deliberately has **zero Minecraft/Fabric
imports** — it operates on a small `InventoryView` interface and a
`ItemStackLite` value type instead of real `Inventory`/`ItemStack`.
That's not just a testing convenience; it's also why I can tell you with
some confidence that the *logic* is sound even though I couldn't compile
it: I traced every test in `SortEngineTest.java` and `ShareCodeCodecTest.java`
by hand against the code, line by line, since no compiler was available
to do it for me. That is a meaningfully weaker guarantee than "tests
passed" — treat it as "logic reviewed," not "verified," until you run
`./gradlew test` yourself.

Everything that *does* touch real Minecraft classes (`PlayerInventoryView`,
`MoveApplier`, the GUI screens, keybinds) was written against Mojang's
official class/method names as I know them (`Inventory`, `ItemStack`,
`AbstractContainerMenu#clicked`, `Screen`, `CycleButton`, etc. — these
are the same names Mojang mappings have used for years, and 26.1+ no
longer obfuscates at all). I could not check them against the actual
26.2 jar, so treat any compile error there as "the one risky layer,
exactly where I said it was risky" rather than a surprise.

## Architecture, briefly
- `core/` — the sorting rules engine. Pure Java, no Minecraft deps, unit tested.
- `config/` — JSON persistence (Gson) for layouts/settings, with backup +
  atomic-write + corrupt-file recovery (spec section 9).
- `share/` — the share-code codec: versioned binary payload + CRC32
  checksum, base64'd. Pure structured data — no eval, no deserialization
  of executable content, so there's nothing in a share code that could
  run code or touch the filesystem (spec section 7's safety requirement).
- `client/` — bridges `core/` to the real game: `PlayerInventoryView`
  reads the real inventory into a snapshot, `SortEngine` runs against
  that snapshot only, and `MoveApplier` replays the resulting moves
  against the *real* inventory using the same click-packet path vanilla
  uses when you drag an item (so the server validates every move
  normally, and NBT/enchantments on moved items survive).
- `gui/` — the menu, the layout editor (click an item, click a grid
  slot to assign it), and the import-share-code screen.

Detection of "an item moved" (pickup / chest transfer / crafting
result / server push — spec section 3's list) is done by polling the
player's 36 sortable slots once per client tick and diffing, rather than
mixin-ing into each individual vanilla code path. One mechanism,
one place to get right, no risk of two separate hooks double-triggering
a sort on the same change. A short cooldown after every sort pass
avoids spamming click packets.

## Honest limitations (spec asked for this explicitly)
- **Not compiled, not run, no `.jar` produced.** This is the big one; see above.
- `fabric_version` in `gradle.properties` needs to be confirmed against
  Modrinth before building.
- Armor and offhand slots are intentionally excluded from auto-sorting.
- The manual-placement detector (`ManualPlacementTracker`) is a
  tick-diff heuristic, not a guaranteed-correct signal — documented
  in-file. `PRESERVE_ALWAYS` is fully implemented; `PREFER_EVENTUALLY`
  and `IGNORE_MANUAL` currently behave the same as each other (both
  just let the item move home once its preferred slot frees up) — a
  real distinction between them (e.g. proactively reclaiming the slot
  sooner) was deliberately left out to avoid the engine ever evicting
  another item, which rule #4 forbids outright.
- Partial-stack merges are applied via repeated single-item right-clicks
  (mirrors how a player manually tops off a stack) rather than one
  hypothetical "move N" packet, which the vanilla click protocol doesn't
  expose. Slower than a full-stack move, but uses only vanilla-legal
  interactions.
- The layout editor's item picker only lists items already in the
  layout plus whatever you've clicked — there's no searchable
  creative-inventory-style item browser. A real implementation would
  reuse vanilla's `CreativeModeInventoryScreen` search widget; left out
  here to keep the editor's slot-assignment interaction (the part the
  spec actually tests against) the focus.
- GUI uses plain vanilla widgets (`Button`, `CycleButton`, `EditBox`),
  not a custom-textured panel — "polished" custom art assets aren't
  something I can produce or preview without image tooling.
- No multiplayer test server was available to verify "handle
  rejected/delayed server responses gracefully" beyond the try/catch
  per-move skip in `MoveApplier` — that's a safety net, not a verified
  reconciliation flow.
- 20+ saved custom layouts, rename/duplicate/delete, color tagging, and
  persistence-across-restart are all implemented in `SorterConfig`/`Layout`
  (JSON on disk survives restarts trivially); I did not exercise "20
  layouts" at runtime.

## Deliverables checklist (spec section 11)
1. Complete source project — yes, this folder.
2. A ZIP of it — yes, alongside this file.
3. A compiled `.jar` — **no**, see blocker above.
4. This README with install/test instructions — yes.
5. Feature list + limitations — the "Honest limitations" section above.
6. Build logs / test results — **none exist**, because nothing was run.

## Installing once you've built it
1. Install Fabric Loader 0.19.3+ for Minecraft 26.2 via the official
   Fabric installer.
2. Install the matching Fabric API build for 26.2 from Modrinth.
3. Drop `build/libs/smart-auto-sorter-1.0.0.jar` into your
   `.minecraft/mods` folder, alongside Fabric API.
4. Launch the Fabric 26.2 profile. `P` opens the menu, `O` toggles
   sorting (both rebindable in Controls).
