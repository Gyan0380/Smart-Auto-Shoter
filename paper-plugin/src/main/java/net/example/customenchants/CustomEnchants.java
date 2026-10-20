package net.example.customenchants;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CustomEnchants extends JavaPlugin implements Listener {
    private static final String SHOP_TITLE = "Custom Enchant Shop";
    private static final int APPLY_COST = 2;
    private NamespacedKey bookKey;
    private NamespacedKey toolKey;
    private final Set<UUID> processing = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        bookKey = new NamespacedKey(this, "book_enchant");
        toolKey = new NamespacedKey(this, "tool_enchant");
        getServer().getPluginManager().registerEvents(this, this);
    }

    private enum EnchantType {
        LUMBERJACK("lumberjack", "Lumberjack", Material.IRON_AXE, 10),
        VEINMINER("veinminer", "Vein Miner", Material.IRON_PICKAXE, 20),
        MINER3("3x3miner", "3x3 Miner", Material.DIAMOND_PICKAXE, 30);
        final String id, label; final Material icon; final int defaultPrice;
        EnchantType(String id, String label, Material icon, int defaultPrice) {
            this.id=id; this.label=label; this.icon=icon; this.defaultPrice=defaultPrice;
        }
        static EnchantType parse(String value) {
            String v=value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            for (EnchantType t: values()) if (t.id.replace("-", "").equals(v) || t.label.toLowerCase(Locale.ROOT).replace(" ", "").equals(v)) return t;
            return null;
        }
    }

    private int price(EnchantType type) { return Math.max(0, getConfig().getInt("prices."+type.id, type.defaultPrice)); }
    private ItemStack book(EnchantType type) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d"+type.label+" Book");
        meta.setLore(List.of("§7Place this book on a compatible tool", "§7in your inventory or an anvil.", "§7Application cost: §e"+APPLY_COST+" XP levels"));
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, type.id);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        item.setItemMeta(meta);
        return item;
    }
    private EnchantType bookType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.STRING);
        return id == null ? null : EnchantType.parse(id);
    }
    private EnchantType toolType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return id == null ? null : EnchantType.parse(id);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("customenchants.shop")) { p.sendMessage("§cYou don't have permission."); return true; }
            openShop(p); return true;
        }
        if (args[0].equalsIgnoreCase("apply") && args.length == 2 && sender instanceof Player p) {
            EnchantType type=EnchantType.parse(args[1]);
            if (type == null) { p.sendMessage("§cChoose lumberjack, veinminer, or 3x3miner."); return true; }
            applyBook(p, type); return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length == 3) {
            if (!sender.hasPermission("customenchants.admin")) { sender.sendMessage("§cYou don't have permission."); return true; }
            Player target=getServer().getPlayerExact(args[1]); EnchantType type=EnchantType.parse(args[2]);
            if (target == null || type == null) { sender.sendMessage("§cUsage: /enchantshop give <online-player> <lumberjack|veinminer|3x3miner>"); return true; }
            target.getInventory().addItem(book(type)); target.sendMessage("§aYou received a "+type.label+" book.");
            sender.sendMessage("§aGave "+type.label+" to "+target.getName()+"."); return true;
        }
        sender.sendMessage("§e/enchantshop §7- open shop");
        sender.sendMessage("§e/enchantshop apply <lumberjack|veinminer|3x3miner> §7- apply book to held tool");
        sender.sendMessage("§e/enchantshop give <player> <enchant> §7- admin"); return true;
    }

    private void openShop(Player p) {
        Inventory inv=getServer().createInventory(null, 27, SHOP_TITLE);
        int[] slots={11,13,15};
        for (int i=0;i<3;i++) {
            EnchantType t=EnchantType.values()[i]; ItemStack item=book(t); ItemMeta meta=item.getItemMeta();
            meta.setLore(List.of("§7Price: §e"+price(t)+" XP levels", "§aClick to purchase")); item.setItemMeta(meta); inv.setItem(slots[i], item);
        }
        p.openInventory(inv);
    }

    @EventHandler public void onShopClick(InventoryClickEvent e) {
        if (!SHOP_TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        int slot=e.getRawSlot(); EnchantType type=slot==11?EnchantType.LUMBERJACK:slot==13?EnchantType.VEINMINER:slot==15?EnchantType.MINER3:null;
        if (type==null) return;
        int cost=price(type);
        if (p.getLevel()<cost) { p.sendMessage("§cYou need "+cost+" XP levels."); return; }
        p.setLevel(p.getLevel()-cost);
        var overflow=p.getInventory().addItem(book(type));
        overflow.values().forEach(item->p.getWorld().dropItemNaturally(p.getLocation(), item));
        p.sendMessage("§aPurchased "+type.label+" for "+cost+" XP levels.");
    }

    @EventHandler public void onBookApplyClick(InventoryClickEvent e) {
        if (SHOP_TITLE.equals(e.getView().getTitle()) || !(e.getWhoClicked() instanceof Player p)) return;
        EnchantType type=bookType(e.getCursor());
        ItemStack tool=e.getCurrentItem();
        if (type==null || tool==null || tool.getType().isAir()) return;
        e.setCancelled(true);
        if (!canApply(p,tool,type)) return;
        applyToTool(tool,type);
        p.setLevel(p.getLevel()-APPLY_COST);
        ItemStack cursor=e.getCursor(); cursor.setAmount(cursor.getAmount()-1);
        e.setCursor(cursor.getAmount()<=0?null:cursor);
        p.sendMessage("§aApplied "+type.label+" for "+APPLY_COST+" XP levels.");
    }

    @EventHandler public void onPrepareAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv=e.getInventory();
        EnchantType type=bookType(inv.getItem(1));
        ItemStack tool=inv.getItem(0);
        if (type==null || !isCompatibleTool(tool,type) || toolType(tool)!=null) return;
        ItemStack result=tool.clone();
        applyToTool(result,type);
        e.setResult(result);
        inv.setRepairCost(APPLY_COST);
    }

    @EventHandler public void onAnvilResult(InventoryClickEvent e) {
        if (e.getRawSlot()!=2 || !(e.getView().getTopInventory() instanceof AnvilInventory anvil)
                || !(e.getWhoClicked() instanceof Player p)) return;
        EnchantType type=bookType(anvil.getItem(1));
        ItemStack tool=anvil.getItem(0);
        if (type==null || !isCompatibleTool(tool,type) || toolType(tool)!=null) return;
        e.setCancelled(true);
        if (!p.hasPermission("customenchants.use")) { p.sendMessage("§cYou don't have permission."); return; }
        if (p.getLevel()<APPLY_COST) { p.sendMessage("§cYou need "+APPLY_COST+" XP levels to apply this book."); return; }
        ItemStack result=tool.clone(); applyToTool(result,type);
        p.setLevel(p.getLevel()-APPLY_COST);
        anvil.setItem(0,null); anvil.setItem(1,null); anvil.setRepairCost(0);
        e.setCursor(result);
        p.sendMessage("§aApplied "+type.label+" for "+APPLY_COST+" XP levels.");
    }

    private void applyBook(Player p, EnchantType type) {
        if (!p.hasPermission("customenchants.use")) { p.sendMessage("§cYou don't have permission."); return; }
        ItemStack tool=p.getInventory().getItemInMainHand();
        ItemStack found=null; int slot=-1;
        for (int i=0;i<p.getInventory().getSize();i++) if (type==bookType(p.getInventory().getItem(i))) {found=p.getInventory().getItem(i);slot=i;break;}
        if (found==null) { p.sendMessage("§cYou need that enchantment book in your inventory."); return; }
        if (!canApply(p,tool,type)) return;
        applyToTool(tool,type);
        p.setLevel(p.getLevel()-APPLY_COST);
        found.setAmount(found.getAmount()-1); if (found.getAmount()<=0) p.getInventory().setItem(slot,null);
        p.sendMessage("§aApplied "+type.label+" for "+APPLY_COST+" XP levels.");
    }
    private boolean canApply(Player p, ItemStack tool, EnchantType type) {
        if (!p.hasPermission("customenchants.use")) { p.sendMessage("§cYou don't have permission."); return false; }
        if (!isCompatibleTool(tool,type)) { p.sendMessage("§cUse an axe for Lumberjack or a pickaxe for a mining enchantment."); return false; }
        if (toolType(tool)!=null) { p.sendMessage("§cThis tool already has one of these custom enchantments."); return false; }
        if (p.getLevel()<APPLY_COST) { p.sendMessage("§cYou need "+APPLY_COST+" XP levels to apply this book."); return false; }
        return true;
    }
    private boolean isCompatibleTool(ItemStack tool, EnchantType type) {
        return tool!=null && (type==EnchantType.LUMBERJACK ? isAxe(tool) : isPickaxe(tool));
    }
    private void applyToTool(ItemStack tool, EnchantType type) {
        ItemMeta meta=tool.getItemMeta(); meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, type.id);
        meta.setLore(appendLore(meta.getLore(), "§d"+type.label)); tool.setItemMeta(meta);
    }
    private List<String> appendLore(List<String> old, String line) { List<String> lore=old==null?new ArrayList<>():new ArrayList<>(old); lore.add(line); return lore; }

    @EventHandler(ignoreCancelled=true) public void onBreak(BlockBreakEvent event) {
        Player p=event.getPlayer(); if (p.isSneaking() || !p.hasPermission("customenchants.use") || processing.contains(p.getUniqueId())) return;
        EnchantType type=toolType(p.getInventory().getItemInMainHand());
        if (type==null) return;
        Block origin=event.getBlock();
        if (type==EnchantType.LUMBERJACK && isAxe(p.getInventory().getItemInMainHand()) && isLog(origin.getType())) {
            processing.add(p.getUniqueId()); try { lumberjack(event, p, origin); } finally { processing.remove(p.getUniqueId()); }
        } else if (type==EnchantType.VEINMINER && isPickaxe(p.getInventory().getItemInMainHand()) && isOre(origin.getType())) {
            processing.add(p.getUniqueId()); try { connected(event,p,origin,false); } finally { processing.remove(p.getUniqueId()); }
        } else if (type==EnchantType.MINER3 && isPickaxe(p.getInventory().getItemInMainHand())) {
            processing.add(p.getUniqueId()); try { mine3x3(event,p,origin); } finally { processing.remove(p.getUniqueId()); }
        }
    }

    private void lumberjack(BlockBreakEvent root, Player p, Block origin) {
        Material logType=origin.getType();
        List<Block> logs=collect(origin,b->b.getType()==logType,getConfig().getInt("limits.lumberjack",128));
        List<Block> leaves=new ArrayList<>(); Set<Block> seen=new HashSet<>(logs);
        int radius=Math.max(1,Math.min(8,getConfig().getInt("lumberjack.leaf-radius",6)));
        for(Block log:logs) for(int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++) {
            Block b=log.getRelative(x,y,z); if(isLeaves(b.getType())&&seen.add(b)) leaves.add(b);
        }
        for(Block b:logs) if(b!=origin&&!breakAndDrop(root,p,b,false)) return;
        for(Block b:leaves) if(!breakAndDrop(root,p,b,true)) return;
    }
    private void connected(BlockBreakEvent root, Player p, Block origin, boolean ignored) {
        Material material=origin.getType();
        for(Block b:collect(origin,x->x.getType()==material,getConfig().getInt("limits.veinminer",64)))
            if(b!=origin&&!breakAndDrop(root,p,b,false)) return;
    }
    private interface BlockTest { boolean test(Block b); }
    private List<Block> collect(Block origin, BlockTest test, int cap) {
        List<Block> out=new ArrayList<>(); Set<Block> seen=new HashSet<>(); ArrayDeque<Block> queue=new ArrayDeque<>();
        queue.add(origin); seen.add(origin); cap=Math.max(1,Math.min(512,cap));
        while(!queue.isEmpty()&&out.size()<cap) { Block b=queue.removeFirst(); if(!test.test(b)) continue; out.add(b);
            for(BlockFace f:BlockFace.values()) if(f!=BlockFace.SELF) { Block n=b.getRelative(f); if(seen.add(n)&&test.test(n)) queue.addLast(n); }
        } return out;
    }
    private void mine3x3(BlockBreakEvent root, Player p, Block origin) {
        BlockFace facing=p.getFacing(); List<Block> plane=new ArrayList<>();
        if(facing==BlockFace.UP||facing==BlockFace.DOWN) { for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)plane.add(origin.getRelative(x,0,z)); }
        else { BlockFace side=(facing==BlockFace.NORTH||facing==BlockFace.SOUTH)?BlockFace.EAST:BlockFace.NORTH;
            for(int a=-1;a<=1;a++)for(int y=-1;y<=1;y++)if(a!=0||y!=0)plane.add(origin.getRelative(side,a).getRelative(0,y,0)); }
        for(Block b:plane) if(!b.getType().isAir()&&!breakAndDrop(root,p,b,false)) return;
    }
    private boolean breakAndDrop(BlockBreakEvent root, Player p, Block block, boolean leaves) {
        if(block.getType().isAir()) return true;
        ItemStack tool=p.getInventory().getItemInMainHand();
        if(tool.getType().isAir()) return false;
        BlockBreakEvent extra=new BlockBreakEvent(block,p); getServer().getPluginManager().callEvent(extra); if(extra.isCancelled())return true;
        Collection<ItemStack> drops=leaves?block.getDrops():block.getDrops(tool,p);
        block.setType(Material.AIR,false);
        for(ItemStack drop:drops) if(leaves) { var overflow=p.getInventory().addItem(drop); overflow.values().forEach(i->p.getWorld().dropItemNaturally(block.getLocation(),i)); }
        else p.getWorld().dropItemNaturally(block.getLocation(),drop);
        return leaves || damageTool(p);
    }
    private boolean damageTool(Player p) {
        ItemStack tool=p.getInventory().getItemInMainHand();
        if(tool.getType().isAir() || tool.getType().getMaxDurability()<=0) return false;
        ItemMeta meta=tool.getItemMeta();
        if(!(meta instanceof Damageable damageable)) return false;
        int unbreaking=meta.getEnchantLevel(Enchantment.UNBREAKING);
        if(unbreaking>0 && ThreadLocalRandom.current().nextInt(unbreaking+1)!=0) return true;
        int newDamage=damageable.getDamage()+1;
        if(newDamage>=tool.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(null);
            return false;
        }
        damageable.setDamage(newDamage); tool.setItemMeta(meta);
        return true;
    }
    private boolean isAxe(ItemStack i) { return i!=null&&i.getType().name().endsWith("_AXE"); }
    private boolean isPickaxe(ItemStack i) { return i!=null&&i.getType().name().endsWith("_PICKAXE"); }
    private boolean isLog(Material m) { return m.name().endsWith("_LOG")||m.name().endsWith("_WOOD")||m.name().endsWith("_STEM")||m.name().endsWith("_HYPHAE"); }
    private boolean isLeaves(Material m) { return m.name().endsWith("_LEAVES"); }
    private boolean isOre(Material m) { String n=m.name(); return n.endsWith("_ORE")||n.equals("ANCIENT_DEBRIS")||n.equals("NETHER_GOLD_ORE"); }
}
