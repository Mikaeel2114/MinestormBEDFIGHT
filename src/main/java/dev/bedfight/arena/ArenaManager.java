package dev.bedfight.arena;

import dev.bedfight.BedFightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Loads/saves arenas (YML), tracks admin setup sessions and build mode. */
public class ArenaManager {

    private final BedFightPlugin plugin;
    private final File folder;
    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    private final Map<UUID, Arena> sessions = new HashMap<UUID, Arena>();
    private final Map<UUID, String> selectedTeam = new HashMap<UUID, String>();
    private final Set<UUID> buildMode = new HashSet<UUID>();

    public ArenaManager(BedFightPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "arenas");
    }

    // ---------------------------------------------------------------- worlds

    /** Returns the loaded world, loading it from the server folder if necessary. Null if no such world folder. */
    public World loadWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        File dir = new File(Bukkit.getWorldContainer(), name);
        if (!dir.isDirectory() || !new File(dir, "level.dat").exists()) return null;
        w = Bukkit.createWorld(new WorldCreator(name));
        if (w != null) {
            w.setGameRuleValue("doMobSpawning", "false");
        }
        return w;
    }

    // ---------------------------------------------------------------- registry

    public Collection<Arena> getArenas() { return Collections.unmodifiableCollection(arenas.values()); }
    public Arena getArena(String name) { return arenas.get(name.toLowerCase()); }

    public Arena getByWorld(World world) {
        if (world == null) return null;
        for (Arena a : arenas.values()) {
            if (a.getWorldName().equalsIgnoreCase(world.getName())) return a;
        }
        return null;
    }

    // ---------------------------------------------------------------- setup sessions

    public Arena beginSetup(Player p, String worldName) {
        Arena a = getArena(worldName);
        if (a == null) a = new Arena(worldName, worldName);
        sessions.put(p.getUniqueId(), a);
        selectedTeam.remove(p.getUniqueId());
        return a;
    }

    public Arena getSession(Player p) { return sessions.get(p.getUniqueId()); }
    public void select(Player p, String teamName) { selectedTeam.put(p.getUniqueId(), teamName); }
    public String getSelected(Player p) { return selectedTeam.get(p.getUniqueId()); }

    // ---------------------------------------------------------------- build mode

    /** @return true if build mode is now enabled. */
    public boolean toggleBuildMode(Player p) {
        if (buildMode.remove(p.getUniqueId())) return false;
        buildMode.add(p.getUniqueId());
        return true;
    }

    public boolean isBuildMode(Player p) { return buildMode.contains(p.getUniqueId()); }

    public void forget(Player p) {
        buildMode.remove(p.getUniqueId());
        selectedTeam.remove(p.getUniqueId());
        sessions.remove(p.getUniqueId());
    }

    // ---------------------------------------------------------------- persistence

    public void loadAll() {
        arenas.clear();
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".yml")) continue;
            try {
                Arena a = load(f);
                if (a != null) arenas.put(a.getName().toLowerCase(), a);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load arena " + f.getName(), ex);
            }
        }
    }

    private Arena load(File f) {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        String name = f.getName().substring(0, f.getName().length() - 4);
        String worldName = c.getString("world");
        if (worldName == null) return null;
        World w = loadWorld(worldName);
        if (w == null) {
            plugin.getLogger().warning("Arena " + name + ": world '" + worldName + "' not found, skipping.");
            return null;
        }
        Arena a = new Arena(name, worldName);
        a.setPos1(readLoc(c.getConfigurationSection("pos1"), w));
        a.setPos2(readLoc(c.getConfigurationSection("pos2"), w));
        a.setSpawn(readLoc(c.getConfigurationSection("spawn"), w));
        ConfigurationSection ts = c.getConfigurationSection("teams");
        if (ts != null) {
            for (String key : ts.getKeys(false)) {
                DyeColor color;
                try {
                    color = DyeColor.valueOf(key.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                TeamData t = new TeamData(color);
                t.setSpawn(readLoc(ts.getConfigurationSection(key + ".spawn"), w));
                t.setBed(readLoc(ts.getConfigurationSection(key + ".bed"), w));
                a.getTeams().put(color.name(), t);
            }
        }
        return a;
    }

    public void save(Arena a) throws IOException {
        if (!folder.exists()) folder.mkdirs();
        YamlConfiguration c = new YamlConfiguration();
        c.set("world", a.getWorldName());
        writeLoc(c, "pos1", a.getPos1());
        writeLoc(c, "pos2", a.getPos2());
        c.set("min-y", a.getMinY()); // void level
        c.set("max-y", a.getMaxY()); // max build height
        writeLoc(c, "spawn", a.getSpawn());
        for (TeamData t : a.getTeams().values()) {
            String base = "teams." + t.getName();
            writeLoc(c, base + ".spawn", t.getSpawn());
            writeLoc(c, base + ".bed", t.getBed());
        }
        c.save(new File(folder, a.getName() + ".yml"));
        arenas.put(a.getName().toLowerCase(), a);
    }

    private Location readLoc(ConfigurationSection s, World w) {
        if (s == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private void writeLoc(YamlConfiguration c, String path, Location l) {
        if (l == null) return;
        c.set(path + ".x", l.getX());
        c.set(path + ".y", l.getY());
        c.set(path + ".z", l.getZ());
        c.set(path + ".yaw", l.getYaw());
        c.set(path + ".pitch", l.getPitch());
    }
}
