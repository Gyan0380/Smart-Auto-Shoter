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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
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
    private static final String SHOP_TITLE = "Custom Enchant Shop";
    private static final String ADMIN_TITLE = "Enchant Admin Settings";
    private static final int APPLY_COST = 2;
    private NamespacedKey bookKey;
    private NamespacedKey toolKey;
    private final Set<UUID> processing = new HashSet<>();
    private final Map<UUID,TpaRequest> incomingRequests = new HashMap<>();
    private final Map<UUID,TpaRequest> outgoingRequests = new HashMap<>();
    private final Map<UUID,BukkitTask> warmups = new HashMap<>();
    private final Map<UUID,Long> tpaCooldowns = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        bookKey = new NamespacedKey(this, "book_enchant");
        toolKey = new NamespacedKey(this, "tool_enchant");
        getServer().getPluginManager().registerEvents(this, this);
        registerCommand("customenchants-tpa", List.of("tpa"), tpaCommand("tpa"));
        registerCommand("customenchants-tpahere", List.of("tpahere", "tpahear"), tpaCommand("tpahere"));
        registerCommand("customenchants-tpaccept", List.of("tpaccept"), tpaCommand("tpaccept"));
        registerCommand("customenchants-tpadeny", List.of("tpadeny"), tpaCommand("tpadeny"));
        registerCommand("customenchants-tpacancel", List.of("tpacancel", "tpacancle"), tpaCommand("tpacancel"));
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
        if (command.getName().equalsIgnoreCase("enchantadmin")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("customenchants.admin")) { p.sendMessage("§cYou don't have permission."); return true; }
            openAdmin(p); return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("customenchants.shop")) { p.sendMessage("§cYou don't have permission."); return true; }
            if (!getConfig().getBoolean("shop.enabled", true)) { p.sendMessage("§cThe enchant shop is currently disabled."); return true; }
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
            EnchantType t=EnchantType.values()[i];
            if (!getConfig().getBoolean("shop.items."+t.id+".enabled", true)) continue;
            ItemStack item=book(t); ItemMeta meta=item.getItemMeta();
            meta.setLore(List.of("§7Price: §e"+price(t)+" XP levels", "§aClick to purchase")); item.setItemMeta(meta); inv.setItem(slots[i], item);
        }
        p.openInventory(inv);
    }

    @EventHandler public void onShopClick(InventoryClickEvent e) {
        if (!SHOP_TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        if (!getConfig().getBoolean("shop.enabled", true)) { p.sendMessage("§cThe enchant shop is currently disabled."); return; }
        int slot=e.getRawSlot(); EnchantType type=slot==11?EnchantType.LUMBERJACK:slot==13?EnchantType.VEINMINER:slot==15?EnchantType.MINER3:null;
        if (type==null || !getConfig().getBoolean("shop.items."+type.id+".enabled", true)) return;
        int cost=price(type);
        if (p.getLevel()<cost) { p.sendMessage("§cYou need "+cost+" XP levels."); return; }
        p.setLevel(p.getLevel()-cost);
        var overflow=p.getInventory().addItem(book(type));
        overflow.values().forEach(item->p.getWorld().dropItemNaturally(p.getLocation(), item));
        p.sendMessage("§aPurchased "+type.label+" for "+cost+" XP levels.");
    }

    private void openAdmin(Player p) {
        Inventory inv=getServer().createInventory(null,27,ADMIN_TITLE);
        EnchantType[] types=EnchantType.values(); int[][] rows={{10,11,12},{13,14,15},{16,17,18}};
        for(int i=0;i<types.length;i++) {
            EnchantType type=types[i]; int[] row=rows[i];
            inv.setItem(row[0],adminItem(Material.REDSTONE,"§c-1 XP level",List.of("§7Shift-click to change by 10")));
            inv.setItem(row[1],adminItem(type.icon,"§e"+type.label,List.of(
                    "§7Price: §e"+price(type)+" levels",
                    "§7In shop: "+(shopItemEnabled(type)?"§aYes":"§cNo"),
                    "§fClick to toggle shop listing")));
            inv.setItem(row[2],adminItem(Material.EMERALD,"§a+1 XP level",List.of("§7Shift-click to change by 10")));
        }
        inv.setItem(21,adminItem(Material.CHEST,"§eEnchant shop: "+(getConfig().getBoolean("shop.enabled",true)?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(23,adminItem(Material.ENDER_PEARL,"§eTPA commands: "+(getConfig().getBoolean("tpa.enabled",true)?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(26,adminItem(Material.BARRIER,"§cClose",List.of()));
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
        if(slot==21) getConfig().set("shop.enabled",!getConfig().getBoolean("shop.enabled",true));
        else if(slot==23) getConfig().set("tpa.enabled",!getConfig().getBoolean("tpa.enabled",true));
        else if(slot==26) { p.closeInventory(); return; }
        else {
            EnchantType type=null; int direction=0; boolean toggle=false;
            int[][] rows={{10,11,12},{13,14,15},{16,17,18}};
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

