package dev.bedfight.arena;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;

public class Arena {

    private final String name;
    private final String worldName;
    private Location pos1;
    private Location pos2;
    private Location spawn;
    private int minX, maxX, minY, maxY, minZ, maxZ;
    private final Map<String, TeamData> teams = new LinkedHashMap<String, TeamData>();

    public Arena(String name, String worldName) {
        this.name = name;
        this.worldName = worldName;
    }

    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public Location getPos1() { return pos1; }
    public Location getPos2() { return pos2; }
    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public Map<String, TeamData> getTeams() { return teams; }
    public TeamData getTeam(String color) { return teams.get(color.toUpperCase()); }

    public void setPos1(Location l) { this.pos1 = l; recalculate(); }
    public void setPos2(Location l) { this.pos2 = l; recalculate(); }

    /** Derives the bounding box. Min Y = void level, Max Y = max build height. */
    private void recalculate() {
        if (pos1 == null || pos2 == null) return;
        minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public boolean hasBounds() { return pos1 != null && pos2 != null; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }

    /** True if the location is inside the buildable box (X/Z area and between void level and build height). */
    public boolean isInside(Location l) {
        if (!hasBounds() || l.getWorld() == null || !l.getWorld().getName().equalsIgnoreCase(worldName)) return false;
        return l.getBlockX() >= minX && l.getBlockX() <= maxX
                && l.getBlockY() >= minY && l.getBlockY() <= maxY
                && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
    }

    public boolean isBelowVoid(Location l) {
        return hasBounds() && l.getY() < minY;
    }

    /** @return null if the arena is playable, otherwise a human readable problem. */
    public String validate() {
        if (!hasBounds()) return "Boundaries missing (use /bedfight po1 and /bedfight po2).";
        if (spawn == null) return "Main spawn missing (use /bedfight setspawn).";
        if (teams.size() < 2) return "At least 2 teams are required (use /bedfight createteam <color>).";
        for (TeamData t : teams.values()) {
            if (t.getSpawn() == null) return "Team " + t.getName() + " has no spawn (use /bedfight setteam).";
            if (t.getBed() == null) return "Team " + t.getName() + " has no bed (use /bedfight setbed).";
        }
        return null;
    }
}
