package net.gyan0380.minecartconnector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MinecraftConnector extends JavaPlugin implements Listener {
    private final Map<UUID, UUID> selectedCart = new HashMap<>();
    private final Set<CartLink> links = new HashSet<>();
    private BukkitTask physicsTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        physicsTask = Bukkit.getScheduler().runTaskTimer(this, this::updateLinkedCarts, 1L, 1L);
        getLogger().info("Minecraft Connector enabled.");
    }

    @Override
    public void onDisable() {
        if (physicsTask != null) {
            physicsTask.cancel();
        }
        selectedCart.clear();
        links.clear();
    }

    @EventHandler
    public void onCartInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Minecart cart)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Material connector = getConnectorMaterial();
        if (item.getType() != connector) {
            return;
        }

        event.setCancelled(true);

        if (player.isSneaking()) {
            int removed = unlinkCart(cart.getUniqueId());
            selectedCart.remove(player.getUniqueId());
            if (removed > 0) {
                player.sendMessage(ChatColor.GREEN + "Disconnected " + removed + " cart link(s).");
            } else {
                player.sendMessage(ChatColor.YELLOW + "This cart is not connected.");
            }
            return;
        }

        UUID playerId = player.getUniqueId();
        UUID firstId = selectedCart.get(playerId);
        if (firstId == null) {
            selectedCart.put(playerId, cart.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "First cart selected. Right-click another cart with a "
                    + connector.name().toLowerCase().replace('_', ' ') + " to connect them.");
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

        if (firstCart.getLocation().distanceSquared(cart.getLocation()) > 100) {
            selectedCart.remove(playerId);
            player.sendMessage(ChatColor.RED + "Carts must be within 10 blocks of each other.");
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
        selectedCart.remove(playerId);
        player.sendMessage(ChatColor.GREEN + "Minecarts connected! Use a " + connector.name().toLowerCase().replace('_', ' ')
                + " while sneaking and right-click a cart to disconnect its links.");
    }

    private void updateLinkedCarts() {
        Iterator<CartLink> iterator = links.iterator();
        while (iterator.hasNext()) {
            CartLink link = iterator.next();
            Entity entityA = Bukkit.getEntity(link.first());
            Entity entityB = Bukkit.getEntity(link.second());

            if (!(entityA instanceof Minecart a) || !(entityB instanceof Minecart b)
                    || !a.isValid() || !b.isValid() || a.getWorld() != b.getWorld()) {
                iterator.remove();
                continue;
            }

            World world = a.getWorld();
            if (!world.isChunkLoaded(a.getLocation().getBlockX() >> 4, a.getLocation().getBlockZ() >> 4)
                    || !world.isChunkLoaded(b.getLocation().getBlockX() >> 4, b.getLocation().getBlockZ() >> 4)) {
                continue;
            }

            Vector offset = b.getLocation().toVector().subtract(a.getLocation().toVector());
            offset.setY(0);
            double distance = offset.length();
            if (distance < 0.001) {
                continue;
            }

            Vector direction = offset.normalize();
            double targetDistance = Math.max(0.8, getConfig().getDouble("target-distance", 1.35));
            double spring = Math.max(0.0, getConfig().getDouble("spring-strength", 0.08));
            double maxCorrection = Math.max(0.0, getConfig().getDouble("max-velocity-correction", 0.08));

            Vector velocityA = a.getVelocity();
            Vector velocityB = b.getVelocity();
            double relativeSpeed = velocityB.clone().subtract(velocityA).dot(direction);
            double correction = (distance - targetDistance) * spring + relativeSpeed * spring;
            correction = Math.max(-maxCorrection, Math.min(maxCorrection, correction));

            // Apply a gentle horizontal correction rather than teleporting either cart.
            velocityA.add(direction.clone().multiply(correction));
            velocityB.subtract(direction.clone().multiply(correction));
            a.setVelocity(velocityA);
            b.setVelocity(velocityB);
        }
    }

    private int unlinkCart(UUID cartId) {
        int before = links.size();
        links.removeIf(link -> link.first().equals(cartId) || link.second().equals(cartId));
        return before - links.size();
    }

    private int linkCount(UUID cartId) {
        int count = 0;
        for (CartLink link : links) {
            if (link.first().equals(cartId) || link.second().equals(cartId)) {
                count++;
            }
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
            sender.sendMessage(ChatColor.GOLD + "Minecraft Connector");
            sender.sendMessage(ChatColor.YELLOW + "Hold a chain and right-click two minecarts to connect them.");
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
            sender.sendMessage(ChatColor.GREEN + "Minecraft Connector settings reloaded.");
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
