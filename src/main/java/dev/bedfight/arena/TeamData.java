package dev.bedfight.arena;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;

public class TeamData {

    private final DyeColor color;
    private Location spawn; // the block the team spawns on (players are placed on top of it)
    private Location bed;

    public TeamData(DyeColor color) {
        this.color = color;
    }

    public DyeColor getColor() { return color; }
    public String getName() { return color.name(); }
    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public Location getBed() { return bed; }
    public void setBed(Location bed) { this.bed = bed; }

    public ChatColor getChatColor() {
        switch (color) {
            case RED: return ChatColor.RED;
            case BLUE: return ChatColor.BLUE;
            case GREEN: return ChatColor.DARK_GREEN;
            case LIME: return ChatColor.GREEN;
            case YELLOW: return ChatColor.YELLOW;
            case LIGHT_BLUE: return ChatColor.AQUA;
            case CYAN: return ChatColor.DARK_AQUA;
            case PINK:
            case MAGENTA: return ChatColor.LIGHT_PURPLE;
            case PURPLE: return ChatColor.DARK_PURPLE;
            case ORANGE: return ChatColor.GOLD;
            case GRAY: return ChatColor.DARK_GRAY;
            case SILVER: return ChatColor.GRAY;
            case BLACK: return ChatColor.BLACK;
            default: return ChatColor.WHITE;
        }
    }
}
