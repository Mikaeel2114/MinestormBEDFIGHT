package dev.bedfight.hook;

import dev.bedfight.BedFightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridge to the custom "minestormparty" plugin.
 *
 * The real API of minestormparty is not known at compile time, so everything goes through reflection and the method
 * names are configurable in config.yml (section "hook"). If the party cannot be resolved, a player simply counts as a
 * party of one, and team splitting falls back to a local random shuffle.
 *
 * To use a typed API instead, add minestormparty as a provided Maven dependency and replace the bodies of
 * getPartyMembers(...) and splitIntoTeams(...).
 */
public class PartyHook {

    private final BedFightPlugin plugin;
    private Plugin party;
    private Object manager;

    public PartyHook(BedFightPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        party = Bukkit.getPluginManager().getPlugin("minestormparty");
        if (party == null || !party.isEnabled()) {
            plugin.getLogger().warning("minestormparty not found/enabled - parties and native team splitting disabled.");
            party = null;
            return;
        }
        manager = invokeAny(party, names("hook.manager-methods"));
        if (manager == null) manager = party;
        plugin.getLogger().info("Hooked into minestormparty (" + party.getDescription().getVersion() + ").");
    }

    public boolean isAvailable() { return party != null; }

    /** The player plus everyone in their party (always contains the player itself). */
    public List<Player> getPartyMembers(Player player) {
        List<Player> out = new ArrayList<Player>();
        out.add(player);
        if (manager == null) return out;

        Object partyObj = invokeAny(manager, names("hook.get-party-methods"), player);
        if (partyObj == null) partyObj = invokeAny(manager, names("hook.get-party-methods"), player.getUniqueId());
        if (partyObj == null) return out;

        Object members = invokeAny(partyObj, names("hook.members-methods"));
        for (Player p : toPlayers(members)) {
            if (!out.contains(p)) out.add(p);
        }
        return out;
    }

    /** Randomly splits the players into teams of teamSize (last team may be smaller). */
    public List<List<Player>> splitIntoTeams(List<Player> players, int teamSize) {
        List<Player> shuffled = new ArrayList<Player>(players);
        Collections.shuffle(shuffled);

        if (manager != null) {
            Object res = invokeAny(manager, names("hook.split-methods"), shuffled, teamSize);
            List<List<Player>> parsed = new ArrayList<List<Player>>();
            if (res instanceof Collection) {
                for (Object o : (Collection<?>) res) {
                    List<Player> team = toPlayers(o);
                    if (!team.isEmpty()) parsed.add(team);
                }
            }
            if (!parsed.isEmpty()) return parsed;
        }

        List<List<Player>> teams = new ArrayList<List<Player>>();
        for (int i = 0; i < shuffled.size(); i += teamSize) {
            teams.add(new ArrayList<Player>(shuffled.subList(i, Math.min(i + teamSize, shuffled.size()))));
        }
        return teams;
    }

    // ---------------------------------------------------------------- reflection helpers

    private List<String> names(String path) {
        return plugin.getConfig().getStringList(path);
    }

    private Object invokeAny(Object target, List<String> methodNames, Object... args) {
        for (String name : methodNames) {
            Object r = invoke(target, name, args);
            if (r instanceof Optional) r = ((Optional<?>) r).orElse(null);
            if (r != null) return r;
        }
        return null;
    }

    private Object invoke(Object target, String name, Object... args) {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
            if (!accepts(m.getParameterTypes(), args)) continue;
            try {
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return null;
    }

    private boolean accepts(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t.isPrimitive()) {
                if (t == int.class) t = Integer.class;
                else if (t == long.class) t = Long.class;
                else if (t == boolean.class) t = Boolean.class;
                else if (t == double.class) t = Double.class;
            }
            if (!t.isInstance(args[i])) return false;
        }
        return true;
    }

    private List<Player> toPlayers(Object o) {
        List<Player> out = new ArrayList<Player>();
        if (!(o instanceof Collection)) return out;
        for (Object e : (Collection<?>) o) {
            Player p = null;
            if (e instanceof Player) p = (Player) e;
            else if (e instanceof UUID) p = Bukkit.getPlayer((UUID) e);
            else if (e instanceof String) p = Bukkit.getPlayerExact((String) e);
            else if (e instanceof Collection) out.addAll(toPlayers(e));
            if (p != null && p.isOnline() && !out.contains(p)) out.add(p);
        }
        return out;
    }
}
