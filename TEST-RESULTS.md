# Smart Auto Sorter — Test Results

## What was run
The Minecraft-independent core sorting engine and share-code codec were compiled with `javac` from the available Java 21 runtime. A standalone test harness ran 11 assertions.

## Results
- PASS: moves item to empty preferred slot
- PASS: does not overwrite preferred slot item
- PASS: merges compatible stacks without deleting remainder
- PASS: leaves item in place when inventory is full
- PASS: respects locked slots
- PASS: preserves manually placed item
- PASS: second sort pass is a no-op
- PASS: share code round-trip
- PASS: rejects invalid share code
- PASS: rejects tampered share code
- PASS: rejects oversized share code

**Result: 11/11 core logic checks passed.**

## Not verified
The full Fabric mod was NOT built or launched. The environment has Java 21, but the project targets Java 25; Gradle and a Gradle wrapper are absent, and the Minecraft/Fabric dependencies are not cached locally. Therefore there is no verified `.jar` yet, and the Minecraft GUI, inventory click integration, chest/crafting flows, and multiplayer behavior remain untested in-game.

## Next step to produce the JAR
Use a machine with JDK 25 and internet access, install/use a compatible Gradle version, verify the Minecraft 26.2 Fabric API coordinate, then run `gradle test build`. Fix any version-specific API compile errors and test the resulting JAR in a separate Minecraft instance before using it in a main world.
