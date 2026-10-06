package dev.bedfight.game;

import dev.bedfight.BedFightPlugin;
import dev.bedfight.arena.Arena;
import dev.bedfight.arena.ArenaManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Iterator;

/** Map protection, Y-level limits and combat/death handling. */
public class GameListener implements Listener {

    private final BedFightPlugin plugin;
    private final ArenaManager arenas;
    private final GameManager games;

    public GameListener(BedFightPlugin plugin) {
        this.plugin = plugin;
        this.arenas = plugin.getArenaManager();
        this.games = plugin.getGameManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        Arena arena = arenas.getByWorld(b.getWorld());
        if (arena == null || arenas.isBuildMode(p)) return;

        Match m = games.getMatch(p);
        if (m == null || !m.isRunning() || !m.isAlive(p)) {
            e.setCancelled(true);
            return;
        }
        if (!arena.isInside(b.getLocation())) {
            e.setCancelled(true);
            plugin.send(p, ChatColor.RED + "You can't build outside the map or above the build limit (Y " + arena.getMaxY() + ").");
            return;
        }
        m.addPlaced(b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Arena arena = arenas.getByWorld(b.getWorld());
        if (arena == null || arenas.isBuildMode(p)) return;

        Match m = games.getMatch(p);
        if (m == null || !m.isRunning() || !m.isAlive(p)) {
            e.setCancelled(true);
            return;
        }
        if (m.isPlaced(b)) {
            m.removePlaced(b);
            return;
        }
        e.setCancelled(true);
        if (b.getType() == Material.BED_BLOCK) {
            m.handleBedBreak(p, b);
        } else {
            plugin.send(p, ChatColor.RED + "You can only break blocks placed by players.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Arena arena = arenas.getByWorld(e.getLocation().getWorld());
        if (arena == null) return;
        Match m = games.getMatch(arena);
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (m != null && m.isPlaced(b)) {
                m.removePlaced(b);
            } else {
                it.remove(); // original map blocks and beds survive explosions
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Match m = games.getMatch(victim);
        if (m == null) return;
        if (!m.isRunning() || !m.isAlive(victim)) {
            e.setCancelled(true);
            return;
        }
        Player killer = null;
        if (e instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) e).getDamager();
            if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
                killer = (Player) ((Projectile) damager).getShooter();
            } else if (damager instanceof Player) {
                killer = (Player) damager;
            }
            if (killer != null && !killer.equals(victim) && m.isSameTeam(killer, victim)) {
                e.setCancelled(true); // no friendly fire
                return;
            }
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            m.onDeath(victim, killer);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getBlockY() == e.getFrom().getBlockY()) return;
        Player p = e.getPlayer();
        Match m = games.getMatch(p);
        if (m == null || !m.isRunning() || !m.isAlive(p)) return;
        if (m.getArena().isBelowVoid(e.getTo())) {
            m.onDeath(p, null); // fell into the void (below the arena's Min Y)
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player && games.isPlaying((Player) e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        games.handleQuit(e.getPlayer());
        plugin.getArenaManager().forget(e.getPlayer());
        plugin.getPrivateMenu().forget(e.getPlayer());
    }
}
