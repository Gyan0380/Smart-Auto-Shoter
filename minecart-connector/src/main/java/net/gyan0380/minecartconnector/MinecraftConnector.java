package net.gyan0380.minecartconnector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
        double maxLinkDistance = Math.max(1.5, getConfig().getDouble("max-link-distance", 3.5));
        if (firstCart.getLocation().distanceSquared(cart.getLocation()) > maxLinkDistance * maxLinkDistance) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.RED + "Carts must be within " + maxLinkDistance + " blocks of each other.");
            return;
        }

        CartLink link = new CartLink(firstId, cart.getUniqueId());
        if (links.contains(link)) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.YELLOW + "Those carts are already connected.");
            return;
        }

        int maxLinks = Math.max(1, getConfig().getInt("max-links-per-cart", 2));
        if (linkCount(firstId) >= maxLinks || linkCount(cart.getUniqueId()) >= maxLinks) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.RED + "One of these carts has reached the link limit (" + maxLinks + ").");
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

            // This plugin is a visual connector only. Do not apply spring forces or set
            // minecart velocity here: doing so fights vanilla rail physics and can reverse
            // furnace carts or push carts off consecutive turns.
            double maxLinkDistance = Math.max(1.5, getConfig().getDouble("max-link-distance", 3.5));
            if (locationA.distanceSquared(locationB) > maxLinkDistance * maxLinkDistance) {
                removeChainDisplays(link);
                iterator.remove();
                continue;
            }

            updateVisibleChain(link, locationA, locationB);
        }
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

    private void removeChainDisplays(CartLink link) {
        List<ItemDisplay> displays = chainDisplays.remove(link);
        if (displays == null) return;
        for (ItemDisplay display : displays) {
            if (display != null && display.isValid()) display.remove();
        }
    }

    private int unlinkCart(UUID cartId) {
        int before = links.size();
        Iterator<CartLink> iterator = links.iterator();
        while (iterator.hasNext()) {
            CartLink link = iterator.next();
            if (link.first().equals(cartId) || link.second().equals(cartId)) {
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
            sender.sendMessage(ChatColor.YELLOW + "A visible chain hangs when carts are close and tightens as they separate.");
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
