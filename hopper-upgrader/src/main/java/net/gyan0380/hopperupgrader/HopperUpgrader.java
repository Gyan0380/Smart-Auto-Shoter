package net.gyan0380.hopperupgrader;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HopperUpgrader extends JavaPlugin implements Listener {
    private static final int EXTRA_SLOTS = 5;
    private static final String MENU_TITLE = "§8Hopper Upgrader";
    private static final long DATA_SAVE_INTERVAL_TICKS = 100L;

    private final Map<String, UpgradedHopper> upgraded = new HashMap<>();
    private final Map<UUID, String> linkMode = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private BukkitTask transferTask;
    private BukkitTask saveTask;
    private boolean dataDirty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "hoppers.yml");
        loadHoppers();
        getServer().getPluginManager().registerEvents(this, this);
        long interval = Math.max(1, getConfig().getLong("transfer.interval-ticks", 2));
        transferTask = getServer().getScheduler().runTaskTimer(this, this::transferItems,
                interval, interval);
        saveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (dataDirty) saveHoppers();
        }, DATA_SAVE_INTERVAL_TICKS, DATA_SAVE_INTERVAL_TICKS);
        getLogger().info("Loaded " + upgraded.size() + " upgraded hopper(s).");
    }

    @Override
    public void onDisable() {
        if (transferTask != null) transferTask.cancel();
        if (saveTask != null) saveTask.cancel();
        saveHoppers();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§6§lHopper Upgrader §7- faster item routing for Paper");
        sender.sendMessage("§e1. §fHold a copper hoe and sneak-right-click a hopper to upgrade/open it.");
        sender.sendMessage("§e2. §fUse the GUI to store items in five extra slots.");
        sender.sendMessage("§e3. §fClick Link Output, then sneak-right-click a chest, furnace, barrel, or hopper.");
        sender.sendMessage("§e4. §fAdd up to " + maxLinks() + " outputs. Click one in the menu to unlink it.");
        sender.sendMessage("§7Hoppers pull from above and move up to "
                + transferAmount() + " items every " + getConfig().getLong("transfer.interval-ticks", 2)
                + " tick(s). Existing vanilla hopper behavior remains available.");
        sender.sendMessage("§7Use /hopperupgrader anywhere to see these instructions again.");
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUpgradedHopperBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        String hopperKey = key(brokenBlock.getLocation());
        getServer().getScheduler().runTask(this, () -> {
            if (brokenBlock.getType() == Material.HOPPER) return;

            UpgradedHopper hopper = upgraded.remove(hopperKey);
            if (hopper == null) return;

            for (ItemStack stack : hopper.storage) {
                if (stack == null || stack.getType().isAir()) continue;
                brokenBlock.getWorld().dropItemNaturally(brokenBlock.getLocation(), stack);
            }
            saveHoppers();
        });
    }

    @EventHandler
    public void onHopperUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        String key = key(clicked.getLocation());

        String sourceKey = linkMode.get(player.getUniqueId());
        if (sourceKey != null) {
            event.setCancelled(true);
            if (!player.isSneaking()) {
                player.sendMessage("§eSneak and right-click a container to add it as an output.");
                return;
            }
            addLink(player, sourceKey, clicked);
            return;
        }

        if (clicked.getType() != Material.HOPPER || !player.isSneaking()
                || !isCopperHoe(event.getItem())) return;
        if (!player.hasPermission("hopperupgrader.use")) {
            player.sendMessage("§cYou do not have permission to configure hoppers.");
            return;
        }

        event.setCancelled(true);
        upgraded.computeIfAbsent(key, ignored -> new UpgradedHopper());
        saveHoppers();
        openMenu(player, key);
        player.sendMessage("§aHopper upgraded. Its five bonus slots and faster routing are ready.");
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HopperMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UpgradedHopper hopper = upgraded.get(menu.locationKey);
        if (hopper == null) {
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.getClick().isShiftClick()) {
                moveFromPlayerInventory(event, hopper);
                refreshMenu(menu, hopper);
                saveHoppers();
            } else {
                event.setCancelled(false);
            }
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= 10 && slot <= 14) {
            editStorage(player, hopper, slot - 10, event.getClick());
            refreshMenu(menu, hopper);
            saveHoppers();
            return;
        }
        if (slot == 18) {
            if (hopper.links.size() >= maxLinks()) {
                player.sendMessage("§cThis hopper already has the maximum of " + maxLinks() + " outputs.");
                return;
            }
            linkMode.put(player.getUniqueId(), menu.locationKey);
            player.closeInventory();
            player.sendMessage("§aLink mode ready. Sneak-right-click a chest, furnace, barrel, or hopper.");
            player.sendMessage("§7Click a listed output in the menu to remove it.");
            return;
        }
        if (slot >= 20 && slot <= 22) {
            int linkIndex = slot - 20;
            if (linkIndex < hopper.links.size()) {
                String removed = hopper.links.remove(linkIndex);
                saveHoppers();
                refreshMenu(menu, hopper);
                player.sendMessage("§eOutput unlinked: §f" + displayName(removed));
            }
            return;
        }
        if (slot == 26) player.closeInventory();
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HopperMenu menu) {
            UpgradedHopper hopper = upgraded.get(menu.locationKey);
            if (hopper != null) saveHoppers();
        }
    }

    private void addLink(Player player, String sourceKey, Block target) {
        UpgradedHopper source = upgraded.get(sourceKey);
        if (source == null) {
            linkMode.remove(player.getUniqueId());
            player.sendMessage("§cThat upgraded hopper is no longer available.");
            return;
        }
        if (!(target.getState() instanceof Container)) {
            player.sendMessage("§cChoose a chest, furnace, barrel, or another container block.");
            return;
        }
        String targetKey = key(target.getLocation());
        if (sourceKey.equals(targetKey)) {
            player.sendMessage("§cA hopper cannot link to itself.");
            return;
        }
        if (source.links.contains(targetKey)) {
            player.sendMessage("§eThat output is already linked.");
            return;
        }
        if (source.links.size() >= maxLinks()) {
            player.sendMessage("§cThis hopper already has the maximum of " + maxLinks() + " outputs.");
            return;
        }
        source.links.add(targetKey);
        linkMode.remove(player.getUniqueId());
        saveHoppers();
        player.sendMessage("§aOutput linked. Items will be distributed between connected containers.");
        openMenu(player, sourceKey);
    }

    private void openMenu(Player player, String locationKey) {
        UpgradedHopper hopper = upgraded.get(locationKey);
        if (hopper == null) return;
        HopperMenu holder = new HopperMenu(locationKey);
        Inventory inventory = Bukkit.createInventory(holder, 27, MENU_TITLE);
        holder.inventory = inventory;
        refreshMenu(holder, hopper);
        player.openInventory(inventory);
    }

    private void refreshMenu(HopperMenu menu, UpgradedHopper hopper) {
        Inventory inventory = menu.inventory;
        inventory.clear();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < 9 || slot > 17) {
                inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
        }

        inventory.setItem(4, item(Material.HOPPER, "§6§lHopper Upgrader", List.of(
                "§7Five bonus storage slots are below.",
                "§7Transfers run every " + getConfig().getLong("transfer.interval-ticks", 2) + " tick(s).")));

        for (int slot = 0; slot < EXTRA_SLOTS; slot++) {
            ItemStack stored = hopper.storage[slot];
            if (stored == null) {
                inventory.setItem(10 + slot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "§7Bonus slot " + (slot + 1),
                        List.of("§8Click with an item on your cursor to store it.")));
            } else {
                inventory.setItem(10 + slot, stored.clone());
            }
        }

        inventory.setItem(18, item(Material.COMPASS, "§bLink an output", List.of(
                "§7Connect this hopper to another container.",
                "§7Up to " + maxLinks() + " outputs per hopper.")));

        for (int index = 0; index < 3; index++) {
            int menuSlot = 20 + index;
            if (index < hopper.links.size()) {
                inventory.setItem(menuSlot, item(Material.CHEST, "§eOutput " + (index + 1), List.of(
                        "§7" + displayName(hopper.links.get(index)), "§cClick to unlink")));
            } else {
                inventory.setItem(menuSlot, item(Material.BLACK_STAINED_GLASS_PANE, "§8No output", List.of()));
            }
        }

        inventory.setItem(26, item(Material.BARRIER, "§cClose", List.of()));
    }

    private void moveFromPlayerInventory(InventoryClickEvent event, UpgradedHopper hopper) {
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.getType().isAir()) return;

        int remaining = moving.getAmount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < hopper.storage.length && remaining > 0; slot++) {
                ItemStack stored = hopper.storage[slot];
                boolean matchingStack = stored != null && stored.isSimilar(moving);
                if ((pass == 0 && !matchingStack) || (pass == 1 && stored != null)) continue;

                int capacity = stored == null
                        ? moving.getMaxStackSize()
                        : stored.getMaxStackSize() - stored.getAmount();
                int count = Math.min(remaining, capacity);
                if (count <= 0) continue;

                if (stored == null) {
                    stored = moving.clone();
                    stored.setAmount(count);
                    hopper.storage[slot] = stored;
                } else {
                    stored.setAmount(stored.getAmount() + count);
                }
                remaining -= count;
            }
        }

        if (remaining == moving.getAmount()) return;
        if (remaining == 0) {
            event.setCurrentItem(null);
        } else {
            moving.setAmount(remaining);
            event.setCurrentItem(moving);
        }
    }

    private void editStorage(Player player, UpgradedHopper hopper, int index, ClickType click) {
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        ItemStack stored = hopper.storage[index];
        ItemStack cursor = player.getItemOnCursor();
        if (click.isRightClick()) {
            if (cursor == null || cursor.getType().isAir()) {
                if (stored == null) return;
                int count = (stored.getAmount() + 1) / 2;
                ItemStack picked = stored.clone();
                picked.setAmount(count);
                stored.setAmount(stored.getAmount() - count);
                hopper.storage[index] = stored.getAmount() == 0 ? null : stored;
                player.setItemOnCursor(picked);
            } else if (stored == null) {
                ItemStack placed = cursor.clone();
                placed.setAmount(1);
                hopper.storage[index] = placed;
                takeFromCursor(player, cursor, 1);
            } else if (stored.isSimilar(cursor) && stored.getAmount() < stored.getMaxStackSize()) {
                stored.setAmount(stored.getAmount() + 1);
                takeFromCursor(player, cursor, 1);
            }
            return;
        }

        if (cursor == null || cursor.getType().isAir()) {
            if (stored == null) return;
            hopper.storage[index] = null;
            player.setItemOnCursor(stored.clone());
        } else if (stored == null) {
            hopper.storage[index] = cursor.clone();
            player.setItemOnCursor(null);
        } else if (stored.isSimilar(cursor) && stored.getAmount() < stored.getMaxStackSize()) {
            int moved = Math.min(cursor.getAmount(), stored.getMaxStackSize() - stored.getAmount());
            stored.setAmount(stored.getAmount() + moved);
            takeFromCursor(player, cursor, moved);
        } else {
            hopper.storage[index] = cursor.clone();
            player.setItemOnCursor(stored.clone());
        }
    }

    private void takeFromCursor(Player player, ItemStack cursor, int amount) {
        if (cursor.getAmount() <= amount) player.setItemOnCursor(null);
        else {
            cursor.setAmount(cursor.getAmount() - amount);
            player.setItemOnCursor(cursor);
        }
    }

    private void transferItems() {
        int budget = transferAmount();
        for (Map.Entry<String, UpgradedHopper> entry : upgraded.entrySet()) {
            Block block = resolve(entry.getKey());
            if (block == null || block.getType() != Material.HOPPER) continue;
            UpgradedHopper hopper = entry.getValue();
            int moved = pullFromAbove(block, hopper, budget);
            moved += pushToOutputs(block, hopper, Math.max(0, budget - moved));
            if (moved > 0) dataDirty = true;
        }
    }

    private Inventory hopperBlockInventory(Block block) {
        return ((Container) block.getState()).getInventory();
    }

    private int pullFromAbove(Block hopperBlock, UpgradedHopper hopper, int budget) {
        Block above = hopperBlock.getRelative(0, 1, 0);
        if (!(above.getState() instanceof Container source)) return 0;

        Inventory sourceInventory = source.getInventory();
        Inventory hopperInventory = hopperBlockInventory(hopperBlock);
        int moved = 0;
        for (int sourceSlot = 0; sourceSlot < sourceInventory.getSize() && moved < budget; sourceSlot++) {
            ItemStack item = sourceInventory.getItem(sourceSlot);
            if (item == null || item.getType().isAir()) continue;

            for (int targetSlot = 0; targetSlot < hopper.storage.length && moved < budget; targetSlot++) {
                ItemStack stored = hopper.storage[targetSlot];
                if (stored != null && (!stored.isSimilar(item)
                        || stored.getAmount() >= stored.getMaxStackSize())) {
                    continue;
                }

                int capacity = stored == null
                        ? item.getMaxStackSize()
                        : stored.getMaxStackSize() - stored.getAmount();
                int count = Math.min(Math.min(item.getAmount(), capacity), budget - moved);
                if (count <= 0) continue;

                ItemStack offered = item.clone();
                offered.setAmount(count);
                InventoryMoveItemEvent move = new InventoryMoveItemEvent(
                        sourceInventory, offered, hopperInventory, true);
                Bukkit.getPluginManager().callEvent(move);
                if (move.isCancelled() || move.getItem() == null || !move.getItem().isSimilar(item)) continue;

                count = Math.min(count, move.getItem().getAmount());
                if (stored == null) {
                    stored = item.clone();
                    stored.setAmount(count);
                    hopper.storage[targetSlot] = stored;
                } else {
                    stored.setAmount(stored.getAmount() + count);
                }

                item.setAmount(item.getAmount() - count);
                sourceInventory.setItem(sourceSlot, item.getAmount() <= 0 ? null : item);
                moved += count;
            }
        }
        return moved;
    }

    private Map<Integer, ItemStack> insertIntoContainer(Inventory inventory, ItemStack offered) {
        if (!(inventory instanceof FurnaceInventory)) return inventory.addItem(offered);
        int slot = offered.getType().isFuel() ? 1 : 0;
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(slot, offered.clone());
            return Map.of();
        }
        if (!current.isSimilar(offered) || current.getAmount() >= current.getMaxStackSize())
            return Map.of(slot, offered.clone());
        int accepted = Math.min(offered.getAmount(), current.getMaxStackSize() - current.getAmount());
        current.setAmount(current.getAmount() + accepted);
        inventory.setItem(slot, current);
        if (accepted == offered.getAmount()) return Map.of();
        ItemStack leftover = offered.clone();
        leftover.setAmount(offered.getAmount() - accepted);
        return Map.of(slot, leftover);
    }

    private int pushToOutputs(Block hopperBlock, UpgradedHopper hopper, int budget) {
        if (budget <= 0) return 0;
        List<Inventory> targets = new ArrayList<>();
        for (String link : hopper.links) {
            Block block = resolve(link);
            if (block != null && block.getState() instanceof Container container) {
                targets.add(container.getInventory());
            }
        }
        if (targets.isEmpty()) {
            Block below = hopperBlock.getRelative(0, -1, 0);
            if (below.getState() instanceof Container container) {
                targets.add(container.getInventory());
            }
        }
        if (targets.isEmpty()) return 0;

        int moved = 0;
        for (int slot = 0; slot < hopper.storage.length && moved < budget; slot++) {
            ItemStack stored = hopper.storage[slot];
            if (stored == null || stored.getType().isAir()) continue;
            int tries = targets.size();
            while (tries-- > 0 && stored != null && moved < budget) {
                int index = hopper.nextOutput++ % targets.size();
                int count = Math.min(stored.getAmount(), budget - moved);
                ItemStack offered = stored.clone();
                offered.setAmount(count);
                InventoryMoveItemEvent move = new InventoryMoveItemEvent(hopperBlockInventory(hopperBlock),
                        offered, targets.get(index), true);
                Bukkit.getPluginManager().callEvent(move);
                if (move.isCancelled() || move.getItem() == null || !move.getItem().isSimilar(stored)) continue;
                offered = move.getItem();
                Map<Integer, ItemStack> leftovers = insertIntoContainer(targets.get(index), offered);
                int accepted = Math.min(count, offered.getAmount());
                for (ItemStack leftover : leftovers.values()) accepted -= leftover.getAmount();
                if (accepted > 0) {
                    stored.setAmount(stored.getAmount() - accepted);
                    moved += accepted;
                    if (stored.getAmount() <= 0) {
                        hopper.storage[slot] = null;
                        stored = null;
                    }
                }
            }
        }
        return moved;
    }

    private void loadHoppers() {
        if (!dataFile.exists()) {
            data = new YamlConfiguration();
            return;
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
        var savedHoppers = data.getConfigurationSection("hoppers");
        if (savedHoppers == null) return;

        for (String key : savedHoppers.getKeys(false)) {
            UpgradedHopper hopper = new UpgradedHopper();
            for (int slot = 0; slot < EXTRA_SLOTS; slot++) {
                hopper.storage[slot] = data.getItemStack("hoppers." + key + ".storage." + slot);
            }
            hopper.links.addAll(data.getStringList("hoppers." + key + ".links"));
            upgraded.put(key, hopper);
        }
    }

    private void saveHoppers() {
        dataDirty = false;
        if (data == null) data = new YamlConfiguration();
        data.set("hoppers", null);
        for (Map.Entry<String, UpgradedHopper> entry : upgraded.entrySet()) {
            String path = "hoppers." + entry.getKey();
            UpgradedHopper hopper = entry.getValue();
            for (int i = 0; i < EXTRA_SLOTS; i++) {
                data.set(path + ".storage." + i, hopper.storage[i]);
            }
            data.set(path + ".links", hopper.links);
        }
        try {
            getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save hopper data: " + exception.getMessage());
        }
    }

    private int maxLinks() {
        return Math.max(1, Math.min(3, getConfig().getInt("links.max-per-hopper", 3)));
    }

    private int transferAmount() {
        return Math.max(1, Math.min(64, getConfig().getInt("transfer.items-per-cycle", 4)));
    }

    private String key(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    private Block resolve(String key) {
        String[] parts = key.split(",", 4);
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return world.getBlockAt(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String displayName(String key) {
        Block block = resolve(key);
        return block == null ? "Missing container" : block.getType().name().toLowerCase().replace('_', ' ')
                + " at " + block.getX() + ", " + block.getY() + ", " + block.getZ();
    }

    private boolean isCopperHoe(ItemStack item) {
        return item != null && item.getType() == Material.COPPER_HOE;
    }

    private ItemStack item(Material type, String name, List<String> lore) {
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private final class HopperMenu implements InventoryHolder {
        private final String locationKey;
        private Inventory inventory;

        private HopperMenu(String locationKey) {
            this.locationKey = locationKey;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class UpgradedHopper {
        private final ItemStack[] storage = new ItemStack[EXTRA_SLOTS];
        private final List<String> links = new ArrayList<>();
        private int nextOutput;
    }
}
