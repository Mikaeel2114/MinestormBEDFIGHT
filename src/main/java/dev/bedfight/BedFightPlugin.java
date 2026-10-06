package dev.bedfight;

import dev.bedfight.arena.ArenaManager;
import dev.bedfight.command.BedFightCommand;
import dev.bedfight.game.GameListener;
import dev.bedfight.game.GameManager;
import dev.bedfight.gui.PrivateMenu;
import dev.bedfight.hook.PartyHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class BedFightPlugin extends JavaPlugin {

    public static final String PERM_PLAY = "bedfight.play";
    public static final String PERM_ADMIN = "bedfight.admin";
    public static final String PERM_PRIVATE = "bedfight.rank.mvpplusplus";
    private static final String PREFIX = ChatColor.GOLD + "[BedFight] " + ChatColor.WHITE;

    private PartyHook partyHook;
    private ArenaManager arenaManager;
    private GameManager gameManager;
    private PrivateMenu privateMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        partyHook = new PartyHook(this);
        partyHook.hook();

        arenaManager = new ArenaManager(this);
        arenaManager.loadAll();

        gameManager = new GameManager(this);
        privateMenu = new PrivateMenu(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(privateMenu, this);
        getCommand("bedfight").setExecutor(new BedFightCommand(this));

        getLogger().info("BedFight enabled with " + arenaManager.getArenas().size() + " arena(s).");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }

    public void send(CommandSender to, String message) {
        to.sendMessage(PREFIX + message);
    }

    public Location getLobby() {
        String name = getConfig().getString("game.lobby-world", "");
        World w = (name == null || name.isEmpty()) ? null : Bukkit.getWorld(name);
        if (w == null) {
            w = Bukkit.getWorlds().get(0);
        }
        return w.getSpawnLocation();
    }

    public PartyHook getPartyHook() { return partyHook; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public GameManager getGameManager() { return gameManager; }
    public PrivateMenu getPrivateMenu() { return privateMenu; }
}
