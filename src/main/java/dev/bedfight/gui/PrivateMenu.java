package dev.bedfight.gui;

import dev.bedfight.BedFightPlugin;
import dev.bedfight.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GUI for /bedfight private: pick a map, then a mode (1v1 / 2v2 / 3v3). */
public class PrivateMenu implements Listener {

    private static final String MAP_TITLE = ChatColor.DARK_PURPLE + "BedFight: Select Map";
    private static final String MODE_TITLE = ChatColor.DARK_PURPLE + "BedFight: Select Mode";

    private final BedFightPlugin plugin;
    private final Map<UUID, List<Arena>> shown = new HashMap<UUID, List<Arena>>();
    private final Map<UUID, Arena> chosen = new HashMap<UUID, Arena>();

    public PrivateMenu(BedFightPlugin plugin) {
        this.plugin = plugin;
    }

    public void forget(Player p) {
        shown.remove(p.getUniqueId());
        chosen.remove(p.getUniqueId());
    }

    public void openMaps(Player p) {
        List<Arena> list = new ArrayList<Arena>();
        for (Arena a : plugin.getArenaManager().getArenas()) {
            if (a.validate() == null) list.add(a);
        }
        if (list.isEmpty()) {
            plugin.send(p, ChatColor.RED + "There are no arenas available yet.");
            return;
        }
        if (list.size() > 54) list = new ArrayList<Arena>(list.subList(0, 54));
        int size = ((list.size() + 8) / 9) * 9;
        Inventory inv = Bukkit.createInventory(null, size, MAP_TITLE);
        for (int i = 0; i < list.size(); i++) {
            Arena a = list.get(i);
            boolean busy = plugin.getGameManager().isBusy(a);
            inv.setItem(i, item(Material.BED, ChatColor.GREEN + a.getName(),
                    ChatColor.GRAY + "Teams: " + ChatColor.WHITE + a.getTeams().size(),
                    busy ? ChatColor.RED + "Currently in use" : ChatColor.YELLOW + "Click to select"));
        }
        shown.put(p.getUniqueId(), list);
        p.openInventory(inv);
    }

    private void openModes(Player p, Arena arena) {
        Inventory inv = Bukkit.createInventory(null, 9, MODE_TITLE);
        inv.setItem(0, item(Material.BARRIER, ChatColor.RED + "Back"));
        inv.setItem(2, item(Material.STONE_SWORD, ChatColor.GREEN + "1v1", ChatColor.GRAY + "Map: " + arena.getName()));
        inv.setItem(4, item(Material.IRON_SWORD, ChatColor.GREEN + "2v2", ChatColor.GRAY + "Random teams from your party"));
        inv.setItem(6, item(Material.DIAMOND_SWORD, ChatColor.GREEN + "3v3", ChatColor.GRAY + "Random teams from your party"));
        chosen.put(p.getUniqueId(), arena);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory inv = e.getInventory();
        String title = inv.getTitle();
        if (title == null) return;
        boolean mapMenu = title.equals(MAP_TITLE);
        boolean modeMenu = title.equals(MODE_TITLE);
        if (!mapMenu && !modeMenu) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission(BedFightPlugin.PERM_PRIVATE)) {
            p.closeInventory();
            plugin.send(p, ChatColor.RED + "Private matches require the MVP++ rank.");
            return;
        }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) return;

        if (mapMenu) {
            List<Arena> list = shown.get(p.getUniqueId());
            if (list == null || slot >= list.size()) return;
            openModes(p, list.get(slot));
            return;
        }

        int teamSize;
        switch (slot) {
            case 0: openMaps(p); return;
            case 2: teamSize = 1; break;
            case 4: teamSize = 2; break;
            case 6: teamSize = 3; break;
            default: return;
        }
        Arena arena = chosen.get(p.getUniqueId());
        if (arena == null) return;
        p.closeInventory();
        plugin.getGameManager().startPrivate(p, arena, teamSize);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore.length > 0) m.setLore(Arrays.asList(lore));
        it.setItemMeta(m);
        return it;
    }
}
