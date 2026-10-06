package dev.bedfight.game;

import dev.bedfight.BedFightPlugin;
import dev.bedfight.arena.Arena;
import dev.bedfight.arena.TeamData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One running game on one arena. */
public class Match {

    private static class TeamState {
        final TeamData data;
        final Set<UUID> alive = new HashSet<UUID>();
        boolean bedAlive = true;

        TeamState(TeamData data) { this.data = data; }
    }

    private final BedFightPlugin plugin;
    private final Arena arena;
    private final List<TeamState> teams = new ArrayList<TeamState>();
    private final Map<UUID, TeamState> byPlayer = new HashMap<UUID, TeamState>();
    private final Set<Block> placed = new HashSet<Block>();
    private final List<BlockState> savedBeds = new ArrayList<BlockState>();
    private boolean running;
    private boolean cleaned;

    public Match(BedFightPlugin plugin, Arena arena, List<List<Player>> groups) {
        this.plugin = plugin;
        this.arena = arena;
        List<TeamData> pool = new ArrayList<TeamData>(arena.getTeams().values());
        Collections.shuffle(pool);
        for (int i = 0; i < groups.size() && i < pool.size(); i++) {
            TeamState ts = new TeamState(pool.get(i));
            for (Player p : groups.get(i)) {
                ts.alive.add(p.getUniqueId());
                byPlayer.put(p.getUniqueId(), ts);
            }
            teams.add(ts);
        }
    }

    // ---------------------------------------------------------------- queries

    public Arena getArena() { return arena; }
    public boolean isRunning() { return running; }
    public Set<UUID> getParticipants() { return byPlayer.keySet(); }
    public boolean isParticipant(Player p) { return byPlayer.containsKey(p.getUniqueId()); }

    public boolean isAlive(Player p) {
        TeamState ts = byPlayer.get(p.getUniqueId());
        return ts != null && ts.alive.contains(p.getUniqueId());
    }

    public boolean isSameTeam(Player a, Player b) {
        TeamState ta = byPlayer.get(a.getUniqueId());
        return ta != null && ta == byPlayer.get(b.getUniqueId());
    }

    public void addPlaced(Block b) { placed.add(b); }
    public boolean isPlaced(Block b) { return placed.contains(b); }
    public boolean removePlaced(Block b) { return placed.remove(b); }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        running = true;
        for (TeamData td : arena.getTeams().values()) saveBed(td);
        for (TeamState ts : teams) {
            for (UUID id : new ArrayList<UUID>(ts.alive)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) prepare(p, ts);
            }
        }
        broadcast(ChatColor.GREEN + "The match has started! Destroy enemy beds and protect your own.");
    }

    private void end(TeamState winner) {
        if (!running) return;
        running = false;
        if (winner != null) {
            broadcast(winner.data.getChatColor() + "" + ChatColor.BOLD + winner.data.getName() + " team wins!");
        } else {
            broadcast(ChatColor.YELLOW + "The match ended without a winner.");
        }
        long delay = plugin.getConfig().getInt("game.end-delay-seconds", 5) * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
            }
        }.runTaskLater(plugin, delay);
    }

    /** Immediate stop + cleanup (used on plugin disable). */
    public void forceEnd() {
        running = false;
        cleanup();
    }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;

        Location lobby = plugin.getLobby();
        for (UUID id : byPlayer.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.teleport(lobby);
        }

        // reset the map: remove everything players built, restore the beds
        for (Block b : placed) b.setType(Material.AIR);
        placed.clear();
        for (BlockState s : savedBeds) s.update(true, false);
        savedBeds.clear();

        plugin.getGameManager().unregister(this);
    }

    // ---------------------------------------------------------------- game events

    public void onDeath(Player victim, Player killer) {
        TeamState ts = byPlayer.get(victim.getUniqueId());
        if (!running || ts == null || !ts.alive.contains(victim.getUniqueId())) return;

        String name = colored(victim, ts);
        String by = (killer != null && !killer.equals(victim) && byPlayer.containsKey(killer.getUniqueId()))
                ? " was killed by " + colored(killer, byPlayer.get(killer.getUniqueId()))
                : " died";

        if (ts.bedAlive) {
            broadcast(name + ChatColor.GRAY + by + ChatColor.GRAY + ".");
            prepare(victim, ts); // instant respawn at team spawn
        } else {
            ts.alive.remove(victim.getUniqueId());
            broadcast(name + ChatColor.GRAY + by + ChatColor.GRAY + ". " + ChatColor.RED + "" + ChatColor.BOLD + "FINAL KILL!");
            victim.getInventory().clear();
            victim.setGameMode(GameMode.SPECTATOR);
            victim.teleport(arena.getSpawn());
            checkWin();
        }
    }

    public void onQuit(Player p) {
        TeamState ts = byPlayer.get(p.getUniqueId());
        if (ts == null) return;
        if (running && ts.alive.remove(p.getUniqueId())) {
            broadcast(colored(p, ts) + ChatColor.GRAY + " left the match.");
            checkWin();
        }
    }

    /** Called for every natural bed block that gets broken. The listener always cancels the vanilla break. */
    public void handleBedBreak(Player breaker, Block block) {
        TeamState own = byPlayer.get(breaker.getUniqueId());
        for (TeamData td : arena.getTeams().values()) {
            Location bed = td.getBed();
            if (bed == null || !nearBed(bed, block)) continue;

            TeamState target = stateOf(td);
            if (target == null || target == own || !target.bedAlive) {
                plugin.send(breaker, ChatColor.RED + "You can't break this bed.");
                return;
            }
            target.bedAlive = false;
            destroyBed(bed);
            broadcast(ChatColor.RED + "" + ChatColor.BOLD + "BED DESTROYED! " + ChatColor.RESET
                    + target.data.getChatColor() + target.data.getName() + ChatColor.GRAY
                    + " bed was destroyed by " + colored(breaker, own) + ChatColor.GRAY + ".");
            for (UUID id : target.alive) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendMessage(ChatColor.RED + "Your bed was destroyed - you won't respawn anymore!");
            }
            return;
        }
        plugin.send(breaker, ChatColor.RED + "You can't break this block.");
    }

    // ---------------------------------------------------------------- internals

    private void checkWin() {
        if (!running) return;
        int remaining = 0;
        TeamState last = null;
        for (TeamState t : teams) {
            if (!t.alive.isEmpty()) {
                remaining++;
                last = t;
            }
        }
        if (remaining <= 1) end(last);
    }

    private TeamState stateOf(TeamData td) {
        for (TeamState t : teams) if (t.data == td) return t;
        return null;
    }

    private void prepare(Player p, TeamState ts) {
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.getInventory().addItem(new ItemStack(Material.WOOD_SWORD));
        p.teleport(spawnOf(ts));
    }

    private Location spawnOf(TeamState ts) {
        Location s = ts.data.getSpawn();
        return new Location(s.getWorld(), s.getBlockX() + 0.5, s.getBlockY() + 1, s.getBlockZ() + 0.5, s.getYaw(), s.getPitch());
    }

    private boolean nearBed(Location bed, Block b) {
        if (!bed.getWorld().equals(b.getWorld()) || bed.getBlockY() != b.getY()) return false;
        return Math.abs(bed.getBlockX() - b.getX()) + Math.abs(bed.getBlockZ() - b.getZ()) <= 1;
    }

    private void saveBed(TeamData td) {
        Location bed = td.getBed();
        if (bed == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                Block b = bed.getWorld().getBlockAt(bed.getBlockX() + dx, bed.getBlockY(), bed.getBlockZ() + dz);
                if (b.getType() == Material.BED_BLOCK) savedBeds.add(b.getState());
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void destroyBed(Location bed) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                Block b = bed.getWorld().getBlockAt(bed.getBlockX() + dx, bed.getBlockY(), bed.getBlockZ() + dz);
                if (b.getType() == Material.BED_BLOCK) b.setTypeIdAndData(0, (byte) 0, false); // no physics => no item drop
            }
        }
    }

    private String colored(Player p, TeamState ts) {
        ChatColor c = ts != null ? ts.data.getChatColor() : ChatColor.WHITE;
        return c + p.getName();
    }

    private void broadcast(String msg) {
        for (UUID id : byPlayer.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(msg);
        }
    }
}
