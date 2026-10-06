package dev.bedfight.game;

import dev.bedfight.BedFightPlugin;
import dev.bedfight.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Matchmaking queue and running matches. */
public class GameManager {

    private final BedFightPlugin plugin;
    private final List<UUID> queue = new ArrayList<UUID>();
    private final Map<Arena, Match> byArena = new HashMap<Arena, Match>();
    private final Map<UUID, Match> byPlayer = new HashMap<UUID, Match>();

    public GameManager(BedFightPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- queries

    public Match getMatch(Player p) { return byPlayer.get(p.getUniqueId()); }
    public Match getMatch(Arena a) { return byArena.get(a); }
    public boolean isPlaying(Player p) { return byPlayer.containsKey(p.getUniqueId()); }
    public boolean isBusy(Arena a) { return a != null && byArena.containsKey(a); }
    public boolean isQueued(Player p) { return queue.contains(p.getUniqueId()); }

    // ---------------------------------------------------------------- public queue

    public void joinQueue(Player p) {
        if (isPlaying(p)) {
            plugin.send(p, ChatColor.RED + "You are already in a match.");
            return;
        }
        if (isQueued(p)) {
            plugin.send(p, ChatColor.RED + "You are already in the queue. Use /bedfight leave to leave it.");
            return;
        }
        List<Player> group = plugin.getPartyHook().getPartyMembers(p);
        for (Player m : group) {
            if (isPlaying(m) || isQueued(m)) {
                plugin.send(p, ChatColor.RED + m.getName() + " is already in a match or queue.");
                return;
            }
        }
        for (Player m : group) {
            queue.add(m.getUniqueId());
            plugin.send(m, ChatColor.GREEN + "You joined the BedFight queue. " + ChatColor.GRAY + "(" + queue.size() + " waiting)");
        }
        tryStartQueue();
    }

    public void leaveQueue(Player p) {
        if (queue.remove(p.getUniqueId())) {
            plugin.send(p, ChatColor.YELLOW + "You left the queue.");
        } else {
            plugin.send(p, ChatColor.RED + "You are not in the queue.");
        }
    }

    private void tryStartQueue() {
        int teamSize = Math.max(1, plugin.getConfig().getInt("queue.team-size", 1));
        Arena arena = findFreeArena();
        if (arena == null) return;

        int needed = arena.getTeams().size() * teamSize;
        if (queue.size() < needed) return;

        List<UUID> taken = new ArrayList<UUID>();
        List<Player> chosen = new ArrayList<Player>();
        Iterator<UUID> it = queue.iterator();
        while (it.hasNext() && chosen.size() < needed) {
            UUID id = it.next();
            it.remove();
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                chosen.add(p);
                taken.add(id);
            }
        }
        if (chosen.size() < needed) { // not enough online players, put them back
            queue.addAll(0, taken);
            return;
        }
        startMatch(arena, plugin.getPartyHook().splitIntoTeams(chosen, teamSize));
    }

    private Arena findFreeArena() {
        List<Arena> free = new ArrayList<Arena>();
        for (Arena a : plugin.getArenaManager().getArenas()) {
            if (!isBusy(a) && a.validate() == null) free.add(a);
        }
        if (free.isEmpty()) return null;
        Collections.shuffle(free);
        return free.get(0);
    }

    // ---------------------------------------------------------------- private matches

    public void startPrivate(Player host, Arena arena, int teamSize) {
        if (!host.hasPermission(BedFightPlugin.PERM_PRIVATE)) {
            plugin.send(host, ChatColor.RED + "Private matches require the MVP++ rank.");
            return;
        }
        if (isBusy(arena)) {
            plugin.send(host, ChatColor.RED + "That arena is currently in use.");
            return;
        }
        String problem = arena.validate();
        if (problem != null) {
            plugin.send(host, ChatColor.RED + "That arena is not ready: " + problem);
            return;
        }
        List<Player> members = plugin.getPartyHook().getPartyMembers(host);
        for (Player m : members) {
            if (isPlaying(m)) {
                plugin.send(host, ChatColor.RED + m.getName() + " is already in a match.");
                return;
            }
        }
        List<List<Player>> groups = plugin.getPartyHook().splitIntoTeams(members, teamSize);
        if (groups.size() < 2) {
            plugin.send(host, ChatColor.RED + "You need at least " + (teamSize + 1)
                    + " players in your party for a " + teamSize + "v" + teamSize + ".");
            return;
        }
        if (groups.size() > arena.getTeams().size()) {
            plugin.send(host, ChatColor.RED + "This map only supports " + arena.getTeams().size() + " teams.");
            return;
        }
        for (Player m : members) queue.remove(m.getUniqueId());
        startMatch(arena, groups);
    }

    // ---------------------------------------------------------------- match registry

    private void startMatch(Arena arena, List<List<Player>> groups) {
        Match m = new Match(plugin, arena, groups);
        byArena.put(arena, m);
        for (UUID id : m.getParticipants()) byPlayer.put(id, m);
        m.start();
    }

    public void unregister(Match m) {
        byArena.remove(m.getArena());
        for (UUID id : m.getParticipants()) {
            if (byPlayer.get(id) == m) byPlayer.remove(id);
        }
    }

    public void handleQuit(Player p) {
        queue.remove(p.getUniqueId());
        Match m = byPlayer.remove(p.getUniqueId());
        if (m != null) m.onQuit(p);
    }

    public void shutdown() {
        queue.clear();
        for (Match m : new ArrayList<Match>(byArena.values())) m.forceEnd();
    }
}
