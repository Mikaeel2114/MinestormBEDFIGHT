package dev.bedfight.command;

import dev.bedfight.BedFightPlugin;
import dev.bedfight.arena.Arena;
import dev.bedfight.arena.ArenaManager;
import dev.bedfight.arena.TeamData;
import dev.bedfight.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

public class BedFightCommand implements CommandExecutor {

    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "setuparena", "po1", "po2", "setspawn", "createteam", "setteam", "setbed", "buildmode", "save");

    private final BedFightPlugin plugin;
    private final ArenaManager arenas;
    private final GameManager games;

    public BedFightCommand(BedFightPlugin plugin) {
        this.plugin = plugin;
        this.arenas = plugin.getArenaManager();
        this.games = plugin.getGameManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player p = (Player) sender;

        // ---- player commands
        if (args.length == 0) {
            if (!p.hasPermission(BedFightPlugin.PERM_PLAY)) return noPerm(p);
            games.joinQueue(p);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("leave")) {
            games.leaveQueue(p);
            return true;
        }
        if (sub.equals("private")) {
            if (!p.hasPermission(BedFightPlugin.PERM_PRIVATE)) {
                plugin.send(p, ChatColor.RED + "Private matches require the MVP++ rank.");
                return true;
            }
            if (games.isPlaying(p)) {
                plugin.send(p, ChatColor.RED + "You are already in a match.");
                return true;
            }
            plugin.getPrivateMenu().openMaps(p);
            return true;
        }

        // ---- admin commands
        if (!ADMIN_SUBS.contains(sub)) {
            plugin.send(p, ChatColor.YELLOW + "Usage: /bedfight | /bedfight private | /bedfight leave");
            return true;
        }
        if (!p.hasPermission(BedFightPlugin.PERM_ADMIN)) return noPerm(p);

        if (sub.equals("setuparena")) return setupArena(p, args);
        if (sub.equals("buildmode")) {
            if (arenas.toggleBuildMode(p)) {
                p.sendMessage("you currently buildmode");
            } else {
                p.sendMessage("you left buildmode");
            }
            return true;
        }

        Arena arena = arenas.getSession(p);
        if (arena == null) {
            plugin.send(p, ChatColor.RED + "Start with /bedfight setuparena <world_name>.");
            return true;
        }
        if (!p.getWorld().getName().equalsIgnoreCase(arena.getWorldName())) {
            plugin.send(p, ChatColor.RED + "You must be in the arena world (" + arena.getWorldName() + ").");
            return true;
        }

        switch (sub) {
            case "po1": return setPos(p, arena, 1);
            case "po2": return setPos(p, arena, 2);
            case "setspawn":
                arena.setSpawn(p.getLocation());
                plugin.send(p, ChatColor.GREEN + "Main game spawn set.");
                return true;
            case "createteam": return createTeam(p, arena, args);
            case "setteam": return setTeam(p, arena, args);
            case "setbed": return setBed(p, arena, args);
            case "save": return save(p, arena);
            default: return true;
        }
    }

    // ---------------------------------------------------------------- admin handlers

    private boolean setupArena(Player p, String[] args) {
        if (args.length < 2) {
            plugin.send(p, ChatColor.RED + "Usage: /bedfight setuparena <world_name>");
            return true;
        }
        String name = args[1];
        File dir = new File(Bukkit.getWorldContainer(), name);
        if (!dir.isDirectory()) {
            plugin.send(p, ChatColor.RED + "World folder '" + name + "' was not found in the server directory.");
            return true;
        }
        if (!new File(dir, "level.dat").exists()) {
            plugin.send(p, ChatColor.RED + "'" + name + "' is not a world folder (level.dat missing).");
            return true;
        }
        Arena existing = arenas.getArena(name);
        if (existing != null && games.isBusy(existing)) {
            plugin.send(p, ChatColor.RED + "That arena is currently in a match.");
            return true;
        }
        World w = Bukkit.getWorld(name);
        if (w == null) {
            plugin.send(p, ChatColor.YELLOW + "Loading world '" + name + "'...");
            w = arenas.loadWorld(name);
        }
        if (w == null) {
            plugin.send(p, ChatColor.RED + "Could not load world '" + name + "'.");
            return true;
        }
        arenas.beginSetup(p, w.getName());
        p.teleport(w.getSpawnLocation());
        p.setGameMode(GameMode.CREATIVE);
        plugin.send(p, ChatColor.GREEN + "World '" + w.getName() + "' is loaded. Arena setup started - you were teleported there.");
        plugin.send(p, ChatColor.GRAY + "Next: po1, po2, setspawn, createteam <color>, setteam, setbed, save.");
        return true;
    }

    private boolean setPos(Player p, Arena arena, int which) {
        Location l = p.getLocation().getBlock().getLocation();
        if (which == 1) arena.setPos1(l); else arena.setPos2(l);
        plugin.send(p, ChatColor.GREEN + "Position " + which + " set to " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ".");
        if (arena.hasBounds()) {
            plugin.send(p, ChatColor.GRAY + "Void level (Min Y): " + ChatColor.WHITE + arena.getMinY()
                    + ChatColor.GRAY + " | Max build height (Max Y): " + ChatColor.WHITE + arena.getMaxY());
        }
        return true;
    }

    private boolean createTeam(Player p, Arena arena, String[] args) {
        if (args.length < 2) {
            plugin.send(p, ChatColor.RED + "Usage: /bedfight createteam <color>");
            return true;
        }
        DyeColor color = parseColor(p, args[1]);
        if (color == null) return true;
        if (arena.getTeams().containsKey(color.name())) {
            plugin.send(p, ChatColor.RED + "Team " + color.name() + " already exists.");
            return true;
        }
        arena.getTeams().put(color.name(), new TeamData(color));
        arenas.select(p, color.name());
        plugin.send(p, ChatColor.GREEN + "Team " + color.name() + " created and selected. "
                + "Now use /bedfight setteam and /bedfight setbed.");
        return true;
    }

    private boolean setTeam(Player p, Arena arena, String[] args) {
        TeamData t = resolveTeam(p, arena, args);
        if (t == null) return true;
        // the block directly under the admin's feet
        Location l = p.getLocation();
        Block b = l.getBlock();
        if (!b.getType().isSolid()) b = b.getRelative(BlockFace.DOWN);
        t.setSpawn(new Location(b.getWorld(), b.getX(), b.getY(), b.getZ(), l.getYaw(), l.getPitch()));
        plugin.send(p, ChatColor.GREEN + "Spawn block of team " + t.getName() + " set to "
                + b.getX() + ", " + b.getY() + ", " + b.getZ() + ".");
        return true;
    }

    private boolean setBed(Player p, Arena arena, String[] args) {
        TeamData t = resolveTeam(p, arena, args);
        if (t == null) return true;
        Block target = p.getTargetBlock((HashSet<Byte>) null, 6);
        if (target == null || target.getType() != Material.BED_BLOCK) {
            plugin.send(p, ChatColor.RED + "Look at the team's bed (within 6 blocks) and try again.");
            return true;
        }
        t.setBed(target.getLocation());
        plugin.send(p, ChatColor.GREEN + "Bed of team " + t.getName() + " registered at "
                + target.getX() + ", " + target.getY() + ", " + target.getZ() + ".");
        return true;
    }

    private boolean save(Player p, Arena arena) {
        String problem = arena.validate();
        if (problem != null) {
            plugin.send(p, ChatColor.RED + "Can't save yet: " + problem);
            return true;
        }
        try {
            arenas.save(arena);
            World w = Bukkit.getWorld(arena.getWorldName());
            if (w != null) w.save(); // persist any map edits made in build mode
            plugin.send(p, ChatColor.GREEN + "Arena '" + arena.getName() + "' saved (" + arena.getTeams().size()
                    + " teams, Y " + arena.getMinY() + "-" + arena.getMaxY() + ").");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arena " + arena.getName(), ex);
            plugin.send(p, ChatColor.RED + "Saving failed - see console.");
        }
        return true;
    }

    // ---------------------------------------------------------------- helpers

    /** Uses the color argument (args[1]) if given, otherwise the last created/selected team. */
    private TeamData resolveTeam(Player p, Arena arena, String[] args) {
        String name = args.length >= 2 ? args[1].toUpperCase() : arenas.getSelected(p);
        if (name == null) {
            plugin.send(p, ChatColor.RED + "No team selected. Use /bedfight createteam <color> or add the color: /bedfight " + args[0] + " <color>");
            return null;
        }
        TeamData t = arena.getTeam(name);
        if (t == null) {
            plugin.send(p, ChatColor.RED + "Team '" + name + "' does not exist. Create it with /bedfight createteam " + name.toLowerCase());
            return null;
        }
        arenas.select(p, t.getName());
        return t;
    }

    private DyeColor parseColor(Player p, String input) {
        try {
            return DyeColor.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ex) {
            StringBuilder sb = new StringBuilder();
            for (DyeColor c : DyeColor.values()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(c.name().toLowerCase());
            }
            plugin.send(p, ChatColor.RED + "Unknown color. Valid colors: " + sb);
            return null;
        }
    }

    private boolean noPerm(Player p) {
        plugin.send(p, ChatColor.RED + "You don't have permission to do that.");
        return true;
    }
}
