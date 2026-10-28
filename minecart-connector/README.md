# Minecart Connector

A small Paper plugin that lets players connect minecarts into linked trains.

## Features

- Connect minecarts by holding a chain and right-clicking two carts.
- Supports any cart that implements Paper/Bukkit's `Minecart` interface, including rideable, chest, hopper, furnace, and TNT minecarts where supported by the server version.
- Connect multiple carts into a line, up to the configured link limit per cart.
- Show a visible chain made from small chain item displays between connected carts.
- The chain dynamically hangs lower when carts are close and becomes straighter as they pull apart.
- Sneak and right-click a cart with a chain to remove its connections.
- Configure link distance, connection strength, velocity correction, and connector item.
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

Connections are held in memory and are cleared when the plugin/server disables. The chain visuals are client-visible ItemDisplay entities and are removed when carts disconnect or the plugin disables. This is a visual approximation—not a physically simulated chain—and the visuals, physics, performance, rail layouts, and minecart types still need in-game testing on the target Paper version. Compatibility with newer Paper server versions has not yet been verified in-game.
