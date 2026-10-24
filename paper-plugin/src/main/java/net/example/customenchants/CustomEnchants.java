package net.example.customenchants;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CustomEnchants extends JavaPlugin implements Listener {
    private static final String SHOP_TITLE = "§8✦  Enchantment Shop  ✦";
    private static final String ADMIN_TITLE = "§8✦  Enchantment Control  ✦";
    private static final Set<Material> AREA_MINE_PROTECTED = Set.of(
            Material.BEDROCK, Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY,
            Material.END_PORTAL_FRAME, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK, Material.JIGSAW, Material.STRUCTURE_VOID
    );
    private NamespacedKey bookKey;
    private NamespacedKey toolKey;
    private NamespacedKey nightVisionKey;
    private final Set<UUID> processing = new HashSet<>();
    private final Map<UUID,TpaRequest> incomingRequests = new HashMap<>();
    private final Map<UUID,TpaRequest> outgoingRequests = new HashMap<>();
    private final Map<UUID,BukkitTask> warmups = new HashMap<>();
    private final Map<UUID,Long> tpaCooldowns = new HashMap<>();
    private final Map<UUID,Map<String,Double>> longArmOriginalRanges = new HashMap<>();
    private boolean longArmWarningLogged;

    @Override public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        bookKey = new NamespacedKey(this, "book_enchant");
        toolKey = new NamespacedKey(this, "tool_enchant");
        nightVisionKey = new NamespacedKey(this, "nightvision_enabled");
        getServer().getPluginManager().registerEvents(this, this);
        registerCommand("customenchants-tpa", List.of("tpa"), tpaCommand("tpa"));
        registerCommand("customenchants-tpahere", List.of("tpahere", "tpahear"), tpaCommand("tpahere"));
        registerCommand("customenchants-tpaccept", List.of("tpaccept"), tpaCommand("tpaccept"));
        registerCommand("customenchants-tpadeny", List.of("tpadeny"), tpaCommand("tpadeny"));
        registerCommand("customenchants-tpacancel", List.of("tpacancel", "tpacancle"), tpaCommand("tpacancel"));
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) updateLongArmRange(player);
        }, 1L, 10L);
    }

    @Override public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) restoreLongArmRange(player);
    }

    private enum EnchantType {
        LUMBERJACK("lumberjack", "Lumberjack", Material.IRON_AXE, 10),
        VEINMINER("veinminer", "Vein Miner", Material.IRON_PICKAXE, 20),
        MINER3("3x3miner", "3x3 Miner", Material.DIAMOND_PICKAXE, 30),
        LONG_ARM("longarm", "Long Arm", Material.IRON_PICKAXE, 15),
        LESS_DAMAGE("lessdamage", "Less Damage", Material.DIAMOND_CHESTPLATE, 40),
        HEALER("healer", "Healer", Material.EXPERIENCE_BOTTLE, 25);
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
        meta.setLore(List.of("§7Place this book on a compatible tool", "§7in your inventory or an anvil.", "§7Application cost rises per enchant already applied."));
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
    private Set<EnchantType> toolTypes(ItemStack item) {
        Set<EnchantType> types=new HashSet<>();
        if (item == null || !item.hasItemMeta()) return types;
        String ids=item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if(ids!=null) for(String id:ids.split(",")) { EnchantType type=EnchantType.parse(id); if(type!=null) types.add(type); }
        return types;
    }
    private int applyBaseCost() { return Math.max(0,Math.min(100,getConfig().getInt("application.base-cost",2))); }
    private int applyStepCost() { return Math.max(0,Math.min(100,getConfig().getInt("application.additional-cost",2))); }
    private int maxCustomEnchants() { return Math.max(1,Math.min(6,getConfig().getInt("application.max-per-tool",6))); }
    private int applyCost(ItemStack tool) { return applyBaseCost()+toolTypes(tool).size()*applyStepCost(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("enchantadmin")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("customenchants.admin")) { p.sendMessage("§cYou don't have permission."); return true; }
            openAdmin(p); return true;
        }
        if (command.getName().equalsIgnoreCase("nightvision")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!getConfig().getBoolean("nightvision.enabled", true)) {
                p.sendMessage("§cNight vision commands are currently disabled by an admin."); return true;
            }
            if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
                p.sendMessage("§eUsage: /nightvision <on|off>"); return true;
            }
            if (args[0].equalsIgnoreCase("on")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
                p.getPersistentDataContainer().set(nightVisionKey, PersistentDataType.BYTE, (byte) 1);
                p.sendMessage("§aNight vision enabled.");
            } else {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                p.getPersistentDataContainer().remove(nightVisionKey);
                p.sendMessage("§7Night vision disabled.");
            }
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("customenchants.shop")) { p.sendMessage("§cYou don't have permission."); return true; }
            if (!getConfig().getBoolean("shop.enabled", true)) { p.sendMessage("§cThe enchant shop is currently disabled."); return true; }
            openShop(p); return true;
        }
        if (args[0].equalsIgnoreCase("apply") && args.length == 2 && sender instanceof Player p) {
            EnchantType type=EnchantType.parse(args[1]);
            if (type == null) { p.sendMessage("§cChoose a valid enchantment: lumberjack, veinminer, 3x3miner, longarm, lessdamage, or healer."); return true; }
            applyBook(p, type); return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length == 3) {
            if (!sender.hasPermission("customenchants.admin")) { sender.sendMessage("§cYou don't have permission."); return true; }
            Player target=getServer().getPlayerExact(args[1]); EnchantType type=EnchantType.parse(args[2]);
            if (target == null || type == null) { sender.sendMessage("§cUsage: /enchantshop give <online-player> <lumberjack|veinminer|3x3miner|longarm|lessdamage|healer>"); return true; }
            target.getInventory().addItem(book(type)); target.sendMessage("§aYou received a "+type.label+" book.");
            sender.sendMessage("§aGave "+type.label+" to "+target.getName()+"."); return true;
        }
        sender.sendMessage("§e/enchantshop §7- open shop");
        sender.sendMessage("§e/enchantshop apply <enchant> §7- apply a book from your inventory to the held tool");
        sender.sendMessage("§e/enchantshop give <player> <enchant> §7- admin"); return true;
    }

    private void openShop(Player p) {
        Inventory inv=getServer().createInventory(null, 27, SHOP_TITLE);
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot : new int[]{10,11,12,14,15,16}) inv.setItem(slot, null);
        int[] slots={10,11,12,14,15,16};
        inv.setItem(4,adminItem(Material.ENCHANTED_BOOK,"§d§lCustom Enchantments",List.of("§7Choose a book to purchase.","§7Prices are paid in XP levels.")));
        int visible=0;
        for (EnchantType t:EnchantType.values()) {
            if (!getConfig().getBoolean("shop.items."+t.id+".enabled", true)) continue;
            ItemStack item=book(t); ItemMeta meta=item.getItemMeta();
            meta.setDisplayName("§d§l"+t.label+" Book");
            meta.setLore(List.of("§8Custom enchantment", "", "§7"+bookDescription(t), "", "§7Price   §e"+price(t)+" XP levels", "§aClick to purchase"));
            item.setItemMeta(meta); inv.setItem(slots[visible++], item);
        }
        inv.setItem(22,adminItem(Material.EXPERIENCE_BOTTLE,"§bXP Level Balance",List.of("§7Your balance: §e"+p.getLevel()+" levels")));
        p.openInventory(inv);
    }

    private void fill(Inventory inv, Material material, String name) {
        ItemStack filler=adminItem(material,name,List.of());
        for(int i=0;i<inv.getSize();i++) inv.setItem(i,filler);
    }
    private String bookDescription(EnchantType t) {
        return switch(t) {
            case LUMBERJACK -> "Chop connected logs.";
            case VEINMINER -> "Mine connected matching ores.";
            case MINER3 -> "Mine a 3x3 plane.";
            case LONG_ARM -> "Extends block interaction reach while held.";
            case LESS_DAMAGE -> "Chance to reduce equipment durability loss.";
            case HEALER -> "Sneak + right-click to repair with XP points.";
        };
    }

    @EventHandler public void onShopClick(InventoryClickEvent e) {
        if (!SHOP_TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        if (!getConfig().getBoolean("shop.enabled", true)) { p.sendMessage("§cThe enchant shop is currently disabled."); return; }
        int slot=e.getRawSlot(); int[] slots={10,11,12,14,15,16}; EnchantType type=null; int visible=0;
        for(EnchantType candidate:EnchantType.values()) {
            if(!getConfig().getBoolean("shop.items."+candidate.id+".enabled",true)) continue;
            if(visible<slots.length && slot==slots[visible]) type=candidate;
            visible++;
        }
        if (type==null || !getConfig().getBoolean("shop.items."+type.id+".enabled", true)) return;
        int cost=price(type);
        if (p.getLevel()<cost) { p.sendMessage("§cYou need "+cost+" XP levels."); return; }
        p.setLevel(p.getLevel()-cost);
        var overflow=p.getInventory().addItem(book(type));
        overflow.values().forEach(item->p.getWorld().dropItemNaturally(p.getLocation(), item));
        p.sendMessage("§aPurchased "+type.label+" for "+cost+" XP levels.");
    }

    private void openAdmin(Player p) {
        Inventory inv=getServer().createInventory(null,54,ADMIN_TITLE);
        fill(inv,Material.GRAY_STAINED_GLASS_PANE," ");
        for(int slot=9;slot<=26;slot++) inv.setItem(slot,null);
        for(int slot=36;slot<=44;slot++) inv.setItem(slot,null);
        EnchantType[] types=EnchantType.values(); int[][] rows={{9,10,11},{12,13,14},{15,16,17},{18,19,20},{21,22,23},{24,25,26}};
        for(int i=0;i<types.length;i++) {
            EnchantType type=types[i]; int[] row=rows[i];
            inv.setItem(row[0],adminItem(Material.RED_STAINED_GLASS_PANE,"§c− Price",List.of("§7Click: −1 level", "§7Shift-click: −10 levels")));
            inv.setItem(row[1],adminItem(type.icon,"§e§l"+type.label,List.of(
                    "§7Shop price  §e"+price(type)+" levels",
                    "§7Visibility  "+(shopItemEnabled(type)?"§aShown":"§cHidden"), "",
                    "§fClick to toggle visibility")));
            inv.setItem(row[2],adminItem(Material.LIME_STAINED_GLASS_PANE,"§a+ Price",List.of("§7Click: +1 level", "§7Shift-click: +10 levels")));
        }
        inv.setItem(4,adminItem(Material.NETHER_STAR,"§d§lEnchantment Shop Controls",List.of("§7Set book prices and choose which books", "§7players can see in the shop.")));
        inv.setItem(36,adminItem(Material.RED_STAINED_GLASS_PANE,"§c− Base application cost",List.of("§7Click: −1 level", "§7Shift-click: −5 levels")));
        inv.setItem(37,adminItem(Material.EXPERIENCE_BOTTLE,"§eBase cost: "+applyBaseCost()+" levels",List.of("§7Application cost for the first book")));
        inv.setItem(38,adminItem(Material.LIME_STAINED_GLASS_PANE,"§a+ Base application cost",List.of("§7Click: +1 level", "§7Shift-click: +5 levels")));
        inv.setItem(39,adminItem(Material.RED_STAINED_GLASS_PANE,"§c− Added cost",List.of("§7Click: −1 level", "§7Shift-click: −5 levels")));
        inv.setItem(40,adminItem(Material.EXPERIENCE_BOTTLE,"§eAdded cost: "+applyStepCost()+" levels",List.of("§7Per enchant already on the item")));
        inv.setItem(41,adminItem(Material.LIME_STAINED_GLASS_PANE,"§a+ Added cost",List.of("§7Click: +1 level", "§7Shift-click: +5 levels")));
        inv.setItem(42,adminItem(Material.RED_STAINED_GLASS_PANE,"§c− Enchant limit",List.of("§7Click: −1", "§7Shift-click: −2")));
        inv.setItem(43,adminItem(Material.BOOK,"§eMax per item: "+maxCustomEnchants(),List.of("§7Choose the custom enchant limit")));
        inv.setItem(44,adminItem(Material.LIME_STAINED_GLASS_PANE,"§a+ Enchant limit",List.of("§7Click: +1", "§7Shift-click: +2")));
        inv.setItem(47,adminItem(Material.CHEST,"§eShop: "+(getConfig().getBoolean("shop.enabled",true)?"§aON":"§cOFF"),List.of("§7Click to enable or disable the shop")));
        inv.setItem(49,adminItem(Material.ENDER_PEARL,"§eTPA: "+(getConfig().getBoolean("tpa.enabled",true)?"§aON":"§cOFF"),List.of("§7Click to enable or disable this plugin's TPA")));
        inv.setItem(45,adminItem(Material.GOLDEN_CARROT,"§eNight Vision: "+(getConfig().getBoolean("nightvision.enabled",true)?"§aON":"§cOFF"),List.of("§7Click to allow or block /nightvision for all players")));
        inv.setItem(51,adminItem(Material.EXPERIENCE_BOTTLE,"§bHealer",List.of("§7One book works on supported tools", "§7and armor. Sneak + right-click to repair.", "§7Rate: "+getConfig().getDouble("healer.xp-per-durability",1.5)+" XP points per durability")));
        inv.setItem(53,adminItem(Material.BARRIER,"§cClose",List.of("§7Close this menu")));
        p.openInventory(inv);
    }
    private ItemStack adminItem(Material material,String name,List<String> lore) {
        ItemStack item=new ItemStack(material); ItemMeta meta=item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }
    private boolean shopItemEnabled(EnchantType type) { return getConfig().getBoolean("shop.items."+type.id+".enabled",true); }

    @EventHandler public void onAdminClick(InventoryClickEvent e) {
        if(!ADMIN_TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("customenchants.admin")) return;
        int slot=e.getRawSlot();
        if(slot<0 || slot>=e.getView().getTopInventory().getSize()) return;
        if(slot==36 || slot==38) changeSetting("application.base-cost",applyBaseCost(),slot==36?-1:1,e.isShiftClick()?5:1,0,100);
        else if(slot==39 || slot==41) changeSetting("application.additional-cost",applyStepCost(),slot==39?-1:1,e.isShiftClick()?5:1,0,100);
        else if(slot==42 || slot==44) changeSetting("application.max-per-tool",maxCustomEnchants(),slot==42?-1:1,e.isShiftClick()?2:1,1,6);
        else if(slot==45) {
            boolean enabled=!getConfig().getBoolean("nightvision.enabled",true);
            getConfig().set("nightvision.enabled",enabled);
            if(!enabled) for(Player online:Bukkit.getOnlinePlayers()) clearPluginNightVision(online);
        }
        else if(slot==47) getConfig().set("shop.enabled",!getConfig().getBoolean("shop.enabled",true));
        else if(slot==49) getConfig().set("tpa.enabled",!getConfig().getBoolean("tpa.enabled",true));
        else if(slot==53) { p.closeInventory(); return; }
        else if(slot==51) { p.sendMessage("§eHealer repair rate is configured in plugins/CustomEnchants/config.yml (healer.xp-per-durability)."); return; }
        else {
            EnchantType type=null; int direction=0; boolean toggle=false;
            int[][] rows={{9,10,11},{12,13,14},{15,16,17},{18,19,20},{21,22,23},{24,25,26}};
            for(int i=0;i<rows.length;i++) {
                if(slot==rows[i][0]) { type=EnchantType.values()[i]; direction=-1; }
                else if(slot==rows[i][1]) { type=EnchantType.values()[i]; toggle=true; }
                else if(slot==rows[i][2]) { type=EnchantType.values()[i]; direction=1; }
            }
            if(type==null) return;
            if(toggle) getConfig().set("shop.items."+type.id+".enabled",!shopItemEnabled(type));
            else {
                int change=e.isShiftClick()?10:1;
                getConfig().set("prices."+type.id,Math.max(0,Math.min(1000,price(type)+direction*change)));
            }
        }
        saveConfig(); openAdmin(p);
    }
    private void changeSetting(String path,int current,int direction,int amount,int min,int max) {
        getConfig().set(path,Math.max(min,Math.min(max,current+direction*amount)));
    }

    @EventHandler public void onNightVisionJoin(PlayerJoinEvent event) {
        if(!getConfig().getBoolean("nightvision.enabled",true)) clearPluginNightVision(event.getPlayer());
    }
    private void clearPluginNightVision(Player player) {
        Byte enabled=player.getPersistentDataContainer().get(nightVisionKey,PersistentDataType.BYTE);
        if(enabled==null || enabled!=1) return;
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.getPersistentDataContainer().remove(nightVisionKey);
    }

    private BasicCommand tpaCommand(String action) {
        return new BasicCommand() {
            @Override public void execute(CommandSourceStack source,String[] args) { runTpaCommand(source.getSender(),action,args); }
            @Override public Collection<String> suggest(CommandSourceStack source,String[] args) {
                if(args.length<1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            }
            @Override public String permission() { return "customenchants.tpa"; }
        };
    }
    private void runTpaCommand(CommandSender sender,String action,String[] args) {
        if(!getConfig().getBoolean("tpa.enabled",true)) { sender.sendMessage("§cThis plugin's TPA commands are disabled."); return; }
        if(!sender.hasPermission("customenchants.tpa")) { sender.sendMessage("§cYou don't have permission."); return; }
        if(!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
        switch(action) {
            case "tpa", "tpahere" -> {
                if(args.length!=1) { player.sendMessage("§eUsage: /"+action+" <player>"); return; }
                Player target=Bukkit.getPlayerExact(args[0]);
                if(target==null) { player.sendMessage("§cThat player is not online."); return; }
                createTpaRequest(player,target,action.equals("tpahere"));
            }
            case "tpaccept" -> acceptTpa(player,args);
            case "tpadeny" -> denyTpa(player,args);
            case "tpacancel" -> cancelTpa(player);
        }
    }
    private void createTpaRequest(Player requester,Player target,boolean here) {
        if(requester.equals(target)) { requester.sendMessage("§cYou cannot request a teleport with yourself."); return; }
        long until=tpaCooldowns.getOrDefault(requester.getUniqueId(),0L);
        long remaining=until-System.currentTimeMillis();
        if(remaining>0) { requester.sendMessage("§cWait "+((remaining+999)/1000)+" seconds before making another TPA request."); return; }
        if(outgoingRequests.containsKey(requester.getUniqueId())) { requester.sendMessage("§cYou already have a pending TPA request. Use /tpacancel first."); return; }
        if(incomingRequests.containsKey(target.getUniqueId())) { requester.sendMessage("§cThat player already has a pending TPA request."); return; }
        TpaRequest request=new TpaRequest(requester.getUniqueId(),target.getUniqueId(),here);
        outgoingRequests.put(requester.getUniqueId(),request); incomingRequests.put(target.getUniqueId(),request);
        int timeout=Math.max(5,getConfig().getInt("tpa.request-timeout-seconds",60));
        request.expiry=Bukkit.getScheduler().runTaskLater(this,()->{
            if(outgoingRequests.get(request.requester)==request) {
                removeTpaRequest(request);
                Player from=Bukkit.getPlayer(request.requester),to=Bukkit.getPlayer(request.target);
                if(from!=null) from.sendMessage("§cYour TPA request expired.");
                if(to!=null) to.sendMessage("§cThe TPA request expired.");
            }
        },timeout*20L);
        String kind=here?"wants you to teleport to them":"wants to teleport to you";
        target.sendMessage("§e"+requester.getName()+" "+kind+". Use §a/tpaccept §eor §c/tpadeny§e. Expires in "+timeout+" seconds.");
        requester.sendMessage("§aTPA request sent to "+target.getName()+".");
    }
    private void acceptTpa(Player acceptor,String[] args) {
        TpaRequest request=incomingRequests.get(acceptor.getUniqueId());
        if(request==null) { acceptor.sendMessage("§cYou have no pending TPA request."); return; }
        if(args.length>1) { acceptor.sendMessage("§eUsage: /tpaccept [player]"); return; }
        Player requester=Bukkit.getPlayer(request.requester);
        if(requester==null || (args.length==1&&!requester.getName().equalsIgnoreCase(args[0]))) { acceptor.sendMessage("§cNo matching pending request was found."); return; }
        removeTpaRequest(request);
        Player teleporter=request.here?acceptor:requester;
        Player destination=request.here?requester:acceptor;
        startTpaWarmup(teleporter,destination,request.requester);
        requester.sendMessage("§aYour TPA request was accepted.");
        acceptor.sendMessage("§aRequest accepted. Teleporting in "+Math.max(0,getConfig().getInt("tpa.warmup-seconds",5))+" seconds.");
    }
    private void denyTpa(Player player,String[] args) {
        TpaRequest request=incomingRequests.get(player.getUniqueId());
        if(args.length>1) { player.sendMessage("§eUsage: /tpadeny [player]"); return; }
        if(request==null) { player.sendMessage("§cYou have no pending TPA request."); return; }
        Player requester=Bukkit.getPlayer(request.requester);
        if(requester==null || (args.length==1&&!requester.getName().equalsIgnoreCase(args[0]))) { player.sendMessage("§cNo matching pending request was found."); return; }
        removeTpaRequest(request); player.sendMessage("§eTPA request denied."); requester.sendMessage("§cYour TPA request was denied.");
    }
    private void cancelTpa(Player player) {
        boolean changed=false;
        TpaRequest sent=outgoingRequests.get(player.getUniqueId());
        if(sent!=null) { removeTpaRequest(sent); Player target=Bukkit.getPlayer(sent.target); if(target!=null) target.sendMessage("§cThe TPA request was cancelled."); changed=true; }
        TpaRequest received=incomingRequests.get(player.getUniqueId());
        if(received!=null) { removeTpaRequest(received); Player requester=Bukkit.getPlayer(received.requester); if(requester!=null) requester.sendMessage("§cThe TPA request was cancelled."); changed=true; }
        if(warmups.containsKey(player.getUniqueId())) { cancelWarmup(player.getUniqueId(),"§cTeleport cancelled."); changed=true; }
        if(!changed) player.sendMessage("§7You have no pending TPA request or teleport.");
    }
    private void removeTpaRequest(TpaRequest request) {
        outgoingRequests.remove(request.requester,request); incomingRequests.remove(request.target,request);
        if(request.expiry!=null) request.expiry.cancel();
    }
    private void startTpaWarmup(Player teleporter,Player destination,UUID requesterId) {
        cancelWarmup(teleporter.getUniqueId(),null);
        int seconds=Math.max(0,Math.min(120,getConfig().getInt("tpa.warmup-seconds",5)));
        Location start=teleporter.getLocation().clone();
        if(seconds==0) { completeTpa(teleporter,destination,requesterId); return; }
        BukkitTask task=new BukkitRunnable() {
            int left=seconds;
            @Override public void run() {
                if(!teleporter.isOnline() || !destination.isOnline()) { warmups.remove(teleporter.getUniqueId()); cancel(); return; }
                if(getConfig().getBoolean("tpa.cancel-on-move",true) && moved(start,teleporter.getLocation())) {
                    warmups.remove(teleporter.getUniqueId()); cancel(); teleporter.sendMessage("§cTeleport cancelled because you moved."); return;
                }
                if(left<=0) { warmups.remove(teleporter.getUniqueId()); completeTpa(teleporter,destination,requesterId); cancel(); return; }
                teleporter.sendMessage("§eTeleporting in "+left+" seconds. Don't move."); left--;
            }
        }.runTaskTimer(this,0L,20L);
        warmups.put(teleporter.getUniqueId(),task);
    }
    private boolean moved(Location a,Location b) {
        return !a.getWorld().equals(b.getWorld()) || a.getBlockX()!=b.getBlockX() || a.getBlockY()!=b.getBlockY() || a.getBlockZ()!=b.getBlockZ();
    }
    private void completeTpa(Player teleporter,Player destination,UUID requesterId) {
        if(!teleporter.isOnline() || !destination.isOnline()) return;
        boolean success=teleporter.teleport(destination.getLocation());
        if(!success) { teleporter.sendMessage("§cTeleport failed."); return; }
        int cooldown=Math.max(0,Math.min(3600,getConfig().getInt("tpa.cooldown-seconds",5)));
        if(cooldown>0) tpaCooldowns.put(requesterId,System.currentTimeMillis()+cooldown*1000L);
        teleporter.sendMessage("§aTeleported to "+destination.getName()+".");
        destination.sendMessage("§a"+teleporter.getName()+" teleported to you.");
        if(getConfig().getBoolean("tpa.play-sound",true)) {
            try { Sound sound=Sound.valueOf(getConfig().getString("tpa.sound","ENTITY_ENDERMAN_TELEPORT"));
                teleporter.playSound(teleporter.getLocation(),sound,1f,1f); destination.playSound(destination.getLocation(),sound,1f,1f);
            } catch(IllegalArgumentException ignored) { getLogger().warning("Invalid TPA sound in config.yml"); }
        }
        if(cooldown>0) teleporter.sendMessage("§7TPA cooldown: "+cooldown+" seconds.");
    }
    private void cancelWarmup(UUID playerId,String message) {
        BukkitTask task=warmups.remove(playerId); if(task!=null) task.cancel();
        Player player=Bukkit.getPlayer(playerId); if(message!=null&&player!=null) player.sendMessage(message);
    }
    @EventHandler public void onMoveDuringTpa(PlayerMoveEvent e) {
        if(!getConfig().getBoolean("tpa.cancel-on-move",true) || e.getTo()==null || !moved(e.getFrom(),e.getTo())) return;
        if(warmups.containsKey(e.getPlayer().getUniqueId())) cancelWarmup(e.getPlayer().getUniqueId(),"§cTeleport cancelled because you moved.");
    }
    @EventHandler public void onTpaQuit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId(); cancelWarmup(id,null);
        restoreLongArmRange(e.getPlayer());
        TpaRequest sent=outgoingRequests.get(id),received=incomingRequests.get(id);
        if(sent!=null) removeTpaRequest(sent); if(received!=null) removeTpaRequest(received);
    }

    private static final class TpaRequest {
        final UUID requester,target; final boolean here; BukkitTask expiry;
        TpaRequest(UUID requester,UUID target,boolean here) { this.requester=requester; this.target=target; this.here=here; }
    }

    @EventHandler public void onBookApplyClick(InventoryClickEvent e) {
        if (SHOP_TITLE.equals(e.getView().getTitle()) || !(e.getWhoClicked() instanceof Player p)) return;
        EnchantType type=bookType(e.getCursor());
        ItemStack tool=e.getCurrentItem();
        if (type==null || tool==null || tool.getType().isAir()) return;
        e.setCancelled(true);
        if (!canApply(p,tool,type)) return;
        int cost=applyCost(tool);
        applyToTool(tool,type);
        p.setLevel(p.getLevel()-cost);
        ItemStack cursor=e.getCursor(); cursor.setAmount(cursor.getAmount()-1);
        e.setCursor(cursor.getAmount()<=0?null:cursor);
        p.sendMessage("§aApplied "+type.label+" for "+cost+" XP levels.");
    }

    @EventHandler public void onPrepareAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv=e.getInventory();
        EnchantType type=bookType(inv.getItem(1));
        ItemStack tool=inv.getItem(0);
        if (type==null || !canApplyToTool(tool,type)) return;
        ItemStack result=tool.clone();
        applyToTool(result,type);
        e.setResult(result);
        inv.setRepairCost(applyCost(tool));
    }

    @EventHandler public void onAnvilResult(InventoryClickEvent e) {
        if (e.getRawSlot()!=2 || !(e.getView().getTopInventory() instanceof AnvilInventory anvil)
                || !(e.getWhoClicked() instanceof Player p)) return;
        EnchantType type=bookType(anvil.getItem(1));
        ItemStack tool=anvil.getItem(0);
        if (type==null || !canApplyToTool(tool,type)) return;
        e.setCancelled(true);
        if (!p.hasPermission("customenchants.use")) { p.sendMessage("§cYou don't have permission."); return; }
        int cost=applyCost(tool);
        if (p.getLevel()<cost) { p.sendMessage("§cYou need "+cost+" XP levels to apply this book."); return; }
        ItemStack result=tool.clone(); applyToTool(result,type);
        p.setLevel(p.getLevel()-cost);
        anvil.setItem(0,null); anvil.setItem(1,null); anvil.setRepairCost(0);
        e.setCursor(result);
        p.sendMessage("§aApplied "+type.label+" for "+cost+" XP levels.");
    }

    private void applyBook(Player p, EnchantType type) {
        if (!p.hasPermission("customenchants.use")) { p.sendMessage("§cYou don't have permission."); return; }
        ItemStack tool=p.getInventory().getItemInMainHand();
        ItemStack found=null; int slot=-1;
        for (int i=0;i<p.getInventory().getSize();i++) if (type==bookType(p.getInventory().getItem(i))) {found=p.getInventory().getItem(i);slot=i;break;}
        if (found==null) { p.sendMessage("§cYou need that enchantment book in your inventory."); return; }
        if (!canApply(p,tool,type)) return;
        int cost=applyCost(tool);
        applyToTool(tool,type);
        p.setLevel(p.getLevel()-cost);
        found.setAmount(found.getAmount()-1); if (found.getAmount()<=0) p.getInventory().setItem(slot,null);
        p.sendMessage("§aApplied "+type.label+" for "+cost+" XP levels.");
    }
    private boolean canApply(Player p, ItemStack tool, EnchantType type) {
        if (!p.hasPermission("customenchants.use")) { p.sendMessage("§cYou don't have permission."); return false; }
        if (!isCompatibleTool(tool,type)) { p.sendMessage("§cUse an axe for Lumberjack or a pickaxe for a mining enchantment."); return false; }
        if (!canApplyToTool(tool,type)) { p.sendMessage("§cThis tool has this enchant already or reached its custom-enchantment limit."); return false; }
        int cost=applyCost(tool);
        if (p.getLevel()<cost) { p.sendMessage("§cYou need "+cost+" XP levels to apply this book."); return false; }
        return true;
    }
    private boolean isCompatibleTool(ItemStack tool, EnchantType type) {
        if(tool==null || tool.getType().isAir()) return false;
        return switch(type) {
            case LUMBERJACK -> isAxe(tool)||isPickaxe(tool);
            case VEINMINER, MINER3 -> isPickaxe(tool);
            case LONG_ARM -> isMiningTool(tool);
            case LESS_DAMAGE, HEALER -> isToolOrArmor(tool);
        };
    }
    private boolean canApplyToTool(ItemStack tool,EnchantType type) {
        if(!isCompatibleTool(tool,type)) return false;
        Set<EnchantType> current=toolTypes(tool);
        return !current.contains(type) && current.size()<maxCustomEnchants();
    }
    private void applyToTool(ItemStack tool, EnchantType type) {
        ItemMeta meta=tool.getItemMeta();
        Set<EnchantType> types=toolTypes(tool); types.add(type);
        String ids=java.util.Arrays.stream(EnchantType.values()).filter(types::contains).map(t->t.id).collect(java.util.stream.Collectors.joining(","));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, ids);
        meta.setLore(appendLore(meta.getLore(), "§d"+type.label)); meta.setEnchantmentGlintOverride(true); tool.setItemMeta(meta);
    }
    private List<String> appendLore(List<String> old, String line) { List<String> lore=old==null?new ArrayList<>():new ArrayList<>(old); lore.add(line); return lore; }

    @EventHandler(ignoreCancelled=true) public void onBreak(BlockBreakEvent event) {
        Player p=event.getPlayer(); if (p.isSneaking() || !p.hasPermission("customenchants.use") || processing.contains(p.getUniqueId())) return;
        Set<EnchantType> types=toolTypes(p.getInventory().getItemInMainHand());
        if (types.isEmpty()) return;
        Block origin=event.getBlock();
        ItemStack tool=p.getInventory().getItemInMainHand();
        if(types.contains(EnchantType.LUMBERJACK) && (isAxe(tool)||isPickaxe(tool)) && isLog(origin.getType())
                || types.contains(EnchantType.VEINMINER) && isPickaxe(tool) && isOre(origin.getType())
                || types.contains(EnchantType.MINER3) && isPickaxe(tool)) {
            processing.add(p.getUniqueId());
            try {
                if(types.contains(EnchantType.LUMBERJACK) && isLog(origin.getType())) lumberjack(p,origin);
                if(types.contains(EnchantType.VEINMINER) && isOre(origin.getType())) connected(p,origin);
                if(types.contains(EnchantType.MINER3)) mine3x3(p,origin);
            } finally { processing.remove(p.getUniqueId()); }
        }
    }

    private void lumberjack(Player p, Block origin) {
        Material logType=origin.getType();
        List<Block> logs=collect(origin,b->b.getType()==logType,getConfig().getInt("limits.lumberjack",128));
        List<Block> leaves=new ArrayList<>(); Set<Block> seen=new HashSet<>(logs);
        Set<Block> treeSnow=new HashSet<>(); Set<Block> vines=new HashSet<>();
        int radius=Math.max(1,Math.min(8,getConfig().getInt("lumberjack.leaf-radius",6)));
        for(Block log:logs) for(int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++) {
            Block b=log.getRelative(x,y,z);
            if(isLeaves(b.getType())&&seen.add(b)) leaves.add(b);
        }
        List<Block> treeParts=new ArrayList<>(logs); treeParts.addAll(leaves);
        BlockFace[] vineFaces={BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.DOWN};
        for(Block part:treeParts) {
            Block snow=part.getRelative(BlockFace.UP);
            if(snow.getType()==Material.SNOW) treeSnow.add(snow);
            for(BlockFace face:vineFaces) {
                Block adjacent=part.getRelative(face);
                if(adjacent.getType()==Material.VINE) vines.add(adjacent);
            }
        }
        for(Block b:logs) if(b!=origin&&!breakAndDrop(p,b,false)) return;
        for(Block snow:treeSnow) if(!breakAndDrop(p,snow,true)) return;
        for(Block vine:vines) if(!breakAndDrop(p,vine,true)) return;
        for(Block b:leaves) if(!breakAndDrop(p,b,true)) return;
    }
    private void connected(Player p, Block origin) {
        Material material=origin.getType();
        for(Block b:collect(origin,x->x.getType()==material,getConfig().getInt("limits.veinminer",64)))
            if(b!=origin&&!breakAndDrop(p,b,false)) return;
    }
    private interface BlockTest { boolean test(Block b); }
    private List<Block> collect(Block origin, BlockTest test, int cap) {
        List<Block> out=new ArrayList<>(); Set<Block> seen=new HashSet<>(); ArrayDeque<Block> queue=new ArrayDeque<>();
        queue.add(origin); seen.add(origin); cap=Math.max(1,Math.min(512,cap));
        while(!queue.isEmpty()&&out.size()<cap) { Block b=queue.removeFirst(); if(!test.test(b)) continue; out.add(b);
            for(BlockFace f:BlockFace.values()) if(f!=BlockFace.SELF) { Block n=b.getRelative(f); if(seen.add(n)&&test.test(n)) queue.addLast(n); }
        } return out;
    }
    private void mine3x3(Player p, Block origin) {
        Vector direction=p.getEyeLocation().getDirection(); List<Block> plane=new ArrayList<>();
        double ax=Math.abs(direction.getX()), ay=Math.abs(direction.getY()), az=Math.abs(direction.getZ());
        // Aim mostly up/down to clear a horizontal plane; otherwise clear a wall perpendicular to aim.
        if(ay>=ax && ay>=az) {
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0||z!=0) plane.add(origin.getRelative(x,0,z));
        } else if(ax>=az) {
            for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) if(y!=0||z!=0) plane.add(origin.getRelative(0,y,z));
        } else {
            for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) if(x!=0||y!=0) plane.add(origin.getRelative(x,y,0));
        }
        for(Block b:plane) if(!b.getType().isAir()&&!breakAndDrop(p,b,false)) return;
    }
    private boolean breakAndDrop(Player p, Block block, boolean noDurability) {
        if(block.getType().isAir()) return true;
        Material material=block.getType();
        if(block.isLiquid() || AREA_MINE_PROTECTED.contains(material) || isActivePortalFrame(block)) return true;
        ItemStack tool=p.getInventory().getItemInMainHand();
        if(tool.getType().isAir()) return false;
        BlockBreakEvent extra=new BlockBreakEvent(block,p); getServer().getPluginManager().callEvent(extra); if(extra.isCancelled())return true;
        Collection<ItemStack> drops=extra.isDropItems()?(noDurability?block.getDrops():block.getDrops(tool,p)):List.of();
        // Apply world physics so gravity blocks fall and neighboring fluids update normally.
        block.setType(Material.AIR, true);
        for(ItemStack drop:drops) if(noDurability) { var overflow=p.getInventory().addItem(drop); overflow.values().forEach(i->p.getWorld().dropItemNaturally(block.getLocation(),i)); }
        else p.getWorld().dropItemNaturally(block.getLocation(),drop);
        return noDurability || damageTool(p);
    }
    private boolean isActivePortalFrame(Block block) {
        if(block.getType()!=Material.OBSIDIAN) return false;
        for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN})
            if(block.getRelative(face).getType()==Material.NETHER_PORTAL) return true;
        return false;
    }
    private boolean damageTool(Player p) {
        ItemStack tool=p.getInventory().getItemInMainHand();
        if(tool.getType().isAir() || tool.getType().getMaxDurability()<=0) return false;
        ItemMeta meta=tool.getItemMeta();
        if(!(meta instanceof Damageable damageable)) return false;
        int unbreaking=meta.getEnchantLevel(Enchantment.UNBREAKING);
        if(unbreaking>0 && ThreadLocalRandom.current().nextInt(unbreaking+1)!=0) return true;
        if(toolTypes(tool).contains(EnchantType.LESS_DAMAGE)) {
            double chance=Math.max(0,Math.min(1,getConfig().getDouble("less-damage.prevent-chance",0.45)));
            if(ThreadLocalRandom.current().nextDouble()<chance) {
                if(ThreadLocalRandom.current().nextDouble()<Math.max(0,Math.min(1,getConfig().getDouble("less-damage.repair-chance",0.05))))
                    damageable.setDamage(Math.max(0,damageable.getDamage()-1));
                tool.setItemMeta(meta); return true;
            }
        }
        int newDamage=damageable.getDamage()+1;
        if(newDamage>=tool.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(null);
            return false;
        }
        damageable.setDamage(newDamage); tool.setItemMeta(meta);
        return true;
    }
    @EventHandler(ignoreCancelled=true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        ItemStack item=e.getItem();
        if(!toolTypes(item).contains(EnchantType.LESS_DAMAGE)) return;
        double chance=Math.max(0,Math.min(1,getConfig().getDouble("less-damage.prevent-chance",0.45)));
        if(ThreadLocalRandom.current().nextDouble()<chance) {
            e.setDamage(0);
            if(ThreadLocalRandom.current().nextDouble()<Math.max(0,Math.min(1,getConfig().getDouble("less-damage.repair-chance",0.05)))
                    && item.getItemMeta() instanceof Damageable d && d.getDamage()>0) {
                d.setDamage(d.getDamage()-1);
                item.setItemMeta((ItemMeta)d);
            }
        } else {
            double reduction=Math.max(0,Math.min(0.9,getConfig().getDouble("less-damage.reduction",0.25)));
            e.setDamage(Math.max(0,(int)Math.floor(e.getDamage()*(1.0-reduction))));
        }
    }

    private void updateLongArmRange(Player player) {
        UUID id=player.getUniqueId();
        boolean active=toolTypes(player.getInventory().getItemInMainHand()).contains(EnchantType.LONG_ARM);
        if(active) {
            Map<String,Double> saved=longArmOriginalRanges.computeIfAbsent(id,k->new HashMap<>());
            setReachBase(player,"BLOCK_INTERACTION_RANGE",saved,Math.max(0.0,Math.min(5.0,getConfig().getDouble("long-arm.block-range-bonus",2.0))));
        } else {
            Map<String,Double> saved=longArmOriginalRanges.remove(id);
            if(saved!=null) for(var entry:saved.entrySet()) setReachBaseExact(player,entry.getKey(),entry.getValue());
        }
    }
    private void restoreLongArmRange(Player player) {
        Map<String,Double> saved=longArmOriginalRanges.remove(player.getUniqueId());
        if(saved!=null) for(var entry:saved.entrySet()) setReachBaseExact(player,entry.getKey(),entry.getValue());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private void setReachBase(Player player,String attributeName,Map<String,Double> saved,double bonus) {
        try {
            Class<?> attributeClass=Class.forName("org.bukkit.attribute.Attribute");
            Object attribute=Enum.valueOf((Class<? extends Enum>)attributeClass.asSubclass(Enum.class),attributeName);
            Object instance=player.getClass().getMethod("getAttribute",attributeClass).invoke(player,attribute);
            if(instance==null) return;
            Class<?> instanceClass=Class.forName("org.bukkit.attribute.AttributeInstance");
            double current=(double)instanceClass.getMethod("getBaseValue").invoke(instance);
            saved.putIfAbsent(attributeName,current);
            instanceClass.getMethod("setBaseValue",double.class).invoke(instance,saved.get(attributeName)+bonus);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            // Older Paper builds may not expose interaction-range attributes.
            if (!longArmWarningLogged) {
                getLogger().warning("Long Arm is unavailable because this server does not expose BLOCK_INTERACTION_RANGE.");
                longArmWarningLogged = true;
            }
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private void setReachBaseExact(Player player,String attributeName,double value) {
        try {
            Class<?> attributeClass=Class.forName("org.bukkit.attribute.Attribute");
            Object attribute=Enum.valueOf((Class<? extends Enum>)attributeClass.asSubclass(Enum.class),attributeName);
            Object instance=player.getClass().getMethod("getAttribute",attributeClass).invoke(player,attribute);
            if(instance==null) return;
            Class<?> instanceClass=Class.forName("org.bukkit.attribute.AttributeInstance");
            instanceClass.getMethod("setBaseValue",double.class).invoke(instance,value);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            if (!longArmWarningLogged) {
                getLogger().warning("Could not restore the block interaction range while disabling Long Arm.");
                longArmWarningLogged = true;
            }
        }
    }

    @EventHandler
    public void onHealerUse(PlayerInteractEvent e) {
        if(e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        Player p=e.getPlayer();
        if(!p.isSneaking() || !p.hasPermission("customenchants.use")) return;
        ItemStack item=e.getItem();
        if(item==null || !toolTypes(item).contains(EnchantType.HEALER) || !(item.getItemMeta() instanceof Damageable d)) return;
        int damage=d.getDamage();
        if(damage<=0) { p.sendMessage("§aThis item is already fully repaired."); return; }
        double rate=Math.max(0.1,getConfig().getDouble("healer.xp-per-durability",1.5));
        int available=totalXpPoints(p);
        int repair=Math.min(damage,(int)Math.floor(available/rate));
        if(repair<=0) { p.sendMessage("§cYou need at least "+(int)Math.ceil(rate)+" XP points to repair this item."); return; }
        int cost=(int)Math.ceil(repair*rate);
        setTotalXpPoints(p,available-cost);
        d.setDamage(damage-repair); item.setItemMeta((ItemMeta)d);
        e.setCancelled(true);
        p.sendMessage("§aHealer repaired "+repair+" durability for "+cost+" XP points.");
    }
    private int totalXpPoints(Player p) {
        int level=p.getLevel();
        int base=level<=16?level*level+6*level:level<=31?(int)Math.floor(2.5*level*level-40.5*level+360):(int)Math.floor(4.5*level*level-162.5*level+2220);
        return Math.max(0,base+(int)Math.floor(p.getExp()*p.getExpToLevel()));
    }
    private void setTotalXpPoints(Player p,int points) {
        int remaining=Math.max(0,points), level=0;
        while(level<21863 && xpAtLevel(level+1)<=remaining) level++;
        int base=xpAtLevel(level), needed=xpToNext(level);
        p.setTotalExperience(remaining); p.setLevel(level);
        p.setExp(needed<=0?0f:Math.max(0f,Math.min(0.999f,(remaining-base)/(float)needed)));
    }
    private int xpAtLevel(int l) {
        return l<=16?l*l+6*l:l<=31?(int)Math.floor(2.5*l*l-40.5*l+360):(int)Math.floor(4.5*l*l-162.5*l+2220);
    }
    private int xpToNext(int l) { return l>=30?67+2*(l-30):l>=15?37+2*(l-15):7+2*l; }

    private boolean isAxe(ItemStack i) { return i!=null&&i.getType().name().endsWith("_AXE"); }
    private boolean isPickaxe(ItemStack i) { return i!=null&&i.getType().name().endsWith("_PICKAXE"); }
    private boolean isMiningTool(ItemStack i) {
        if(i==null) return false;
        String n=i.getType().name();
        return n.endsWith("_AXE")||n.endsWith("_PICKAXE")||n.endsWith("_SHOVEL")||n.endsWith("_HOE")||n.equals("SHEARS");
    }
    private boolean isToolOrArmor(ItemStack i) {
        if(i==null || i.getType().getMaxDurability()<=0) return false;
        String n=i.getType().name();
        return isMiningTool(i)||n.endsWith("_HELMET")||n.endsWith("_CHESTPLATE")||n.endsWith("_LEGGINGS")||n.endsWith("_BOOTS");
    }
    private boolean isLog(Material m) { return m.name().endsWith("_LOG")||m.name().endsWith("_WOOD")||m.name().endsWith("_STEM")||m.name().endsWith("_HYPHAE"); }
    private boolean isLeaves(Material m) { return m.name().endsWith("_LEAVES"); }
    private boolean isOre(Material m) { String n=m.name(); return n.endsWith("_ORE")||n.equals("ANCIENT_DEBRIS")||n.equals("NETHER_GOLD_ORE"); }
}

