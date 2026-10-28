# Minecart Connector — In-game test checklist

Use this checklist after the GitHub Actions build succeeds and you have downloaded the JAR.

## Setup
- Use a test Paper server first, not your main world.
- Keep a backup of the world.
- Confirm the server version is supported by the plugin build (currently compiled against Paper API 1.21.4, Java 21).

## Basic connection
1. Place two rideable minecarts on a straight, level rail.
2. Hold a chain and right-click the first minecart, then the second.
3. Confirm the chat says the carts are connected.
4. Watch whether a visible chain appears between them.
5. Move the carts slowly and check that the chain follows their positions.

## Slack / tension appearance
- Put the carts close together and check whether the chain visibly sags.
- Let the carts separate and check whether the chain looks straighter.
- Check that the chain doesn't flicker, rotate oddly, or leave floating entities behind.

## Disconnect and cleanup
- Hold a chain, sneak, and right-click either linked cart.
- Confirm the chain visuals disappear.
- Stop/restart the server and confirm no old chain visuals remain.

## Rail and compatibility checks
- Test straight rails, curves, slopes, powered rails, and different cart types.
- If the server uses Geyser/Floodgate, check the visuals from both Java and Bedrock clients. Display-entity rendering can differ between clients.
- Watch server logs for errors and check that many connected carts do not cause lag.

## Important limitations
- This is a visual approximation made from multiple chain item displays; it is not Minecraft's native chain physics.
- The current chain links and connection state are in-memory and are not saved across a restart.
- Do not use on a public server until these tests pass on your exact Paper/Geyser versions.
