package net.gyan0380.villagertradereroller;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VillagerTradeReroller extends JavaPlugin implements Listener {
    private static final int MENU_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Villager Trade Reroller enabled for all supported villager professions.");
    }

    @Override
    public void onDisable() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
        attempts.clear();
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
        if (!isUntraded(villager)) {
            player.sendMessage(ChatColor.RED + "This villager has already been traded with. Its trades are locked.");
            return true;
        }
        if (villager.getProfession() == Villager.Profession.NONE
                || villager.getProfession() == Villager.Profession.NITWIT) {
            player.sendMessage(ChatColor.RED + "This villager has no trading profession. Give it a workstation first.");
            return true;
        }

        if (villager.getRecipes().isEmpty()) villager.resetOffers();
        if (villager.getRecipes().isEmpty()) {
            player.sendMessage(ChatColor.RED + "No trades are available for this villager yet.");
            return true;
        }
        openTradeMenu(player, villager, 0);
        return true;
    }

    private boolean isUntraded(Villager villager) {
        return villager.getVillagerExperience() <= 0
                && villager.getRecipes().stream().noneMatch(recipe -> recipe.getUses() > 0);
    }

    private void openTradeMenu(Player player, Villager villager, int page) {
        List<MerchantRecipe> recipes = villager.getRecipes();
        MenuHolder holder = new MenuHolder(MenuType.TRADES, villager.getUniqueId(), page);
        int pages = Math.max(1, (recipes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE,
                ChatColor.DARK_GREEN + villager.getProfession().name() + " Trades " + (page + 1) + "/" + pages);
        holder.setInventory(inventory);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, recipes.size());
        for (int index = start; index < end; index++) {
            MerchantRecipe recipe = recipes.get(index);
            ItemStack icon = recipe.getResult().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + readableName(icon.getType()));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Trade " + (index + 1));
                lore.add(ChatColor.GRAY + "Uses: " + recipe.getUses() + "/" + recipe.getMaxUses());
                lore.add(ChatColor.YELLOW + "Click to select this trade");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(index - start, icon);
            holder.tradeTargets.put(index - start, recipe.getResult().clone());
        }
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inventory.setItem(49, named(Material.BARRIER, ChatColor.RED + "Cancel"));
        if (end < recipes.size()) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Next page"));
        player.openInventory(inventory);
    }

    private void openConfirmMenu(Player player, UUID villagerId, ItemStack target) {
        MenuHolder holder = new MenuHolder(MenuType.CONFIRM, villagerId, 0);
        holder.target = target.clone();
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_GREEN + "Confirm Trade Search");
        holder.setInventory(inventory);

        inventory.setItem(11, named(Material.LIME_CONCRETE, ChatColor.GREEN + "CONFIRM - Start Reroll"));
        ItemStack preview = target.clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Target trade result");
            meta.setLore(List.of(ChatColor.GRAY + "The villager will reroll until this result appears.",
                    ChatColor.GRAY + "Only the result item is matched."));
            preview.setItemMeta(meta);
        }
        inventory.setItem(13, preview);
        inventory.setItem(15, named(Material.RED_CONCRETE, ChatColor.RED + "Cancel"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (holder.type == MenuType.CONFIRM) {
            if (slot == 15) {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Trade search cancelled.");
                return;
            }
            if (slot == 11) {
                Villager villager = getVillager(holder.villagerId);
                if (villager == null || !villager.isValid() || !isUntraded(villager)) {
                    player.closeInventory();
                    player.sendMessage(ChatColor.RED + "The villager is no longer available or has been traded with.");
                    return;
                }
                ItemStack target = holder.target.clone();
                player.closeInventory();
                startReroll(player, villager, target);
            }
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Trade selection cancelled.");
            return;
        }
        if (slot == 45 && holder.page > 0) {
            Villager villager = getVillager(holder.villagerId);
            if (villager != null) openTradeMenu(player, villager, holder.page - 1);
            return;
        }
        if (slot == 53) {
            Villager villager = getVillager(holder.villagerId);
            if (villager != null && (holder.page + 1) * PAGE_SIZE < villager.getRecipes().size()) {
                openTradeMenu(player, villager, holder.page + 1);
            }
            return;
        }

        ItemStack target = holder.tradeTargets.get(slot);
        Villager villager = getVillager(holder.villagerId);
        if (target == null || villager == null || !villager.isValid() || !isUntraded(villager)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "The selected villager is no longer available.");
            return;
        }
        openConfirmMenu(player, holder.villagerId, target);
    }

    private void startReroll(Player player, Villager villager, ItemStack target) {
        stopTask(player.getUniqueId());
        UUID playerId = player.getUniqueId();
        UUID villagerId = villager.getUniqueId();
        attempts.put(playerId, 0);
        player.sendMessage(ChatColor.GOLD + "Searching this villager's " + villager.getProfession().name()
                + " trades for " + readableName(target.getType()) + "...");

        long delay = Math.max(1L, getConfig().getLong("reroll-delay-ticks", 2L));
        int maxAttempts = Math.max(1, getConfig().getInt("max-attempts", 2000));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Player online = Bukkit.getPlayer(playerId);
            Villager current = getVillager(villagerId);
            if (online == null || current == null || !current.isValid() || current.isDead()) {
                stopTask(playerId);
                return;
            }
            if (!isUntraded(current)) {
                online.sendMessage(ChatColor.RED + "Search stopped: the villager has been traded with.");
                stopTask(playerId);
                return;
            }

            int count = attempts.merge(playerId, 1, Integer::sum);
            current.resetOffers();
            int extra = Math.max(0, getConfig().getInt("additional-trades", 0));
            if (extra > 0) current.addTrades(extra);

            boolean found = current.getRecipes().stream()
                    .anyMatch(recipe -> recipe.getResult().isSimilar(target));
            if (found) {
                online.sendMessage(ChatColor.GREEN + "FOUND! " + readableName(target.getType())
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

    private String readableName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private enum MenuType { TRADES, CONFIRM }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final UUID villagerId;
        private final int page;
        private final Map<Integer, ItemStack> tradeTargets = new HashMap<>();
        private ItemStack target;
        private Inventory inventory;

        private MenuHolder(MenuType type, UUID villagerId, int page) {
            this.type = type;
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
