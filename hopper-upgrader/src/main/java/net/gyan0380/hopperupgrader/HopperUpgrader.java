package net.gyan0380.hopperupgrader;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
    private static final int MENU_SIZE = 36;
    private static final int NORMAL_SLOT_START = 10;
    private static final int EXTRA_SLOT_START = 19;
    private static final int LINK_BUTTON_SLOT = 28;
    private static final int FIRST_LINK_SLOT = 30;
    private static final int CLOSE_BUTTON_SLOT = 35;
    private static final String MENU_TITLE = "§8Hopper Upgrader";
    private static final long DATA_SAVE_INTERVAL_TICKS = 100L;

    private final Map<String, UpgradedHopper> upgraded = new HashMap<>();
    private final Map<UUID, String> linkMode = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private BukkitTask transferTask;
    private BukkitTask saveTask;
    private boolean dataDirty;
    private boolean linkedOutputMove;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getInt("links.max-per-hopper", 3) == 3) {
            getConfig().set("links.max-per-hopper", 4);
            saveConfig();
        }
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
        sender.sendMessage("§e3. §fClick Link Output, then sneak-right-click an adjacent container on one of four sides.");
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
                player.sendMessage("§eSneak and right-click a container on one of its four horizontal sides.");
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
        player.sendMessage("§aHopper upgraded. Its normal slots, five bonus slots, and faster routing are ready.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onVanillaLinkedHopperMove(InventoryMoveItemEvent event) {
        if (linkedOutputMove) return;
        for (Map.Entry<String, UpgradedHopper> entry : upgraded.entrySet()) {
            UpgradedHopper hopper = entry.getValue();
            if (hopper.links.isEmpty()) continue;
            Block block = resolve(entry.getKey());
            if (block == null || block.getType() != Material.HOPPER) continue;
            if (!event.getSource().equals(hopperBlockInventory(block))) continue;
            if (hasLinkedOutput(hopper)) event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HopperMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UpgradedHopper hopper = upgraded.get(menu.locationKey);
        Inventory normalInventory = getHopperInventory(menu.locationKey);
        if (hopper == null || normalInventory == null) {
            player.closeInventory();
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (event.getClick().isShiftClick()) {
                moveFromPlayerInventory(event, normalInventory, hopper);
                refreshMenu(menu, hopper);
                saveHoppers();
            } else {
                event.setCancelled(false);
            }
            return;
        }

        int slot = event.getRawSlot();
        if (slot >= NORMAL_SLOT_START && slot < NORMAL_SLOT_START + EXTRA_SLOTS) {
            editInventorySlot(player, normalInventory, slot - NORMAL_SLOT_START, event.getClick());
            refreshMenu(menu, hopper);
            return;
        }
        if (slot >= EXTRA_SLOT_START && slot < EXTRA_SLOT_START + EXTRA_SLOTS) {
            editStorage(player, hopper, slot - EXTRA_SLOT_START, event.getClick());
            refreshMenu(menu, hopper);
            saveHoppers();
            return;
        }
        if (slot == LINK_BUTTON_SLOT) {
            if (hopper.links.size() >= maxLinks()) {
                player.sendMessage("§cThis hopper already has the maximum of " + maxLinks() + " outputs.");
                return;
            }
            linkMode.put(player.getUniqueId(), menu.locationKey);
            player.closeInventory();
            player.sendMessage("§aLink mode ready. Sneak-right-click an adjacent container on one of its four horizontal sides.");
            player.sendMessage("§7Choose an open horizontal side; links must be directly adjacent.");
            return;
        }
        if (slot >= FIRST_LINK_SLOT && slot < FIRST_LINK_SLOT + 4) {
            int linkIndex = slot - FIRST_LINK_SLOT;
            if (linkIndex < hopper.links.size()) {
                String removed = hopper.links.remove(linkIndex);
                saveHoppers();
                refreshMenu(menu, hopper);
                player.sendMessage("§eOutput unlinked: §f" + displayName(removed));
            }
            return;
        }
        if (slot == CLOSE_BUTTON_SLOT) player.closeInventory();
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
        Block sourceBlock = resolve(sourceKey);
        if (sourceBlock == null || !isOneBlockAway(sourceBlock, target)) {
            player.sendMessage("§cThe output must be directly beside the upgraded hopper.");
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

    private boolean isOneBlockAway(Block first, Block second) {
        return first.getWorld().equals(second.getWorld()) && faceTo(first, second) != null;
    }

    private BlockFace faceTo(Block first, Block second) {
        if (first.getY() != second.getY()) return null;
        int dx = second.getX() - first.getX();
        int dz = second.getZ() - first.getZ();
        if (dx == 1 && dz == 0) return BlockFace.EAST;
        if (dx == -1 && dz == 0) return BlockFace.WEST;
        if (dx == 0 && dz == 1) return BlockFace.SOUTH;
        if (dx == 0 && dz == -1) return BlockFace.NORTH;
        return null;
    }

    private void openMenu(Player player, String locationKey) {
        UpgradedHopper hopper = upgraded.get(locationKey);
        if (hopper == null) return;

        HopperMenu holder = new HopperMenu(locationKey);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, MENU_TITLE);
        holder.inventory = inventory;
        refreshMenu(holder, hopper);
        player.openInventory(inventory);
    }

    private void refreshMenu(HopperMenu menu, UpgradedHopper hopper) {
        Inventory inventory = menu.inventory;
        Inventory normalInventory = getHopperInventory(menu.locationKey);
        if (normalInventory == null) return;

        inventory.clear();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        inventory.setItem(4, item(Material.HOPPER, "§6§lHopper Upgrader", List.of(
                "§7Slots 1-5 are the hopper's normal inventory.",
                "§7Slots 6-10 are bonus storage for linked outputs.")));

        for (int slot = 0; slot < EXTRA_SLOTS; slot++) {
            ItemStack normalItem = normalInventory.getItem(slot);
            inventory.setItem(NORMAL_SLOT_START + slot, normalItem == null
                    ? item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Normal slot " + (slot + 1), List.of())
                    : normalItem.clone());

            ItemStack bonusItem = hopper.storage[slot];
            inventory.setItem(EXTRA_SLOT_START + slot, bonusItem == null
                    ? item(Material.BLACK_STAINED_GLASS_PANE, "§7Bonus slot " + (slot + 1),
                            List.of("§8Items here use the linked output route."))
                    : bonusItem.clone());
        }

        inventory.setItem(LINK_BUTTON_SLOT, item(Material.COMPASS, "§bLink an output", List.of(
                "§7Connect one container on each horizontal side.",
                "§7Up to " + maxLinks() + " outputs per hopper.")));

        for (int index = 0; index < 4; index++) {
            int menuSlot = FIRST_LINK_SLOT + index;
            if (index < hopper.links.size()) {
                String targetKey = hopper.links.get(index);
                inventory.setItem(menuSlot, item(Material.CHEST, "§eOutput " + (index + 1), List.of(
                        "§7Side: " + linkedFaceName(menu.locationKey, targetKey),
                        "§7" + displayName(targetKey), "§cClick to unlink")));
            } else {
                inventory.setItem(menuSlot, item(Material.BLACK_STAINED_GLASS_PANE, "§8No output", List.of()));
            }
        }

        inventory.setItem(CLOSE_BUTTON_SLOT, item(Material.BARRIER, "§cClose", List.of()));
    }
    private void moveFromPlayerInventory(InventoryClickEvent event,
                                         Inventory normalInventory,
                                         UpgradedHopper hopper) {
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.getType().isAir()) return;

        Map<Integer, ItemStack> leftovers = normalInventory.addItem(moving.clone());
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (remaining > 0) {
            remaining = moveIntoBonusSlots(hopper, moving, remaining);
        }

        if (remaining == moving.getAmount()) return;
        if (remaining == 0) {
            event.setCurrentItem(null);
        } else {
            moving.setAmount(remaining);
            event.setCurrentItem(moving);
        }
    }

    private int moveIntoBonusSlots(UpgradedHopper hopper, ItemStack item, int amount) {
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < hopper.storage.length && remaining > 0; slot++) {
                ItemStack stored = hopper.storage[slot];
                boolean matchingStack = stored != null && stored.isSimilar(item);
                if ((pass == 0 && !matchingStack) || (pass == 1 && stored != null)) continue;

                int capacity = stored == null
                        ? item.getMaxStackSize()
                        : stored.getMaxStackSize() - stored.getAmount();
                int count = Math.min(remaining, capacity);
                if (count <= 0) continue;

                if (stored …127 tokens truncated…click != ClickType.RIGHT) return;

        ItemStack stored = inventory.getItem(slot);
        ItemStack cursor = player.getItemOnCursor();
        if (click.isRightClick()) {
            if (cursor == null || cursor.getType().isAir()) {
                if (stored == null) return;
                int count = (stored.getAmount() + 1) / 2;
                ItemStack picked = stored.clone();
                picked.setAmount(count);
                stored.setAmount(stored.getAmount() - count);
                inventory.setItem(slot, stored.getAmount() == 0 ? null : stored);
                player.setItemOnCursor(picked);
            } else if (stored == null) {
                ItemStack placed = cursor.clone();
                placed.setAmount(1);
                inventory.setItem(slot, placed);
                takeFromCursor(player, cursor, 1);
            } else if (stored.isSimilar(cursor) && stored.getAmount() < stored.getMaxStackSize()) {
                stored.setAmount(stored.getAmount() + 1);
                inventory.setItem(slot, stored);
                takeFromCursor(player, cursor, 1);
            }
            return;
        }

        if (cursor == null || cursor.getType().isAir()) {
            if (stored == null) return;
            inventory.setItem(slot, null);
            player.setItemOnCursor(stored.clone());
        } else if (stored == null) {
            inventory.setItem(slot, cursor.clone());
            player.setItemOnCursor(null);
        } else if (stored.isSimilar(cursor) && stored.getAmount() < stored.getMaxStackSize()) {
            int moved = Math.min(cursor.getAmount(), stored.getMaxStackSize() - stored.getAmount());
            stored.setAmount(stored.getAmount() + moved);
            inventory.setItem(slot, stored);
            takeFromCursor(player, cursor, moved);
        } else {
            inventory.setItem(slot, cursor.clone());
            player.setItemOnCursor(stored.clone());
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

            int moved;
            if (hopper.links.isEmpty()) {
                int pulled = pullFromAbove(block, hopper, budget);
                int pushed = pushToOutputs(block, hopper, budget);
                moved = pulled + pushed;
            } else {
                int routed = pushNormalToOutputs(block, hopper, budget);
                routed += pushToOutputs(block, hopper, Math.max(0, budget - routed));
                int pulled = pullFromAbove(block, hopper, budget);
                moved = routed + pulled;
            }
            if (moved > 0) dataDirty = true;
        }
    }
    private Inventory getHopperInventory(String locationKey) {
        Block block = resolve(locationKey);
        if (block == null || block.getType() != Material.HOPPER) return null;
        if (!(block.getState() instanceof Container container)) return null;
        return container.getInventory();
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

            int count = Math.min(item.getAmount(), budget - moved);
            ItemStack offered = item.clone();
            offered.setAmount(count);
            InventoryMoveItemEvent move = new InventoryMoveItemEvent(
                    sourceInventory, offered, hopperInventory, true);
            Bukkit.getPluginManager().callEvent(move);
            if (move.isCancelled() || move.getItem() == null || !move.getItem().isSimilar(item)) continue;

            int transferCount = Math.min(count, move.getItem().getAmount());
            if (transferCount <= 0) continue;
            offered = move.getItem().clone();
            offered.setAmount(transferCount);
            Map<Integer, ItemStack> leftovers = hopperInventory.addItem(offered);
            int accepted = transferCount;
            for (ItemStack leftover : leftovers.values()) accepted -= leftover.getAmount();
            if (accepted <= 0) continue;

            item.setAmount(item.getAmount() - accepted);
            sourceInventory.setItem(sourceSlot, item.getAmount() <= 0 ? null : item);
            moved += accepted;
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

    private int pushNormalToOutputs(Block hopperBlock, UpgradedHopper hopper, int budget) {
        if (budget <= 0 || hopper.links.isEmpty()) return 0;
        List<Inventory> targets = linkedOutputInventories(hopper);
        if (targets.isEmpty()) return 0;

        Inventory source = hopperBlockInventory(hopperBlock);
        int moved = 0;
        for (int slot = 0; slot < source.getSize() && moved < budget; slot++) {
            ItemStack stored = source.getItem(slot);
            if (stored == null || stored.getType().isAir()) continue;

            int targetIndex = targetWithRoom(targets, stored, hopper.nextOutput);
            if (targetIndex < 0) continue;
            int room = availableCapacity(targets.get(targetIndex), stored);
            int count = Math.min(Math.min(stored.getAmount(), budget - moved), room);
            if (count <= 0) {
                hopper.nextOutput = (targetIndex + 1) % targets.size();
                continue;
            }
            ItemStack offered = stored.clone();
            offered.setAmount(count);
            InventoryMoveItemEvent move = new InventoryMoveItemEvent(source, offered, targets.get(targetIndex), true);
            linkedOutputMove = true;
            try {
                Bukkit.getPluginManager().callEvent(move);
            } finally {
                linkedOutputMove = false;
            }
            if (move.isCancelled() || move.getItem() == null || !move.getItem().isSimilar(stored)) {
                hopper.nextOutput = (targetIndex + 1) % targets.size();
                continue;
            }

            offered = move.getItem().clone();
            int transferCount = Math.min(count, offered.getAmount());
            offered.setAmount(transferCount);
            Map<Integer, ItemStack> leftovers = insertIntoContainer(targets.get(targetIndex), offered);
            int accepted = transferCount;
            for (ItemStack leftover : leftovers.values()) accepted -= leftover.getAmount();
            if (accepted <= 0) {
                hopper.nextOutput = (targetIndex + 1) % targets.size();
                continue;
            }

            stored.setAmount(stored.getAmount() - accepted);
            source.setItem(slot, stored.getAmount() <= 0 ? null : stored);
            moved += accepted;
            hopper.nextOutput = targetIndex;
        }
        return moved;
    }

    private List<Inventory> linkedOutputInventories(UpgradedHopper hopper) {
        List<Inventory> targets = new ArrayList<>();
        for (String link : hopper.links) {
            Block block = resolve(link);
            if (block != null && block.getState() instanceof Container container) {
                targets.add(container.getInventory());
            }
        }
        return targets;
    }

    private boolean hasLinkedOutput(UpgradedHopper hopper) {
        return !linkedOutputInventories(hopper).isEmpty();
    }

    private int targetWithRoom(List<Inventory> targets, ItemStack item, int startIndex) {
        for (int attempt = 0; attempt < targets.size(); attempt++) {
            int index = Math.floorMod(startIndex + attempt, targets.size());
            if (availableCapacity(targets.get(index), item) > 0) return index;
        }
        return -1;
    }

    private int availableCapacity(Inventory inventory, ItemStack item) {
        if (inventory instanceof FurnaceInventory furnace) {
            int slot = item.getType().isFuel() ? 1 : 0;
            ItemStack current = furnace.getItem(slot);
            if (current == null || current.getType().isAir()) return item.getMaxStackSize();
            if (!current.isSimilar(item)) return 0;
            return Math.max(0, Math.min(current.getMaxStackSize(), inventory.getMaxStackSize()) - current.getAmount());
        }

        int capacity = 0;
        int stackLimit = Math.min(item.getMaxStackSize(), inventory.getMaxStackSize());
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) {
                capacity += stackLimit;
            } else if (current.isSimilar(item)) {
                capacity += Math.max(0, Math.min(current.getMaxStackSize(), inventory.getMaxStackSize())
                        - current.getAmount());
            }
            if (capacity >= item.getAmount()) return capacity;
        }
        return capacity;
    }

    private int pushToOutputs(Block hopperBlock, UpgradedHopper hopper, int budget) {
        if (budget <= 0) return 0;
        List<Inventory> targets = linkedOutputInventories(hopper);
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

            int targetIndex = targetWithRoom(targets, stored, hopper.nextOutput);
            if (targetIndex < 0) continue;
            int room = availableCapacity(targets.get(targetIndex), stored);
            int count = Math.min(Math.min(stored.getAmount(), budget - moved), room);
            if (count <= 0) {
                hopper.nextOutput = (targetIndex + 1) % targets.size();
                continue;
            }
            ItemStack offered = stored.clone();
            offered.setAmount(count);
            InventoryMoveItemEvent move = new InventoryMoveItemEvent(hopperBlockInventory(hopperBlock),
                    offered, targets.get(targetIndex), true);
            linkedOutputMove = true;
            try {
                Bukkit.getPluginManager().callEvent(move);
            } finally {
                linkedOutputMove = false;
            }
            if (move.isCancelled() || move.getItem() == null || !move.getItem().isSimilar(stored)) {
                hopper.nextOutput = (targetIndex + 1) % targets.size();
                continue;
            }

            offered = move.getItem().clone();
            int transferCount = Math.min(count, offered.getAmount());
            offered.setAmount(transferCount);
            Map<Integer, ItemStack> leftovers = insertIntoContainer(targets.get(targetIndex), offered);
            int accepted = transferCount;
            for (ItemStack leftover : leftovers.values()) accepted -= leftover.getAmount();
            if (accepted <= 0) {
                hopper.nextOutput = (targetIndex + 1) % targets.size();
                continue;
            }

            stored.setAmount(stored.getAmount() - accepted);
            moved += accepted;
            hopper.nextOutput = targetIndex;
            if (stored.getAmount() <= 0) {
                hopper.storage[slot] = null;
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
        return Math.max(1, Math.min(4, getConfig().getInt("links.max-per-hopper", 4)));
    }

    private String linkedFaceName(String hopperKey, String targetKey) {
        Block hopper = resolve(hopperKey);
        Block target = resolve(targetKey);
        if (hopper == null || target == null) return "unknown";
        BlockFace face = faceTo(hopper, target);
        return face == null ? "unknown" : face.name();
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
