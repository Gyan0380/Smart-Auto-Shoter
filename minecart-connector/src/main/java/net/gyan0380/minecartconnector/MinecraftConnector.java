package net.gyan0380.minecartconnector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MinecraftConnector extends JavaPlugin implements Listener {
    private static final int CHAIN_SEGMENTS = 6;
    private final Map<UUID, UUID> selectedCart = new HashMap<>();
    private final Set<CartLink> links = new HashSet<>();
    private final Map<CartLink, List<ItemDisplay>> chainDisplays = new HashMap<>();
    private final Map<UUID, Vector> lastRailDirection = new HashMap<>();
    private BukkitTask physicsTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        physicsTask = Bukkit.getScheduler().runTaskTimer(this, this::updateLinkedCarts, 1L, 1L);
        getLogger().info("Minecart Connector enabled.");
    }

    @Override
    public void onDisable() {
        if (physicsTask != null) physicsTask.cancel();
        for (List<ItemDisplay> displays : chainDisplays.values()) {
            for (ItemDisplay display : displays) {
                if (display != null && display.isValid()) display.remove();
            }
        }
        chainDisplays.clear();
        selectedCart.clear();
        links.clear();
        lastRailDirection.clear();
    }

    @EventHandler
    public void onCartInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Minecart cart) || event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Material connector = getConnectorMaterial();
        if (item.getType() != connector) return;

        event.setCancelled(true);
        if (player.isSneaking()) {
            int removed = unlinkCart(cart.getUniqueId());
            selectedCart.remove(player.getUniqueId());
            player.sendMessage(removed > 0
                    ? ChatColor.GREEN + "Disconnected " + removed + " cart link(s)."
                    : ChatColor.YELLOW + "This cart is not connected.");
            return;
        }

        UUID playerId = player.getUniqueId();
        UUID firstId = selectedCart.get(playerId);
        if (firstId == null) {
            selectedCart.put(playerId, cart.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "First cart selected. Right-click another cart with a chain to connect them.");
            return;
        }
        if (firstId.equals(cart.getUniqueId())) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.GRAY + "Cart selection cancelled.");
            return;
        }

        Entity firstEntity = Bukkit.getEntity(firstId);
        if (!(firstEntity instanceof Minecart firstCart) || !firstCart.isValid()) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.RED + "The first cart is no longer available. Select it again.");
            return;
        }
        if (firstCart.getWorld() != cart.getWorld()) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.RED + "Both carts must be in the same world.");
            return;
        }

        CartLink link = new CartLink(firstId, cart.getUniqueId());
        if (links.contains(link)) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.YELLOW + "Those carts are already connected.");
            return;
        }

        links.add(link);
        createChainDisplays(link, firstCart.getLocation());
        selectedCart.remove(playerId);
        player.sendMessage(ChatColor.GREEN + "Minecarts connected with a visible chain! Sneak + right-click with a chain to disconnect.");
    }

    private void createChainDisplays(CartLink link, Location anchor) {
        List<ItemDisplay> displays = new ArrayList<>();
        for (int i = 0; i < CHAIN_SEGMENTS; i++) {
            ItemDisplay display = anchor.getWorld().spawn(anchor, ItemDisplay.class, entity -> {
                entity.setItemStack(new ItemStack(Material.CHAIN));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                entity.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0), new Quaternionf(),
                        new Vector3f(0.20f, 0.20f, 0.20f), new Quaternionf()));
                entity.setInterpolationDuration(0);
                entity.setTeleportDuration(0);
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setGravity(false);
            });
            displays.add(display);
        }
        chainDisplays.put(link, displays);
    }

    private void updateLinkedCarts() {
        Iterator<CartLink> iterator = links.iterator();
        while (iterator.hasNext()) {
            CartLink link = iterator.next();
            Entity entityA = Bukkit.getEntity(link.first());
            Entity entityB = Bukkit.getEntity(link.second());

            if (!(entityA instanceof Minecart a) || !(entityB instanceof Minecart b)
                    || !a.isValid() || !b.isValid() || a.getWorld() != b.getWorld()) {
                removeChainDisplays(link);
                iterator.remove();
                continue;
            }

            World world = a.getWorld();
            Location locationA = a.getLocation();
            Location locationB = b.getLocation();
            if (!world.isChunkLoaded(locationA.getBlockX() >> 4, locationA.getBlockZ() >> 4)
                    || !world.isChunkLoaded(locationB.getBlockX() >> 4, locationB.getBlockZ() >> 4)) continue;

            // Keep the visible connection while linked, even when carts temporarily spread apart.
            // This is visual/logical coupling only; no artificial speed cap or velocity override is applied.
            updateVisibleChain(link, locationA, locationB);
        }
        propagateFurnaceMotion();
    }

    private void propagateFurnaceMotion() {
        Set<UUID> visited = new HashSet<>();
        for (CartLink seed : links) {
            if (visited.contains(seed.first()) && visited.contains(seed.second())) continue;
            Set<UUID> component = new HashSet<>();
            List<UUID> queue = new ArrayList<>();
            queue.add(seed.first());
            queue.add(seed.second());
            for (int i = 0; i < queue.size(); i++) {
                UUID id = queue.get(i);
                if (!component.add(id)) continue;
                for (CartLink edge : links) {
                    if (edge.first().equals(id)) queue.add(edge.second());
                    else if (edge.second().equals(id)) queue.add(edge.first());
                }
            }
            visited.addAll(component);
            List<Minecart> carts = new ArrayList<>();
            for (UUID id : component) {
                Entity entity = Bukkit.getEntity(id);
                if (entity instanceof Minecart cart && cart.isValid()) {
                    carts.add(cart);
                    Vector velocity = cart.getVelocity().clone();
                    velocity.setY(0);
                    if (velocity.lengthSquared() > 0.0004 && isOnRail(cart)) {
                        lastRailDirection.put(id, velocity.normalize());
                    }
                }
            }
            Minecart engine = null;
            double engineSpeed = 0.0;
            for (Minecart cart : carts) {
                Vector velocity = cart.getVelocity().clone();
                velocity.setY(0);
                double speed = velocity.length();
                if (cart.getType() == org.bukkit.entity.EntityType.FURNACE_MINECART && speed > engineSpeed) {
                    engine = cart;
                    engineSpeed = speed;
                }
            }
            // Followers keep moving with the train even when carts get close.
            // There is deliberately no per-cart link-count limit or proximity brake here.
            if (engine == null || engineSpeed < 0.025) continue;
            Vector engineVelocity = engine.getVelocity().clone();
            engineVelocity.setY(0);
            for (Minecart cart : carts) {
                if (cart.getUniqueId().equals(engine.getUniqueId()) || !isOnRail(cart)) continue;
                if (cart.getLocation().distanceSquared(engine.getLocation()) < 0.01) continue;
                Vector current = cart.getVelocity().clone();
                current.setY(0);
                Vector preferred = engineVelocity.clone();
                double nearestNeighborDistance = Double.MAX_VALUE;
                for (CartLink edge : links) {
                    UUID neighborId = edge.first().equals(cart.getUniqueId()) ? edge.second()
                            : edge.second().equals(cart.getUniqueId()) ? edge.first() : null;
                    if (neighborId == null) continue;
                    Entity neighborEntity = Bukkit.getEntity(neighborId);
                    if (!(neighborEntity instanceof Minecart neighbor) || !neighbor.isValid()) continue;
                    double distanceSquared = cart.getLocation().distanceSquared(neighbor.getLocation());
                    if (distanceSquared < nearestNeighborDistance) {
                        nearestNeighborDistance = distanceSquared;
                        Vector neighborVelocity = neighbor.getVelocity().clone();
                        neighborVelocity.setY(0);
                        if (neighborVelocity.lengthSquared() > 0.0004) preferred = neighborVelocity;
                    }
                }
                // Let close followers continue forward; vanilla minecart collision/rail physics applies.
                double followSpeed = Math.min(engineSpeed, cart.getMaxSpeed());
                Vector nextVelocity;
                if (current.lengthSquared() > 0.0004) {
                    // Preserve the cart's own rail tangent so vanilla rail physics can carry it through curves.
                    // Only reverse it when it is clearly moving against the train's direction.
                    Vector direction = current.clone().normalize();
                    Vector guide = preferred.clone().setY(0);
                    if (guide.lengthSquared() > 0.0004 && direction.dot(guide.normalize()) < -0.2) direction.multiply(-1);
                    nextVelocity = direction.multiply(Math.min(followSpeed, Math.max(current.length(), followSpeed * 0.65)));
                } else {
                    // A stopped follower needs a direction hint; the rail helper chooses its local track axis.
                    Vector guide = preferred.clone().setY(0);
                    if (guide.lengthSquared() < 0.0004) guide = lastRailDirection.getOrDefault(cart.getUniqueId(), engineVelocity.clone()).clone();
                    Vector railDirection = getRailDirection(cart, guide);
                    if (railDirection == null) continue;
                    nextVelocity = railDirection.multiply(followSpeed * 0.65);
                }
                nextVelocity.setY(cart.getVelocity().getY());
                cart.setVelocity(nextVelocity);
                lastRailDirection.put(cart.getUniqueId(), nextVelocity.clone().setY(0).normalize());
            }
        }
    }

    private boolean isOnRail(Minecart cart) {
        Block block = cart.getLocation().getBlock();
        return block.getBlockData() instanceof Rail || block.getRelative(BlockFace.DOWN).getBlockData() instanceof Rail;
    }

    private Vector getRailDirection(Minecart cart, Vector preferred) {
        Block block = cart.getLocation().getBlock();
        if (!(block.getBlockData() instanceof Rail)) block = block.getRelative(BlockFace.DOWN);
        if (!(block.getBlockData() instanceof Rail rail)) return null;
        List<Vector> candidates = new ArrayList<>();
        switch (rail.getShape()) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> candidates.add(new Vector(0, 0, 1));
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> candidates.add(new Vector(1, 0, 0));
            case SOUTH_EAST -> { candidates.add(new Vector(0, 0, 1)); candidates.add(new Vector(1, 0, 0)); }
            case SOUTH_WEST -> { candidates.add(new Vector(0, 0, 1)); candidates.add(new Vector(1, 0, 0)); }
            case NORTH_WEST -> { candidates.add(new Vector(0, 0, 1)); candidates.add(new Vector(1, 0, 0)); }
            case NORTH_EAST -> { candidates.add(new Vector(0, 0, 1)); candidates.add(new Vector(1, 0, 0)); }
        }
        if (candidates.isEmpty()) return null;
        Vector guide = preferred.clone();
        guide.setY(0);
        if (guide.lengthSquared() < 0.0004) {
            guide = cart.getVelocity().clone();
            guide.setY(0);
        }
        if (guide.lengthSquared() < 0.0004) return candidates.get(0);
        guide.normalize();
        Vector best = candidates.get(0);
        double bestScore = -1.0;
        for (Vector candidate : candidates) {
            double score = Math.abs(candidate.dot(guide));
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        if (best.dot(guide) < 0) best.multiply(-1);
        return best;
    }

    private void updateVisibleChain(CartLink link, Location a, Location b) {
        List<ItemDisplay> displays = chainDisplays.get(link);
        if (displays == null || displays.size() != CHAIN_SEGMENTS) {
            createChainDisplays(link, a);
            displays = chainDisplays.get(link);
        }

        Vector start = a.toVector().add(new Vector(0, 0.35, 0));
        Vector end = b.toVector().add(new Vector(0, 0.35, 0));
        Vector delta = end.clone().subtract(start);
        double horizontalDistance = Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ());

        // Close carts get a hanging, loose chain; distant carts pull it nearly taut.
        double sag = Math.max(0.015, Math.min(0.16, 0.18 - horizontalDistance * 0.035));
        for (int i = 0; i < displays.size(); i++) {
            double t = (i + 0.5) / displays.size();
            Vector point = start.clone().add(delta.clone().multiply(t));
            point.setY(point.getY() - 4.0 * sag * t * (1.0 - t));

            double nextT = Math.min(1.0, t + 0.02);
            double prevT = Math.max(0.0, t - 0.02);
            Vector next = pointOnCurve(start, delta, sag, nextT);
            Vector prev = pointOnCurve(start, delta, sag, prevT);
            Vector tangent = next.subtract(prev);
            float yaw = (float) Math.toDegrees(Math.atan2(-tangent.getX(), tangent.getZ()));
            float pitch = (float) -Math.toDegrees(Math.atan2(tangent.getY(),
                    Math.sqrt(tangent.getX() * tangent.getX() + tangent.getZ() * tangent.getZ())));

            ItemDisplay display = displays.get(i);
            if (display == null || !display.isValid()) continue;
            Location target = point.toLocation(a.getWorld());
            target.setYaw(yaw);
            target.setPitch(pitch);
            display.teleport(target);
            display.setRotation(yaw, pitch);
        }
    }

    private Vector pointOnCurve(Vector start, Vector delta, double sag, double t) {
        Vector point = start.clone().add(delta.clone().multiply(t));
        point.setY(point.getY() - 4.0 * sag * t * (1.0 - t));
        return point;
    }


    private void hideChainDisplays(CartLink link) {
        List<ItemDisplay> displays = chainDisplays.get(link);
        if (displays == null) return;
        for (ItemDisplay display : displays) {
            if (display != null && display.isValid()) display.remove();
        }
        chainDisplays.remove(link);
    }

    private void removeChainDisplays(CartLink link) {
        List<ItemDisplay> displays = chainDisplays.remove(link);
        if (displays == null) return;
        for (ItemDisplay display : displays) {
            if (display != null && display.isValid()) display.remove();
        }
    }

    private int unlinkCart(UUID cartId) {
        int before = links.size();
        Set<UUID> affectedCarts = new HashSet<>();
        affectedCarts.add(cartId);
        Iterator<CartLink> iterator = links.iterator();
        while (iterator.hasNext()) {
            CartLink link = iterator.next();
            if (link.first().equals(cartId) || link.second().equals(cartId)) {
                affectedCarts.add(link.first());
                affectedCarts.add(link.second());
                removeChainDisplays(link);
                iterator.remove();
            }
        }
        return before - links.size();
    }

    private int linkCount(UUID cartId) {
        int count = 0;
        for (CartLink link : links) {
            if (link.first().equals(cartId) || link.second().equals(cartId)) count++;
        }
        return count;
    }

    private Material getConnectorMaterial() {
        String name = getConfig().getString("connector-item", "CHAIN");
        Material material = Material.matchMaterial(name == null ? "CHAIN" : name);
        return material == null || !material.isItem() ? Material.CHAIN : material;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.GOLD + "Minecart Connector");
            sender.sendMessage(ChatColor.YELLOW + "Hold a chain and right-click two minecarts to connect them.");
            sender.sendMessage(ChatColor.YELLOW + "The connection remains active if carts spread apart; the chain hides until they are close again.");
            sender.sendMessage(ChatColor.YELLOW + "Sneak + right-click a cart with a chain to disconnect it.");
            sender.sendMessage(ChatColor.YELLOW + "/minecartconnector reload - reload settings");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("minecartconnector.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                return true;
            }
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Minecart Connector settings reloaded.");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /minecartconnector help.");
        return true;
    }

    private record CartLink(UUID first, UUID second) {
        private CartLink {
            if (first.compareTo(second) > 0) {
                UUID swap = first;
                first = second;
                second = swap;
            }
        }
    }
}
