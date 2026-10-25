# Four-nozzle hopper resource pack

The pack adds four horizontal nozzle visuals to the vanilla hopper model. The plugin routes items across four linked horizontal faces; each face can have one directly adjacent linked container. Items are shared in round-robin order across available linked outputs, and stay in the upgraded hopper when outputs have no room.

Minecraft resource packs cannot distinguish plugin-upgraded hoppers from ordinary hoppers, so this model changes the appearance of every hopper while the pack is enabled. Install the pack on the server to show it to players:

1. Set `resource-pack` in `server.properties` to `https://raw.githubusercontent.com/Gyan0380/Smart-Auto-Shoter/feature/hopper-upgrader/hopper-upgrader/resource-pack/HopperFourNozzles.zip`.
2. Set `resource-pack-sha1` to `A27C57E722C39C7F4C508F915181F853DDC03D02`.
3. Restart the server. Players need to accept the resource pack.

This pack targets Minecraft Java 26.3.