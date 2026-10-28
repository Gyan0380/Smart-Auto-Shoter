# Minecart Connector

A small Paper plugin that lets players connect minecarts into linked trains.

## Features

- Connect minecarts by holding a chain and right-clicking two carts.
- Supports any cart that implements Paper/Bukkit's `Minecart` interface, including rideable, chest, hopper, furnace, and TNT minecarts where supported by the server version.
- Connect multiple carts into a line, up to the configured link limit per cart.
- Show a visible chain made from small chain item displays between connected carts.
- The chain visual hides when carts stretch far apart, while the logical link remains active.
- A moving furnace minecart can pass its direction and speed to linked carts while each follower is aligned to its current rail direction.
- Reverse travel follows the furnace cart's current movement direction; the plugin does not apply a global speed cap.
- A close-overlap guard stops a linked group if carts are dangerously close together, reducing pile-ups.
- Sneak and right-click a cart with a chain to remove its connections.
- Configure the maximum number of links per cart, chain visibility distance, and connector item.
- Admin command: `/minecartconnector reload`.

## Installation

1. Build the plugin JAR or download it from the project's Actions artifacts.
2. Put `MinecartConnector-1.0.0.jar` into the server's `plugins` directory.
3. Restart the Paper server.
4. Hold a chain, right-click the first cart, then right-click the second cart.
5. To disconnect a cart, sneak and right-click it while holding a chain.

## Build

The Gradle project is in `minecart-connector/`. It compiles against the published Paper 1.21.4 API using Java 21 to avoid relying on an unresolved `26.3.build.+` version selector.

## Notes

Connections are held in memory and are cleared when the plugin/server disables. The chain visuals are client-visible ItemDisplay entities and are removed when carts disconnect or the plugin disables. Paper does not expose a native physical minecart-coupling API, so train following is best-effort velocity synchronization along detected rail directions, not true vanilla cart coupling. The build, curves, reverse travel, blocked-cart handling, Bedrock/Geyser visuals, and performance still need in-game testing on the target Paper version.
