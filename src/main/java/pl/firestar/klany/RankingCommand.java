package pl.firestar.klany;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /ranking - top graczy. */
public class RankingCommand implements CommandExecutor {

    private final FirestarKlany plugin;

    public RankingCommand(FirestarKlany plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        RankingManager r = plugin.ranking();
        KlanManager m = plugin.manager();
        int ile = plugin.getConfig().getInt("ranking.top-graczy", 10);
        List<UUID> top = r.topGracze();

        Text.raw(s, plugin.getConfig().getString("naglowek", ""));
        Text.raw(s, "&8» &6&lTOP " + ile + " GRACZY");
        if (top.isEmpty()) Text.raw(s, "&7Brak graczy w rankingu.");
        for (int i = 0; i < Math.min(ile, top.size()); i++) {
            UUID u = top.get(i);
            RankingManager.Stat st = r.stat(u);
            Klan k = m.klanGracza(u);
            String tag = k == null ? "" : "&6[" + k.tag() + "] ";
            Text.raw(s, "&e" + (i + 1) + ". " + tag + "&f" + st.nick + " &8- &a" + st.punkty + " pkt &7("
                    + st.zabojstwa + " zab. / " + st.smierci + " zgonów, K/D " + kd(st) + ")");
        }
        if (s instanceof Player p) {
            RankingManager.Stat st = r.stat(p);
            Text.raw(s, "&8» &7Twoje miejsce: &e#" + r.pozycjaGracza(p.getUniqueId())
                    + " &7(&a" + st.punkty + " pkt&7)");
        }
        return true;
    }

    public static String kd(RankingManager.Stat st) {
        return String.format(Locale.US, "%.2f", st.zabojstwa / (double) Math.max(1, st.smierci));
    }
}
