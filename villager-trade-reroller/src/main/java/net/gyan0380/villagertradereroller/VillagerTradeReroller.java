package net.gyan0380.villagertradereroller;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VillagerTradeReroller extends JavaPlugin implements Listener {
    private static final int MENU_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final List<EnchantTarget> targets = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadTargets();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Villager Trade Reroller enabled with " + targets.size() + " enchantment/level choices.");
    }

    @Override
    public void onDisable() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
        attempts.clear();
    }

    private void loadTargets() {
        targets.clear();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            for (int level = 1; level <= enchantment.getMaxLevel(); level++) {
                targets.add(new EnchantTarget(enchantment, level));
            }
        }
        targets.sort(Comparator.comparing((EnchantTarget t) -> t.enchantment().getKey().getKey())
                .thenComparingInt(EnchantTarget::level));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("tradereroller.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            stopTask(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Your villager reroll task was stopped.");
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("select")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /traderoll [select|stop]");
            return true;
        }

        RayTraceResult trace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 6.0, 0.5,
                entity -> entity instanceof Villager);
        Entity hit = trace == null ? null : trace.getHitEntity();
        if (!(hit instanceof Villager villager)) {
            player.sendMessage(ChatColor.RED + "Look directly at a villager within 6 blocks, then run /traderoll.");
            return true;
        }
        if (villager.getVillagerExperience() > 0 || villager.getRecipes().stream().anyMatch(recipe -> recipe.getUses() > 0)) {
            player.sendMessage(ChatColor.RED + "This villager has already been traded with. Its trades cannot be safely rerolled.");
            return true;
        }
        if (getConfig().getBoolean("set-librarian-profession", true)) {
            villager.setProfession(Villager.Profession.LIBRARIAN);
        } else if (villager.getProfession() != Villager.Profession.LIBRARIAN) {
            player.sendMessage(ChatColor.RED + "This villager is not a librarian. Enable set-librarian-profession in config.yml.");
            return true;
        }
        openMenu(player, villager, 0);
        return true;
    }

    private void openMenu(Player player, Villager villager, int page) {
        MenuHolder holder = new MenuHolder(villager.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE,
                ChatColor.DARK_GREEN + "Choose Enchanted Book " + (page + 1) + "/" + Math.max(1, (targets.size() + PAGE_SIZE - 1) / PAGE_SIZE));
        holder.setInventory(inventory);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, targets.size());
        for (int index = start; index < end; index++) {
            EnchantTarget target = targets.get(index);
            ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) icon.getItemMeta();
            meta.addStoredEnchant(target.enchantment(), target.level(), true);
            meta.setDisplayName(ChatColor.GREEN + pretty(target.enchantment()) + " " + roman(target.level()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to search for this exact book trade");
            lore.add(ChatColor.DARK_GRAY + target.enchantment().getKey().toString());
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(index - start, icon);
            holder.targetsBySlot.put(index - start, target);
        }
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inventory.setItem(49, named(Material.BARRIER, ChatColor.RED + "Cancel"));
        if (end < targets.size()) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Next page"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == 49) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Trade search cancelled.");
            return;
        }
        if (slot == 45 && holder.page > 0) {
            openMenu(player, getVillager(holder.villagerId), holder.page - 1);
            return;
        }
        if (slot == 53 && (holder.page + 1) * PAGE_SIZE < targets.size()) {
            openMenu(player, getVillager(holder.villagerId), holder.page + 1);
            return;
        }
        EnchantTarget target = holder.targetsBySlot.get(slot);
        Villager villager = getVillager(holder.villagerId);
        if (target == null || villager == null || !villager.isValid()) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "The selected villager is no longer available.");
            return;
        }
        if (villager.getVillagerExperience() > 0 || villager.getRecipes().stream().anyMatch(recipe -> recipe.getUses() > 0)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "This villager has been traded with; search stopped for safety.");
            return;
        }
        player.closeInventory();
        startReroll(player, villager, target);
    }

    private void startReroll(Player player, Villager villager, EnchantTarget target) {
        stopTask(player.getUniqueId());
        UUID playerId = player.getUniqueId();
        UUID villagerId = villager.getUniqueId();
        attempts.put(playerId, 0);
        player.sendMessage(ChatColor.GOLD + "Searching for " + pretty(target.enchantment()) + " " + roman(target.level()) + "...");

        long delay = Math.max(1L, getConfig().getLong("reroll-delay-ticks", 2L));
        int maxAttempts = Math.max(1, getConfig().getInt("max-attempts", 2000));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Player online = Bukkit.getPlayer(playerId);
            Villager current = getVillager(villagerId);
            if (online == null || current == null || !current.isValid() || current.isDead()) {
                stopTask(playerId);
                return;
            }
            if (current.getVillagerExperience() > 0 || current.getRecipes().stream().anyMatch(recipe -> recipe.getUses() > 0)) {
                online.sendMessage(ChatColor.RED + "Search stopped: the villager has been traded with.");
                stopTask(playerId);
                return;
            }

            int count = attempts.merge(playerId, 1, Integer::sum);
            current.resetOffers();
            int extra = Math.max(0, getConfig().getInt("additional-trades", 0));
            if (extra > 0) current.addTrades(extra);

            boolean found = current.getRecipes().stream().anyMatch(recipe -> matches(recipe.getResult(), target));
            if (found) {
                online.sendMessage(ChatColor.GREEN + "FOUND! " + pretty(target.enchantment()) + " " + roman(target.level())
                        + " is now in this villager's offers. Trade once to lock the offers.");
                current.shakeHead();
                stopTask(playerId);
                return;
            }
            if (count >= maxAttempts) {
                online.sendMessage(ChatColor.RED + "Search stopped after " + maxAttempts + " attempts. No match found.");
                stopTask(playerId);
            } else if (count % 100 == 0) {
                online.sendActionBar(ChatColor.YELLOW + "Searching... " + count + "/" + maxAttempts);
            }
        }, 1L, delay);
        activeTasks.put(playerId, task);
    }

    private boolean matches(ItemStack result, EnchantTarget target) {
        if (result == null || result.getType() != Material.ENCHANTED_BOOK) return false;
        if (!(result.getItemMeta() instanceof EnchantmentStorageMeta meta)) return false;
        return meta.getStoredEnchantLevel(target.enchantment()) == target.level();
    }

    private Villager getVillager(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof Villager villager ? villager : null;
    }

    private void stopTask(UUID playerId) {
        BukkitTask task = activeTasks.remove(playerId);
        if (task != null) task.cancel();
        attempts.remove(playerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopTask(event.getPlayer().getUniqueId());
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private String pretty(Enchantment enchantment) {
        String key = enchantment.getKey().getKey().replace('_', ' ');
        String[] words = key.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String roman(int number) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number > 0 && number < numerals.length ? numerals[number] : Integer.toString(number);
    }

    private record EnchantTarget(Enchantment enchantment, int level) {}

    private static final class MenuHolder implements InventoryHolder {
        private final UUID villagerId;
        private final int page;
        private final Map<Integer, EnchantTarget> targetsBySlot = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(UUID villagerId, int page) {
            this.villagerId = villagerId;
            this.page = page;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
