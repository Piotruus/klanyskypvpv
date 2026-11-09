package pl.firestar.klany;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Kolory nickow: ten sam klan (i Ty sam) = zielony, sojusznik = aqua, reszta = czerwony.
 * Kazdy gracz ma wlasna tablice (scoreboard), wiec kazdy widzi inne kolory.
 * Jesli gracz ma juz wlasna tablice z innego pluginu, dopisujemy tylko nasze teamy "fsk_...".
 */
public class NametagManager implements Listener {

    private final FirestarKlany plugin;
    private final Map<UUID, Scoreboard> tablice = new HashMap<>();

    public NametagManager(FirestarKlany plugin) { this.plugin = plugin; }

    private boolean wlaczone() { return plugin.getConfig().getBoolean("nametag.wlaczone", true); }

    private NamedTextColor kolor(String sciezka, NamedTextColor domyslny) {
        String nazwa = plugin.getConfig().getString(sciezka, "");
        NamedTextColor c = NamedTextColor.NAMES.value(nazwa.toLowerCase(Locale.ROOT));
        return c != null ? c : domyslny;
    }

    private Scoreboard tablica(Player v) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Scoreboard obecna = v.getScoreboard();
        if (obecna.equals(main)) {
            Scoreboard wlasna = tablice.computeIfAbsent(v.getUniqueId(),
                    k -> Bukkit.getScoreboardManager().getNewScoreboard());
            v.setScoreboard(wlasna);
            return wlasna;
        }
        return obecna;
    }

    public void odswiezWszystkich() {
        if (!wlaczone()) return;
        for (Player v : Bukkit.getOnlinePlayers()) odswiez(v);
    }

    private void odswiez(Player v) {
        Scoreboard sb = tablica(v);
        KlanManager m = plugin.manager();
        Klan kv = m.klanGracza(v.getUniqueId());
        boolean pokazTag = plugin.getConfig().getBoolean("nametag.pokaz-tag", true);
        NamedTextColor zielony = kolor("nametag.kolor-sojusznik", NamedTextColor.GREEN);
        NamedTextColor czerwony = kolor("nametag.kolor-wrog", NamedTextColor.RED);
        NamedTextColor aqua = kolor("nametag.kolor-sojusz", NamedTextColor.AQUA);

        for (Player t : Bukkit.getOnlinePlayers()) {
            Klan kt = m.klanGracza(t.getUniqueId());
            boolean swoj = v.equals(t) || (kv != null && kv == kt);
            boolean sojusz = !swoj && m.czySojusznicy(kv, kt);
            String rel = swoj ? "g" : sojusz ? "a" : "r";
            String tag = (pokazTag && kt != null) ? kt.tag() : "";
            String nazwa = "fsk_" + rel + "_" + tag;

            Team team = sb.getTeam(nazwa);
            if (team == null) {
                team = sb.registerNewTeam(nazwa);
                NamedTextColor kol = swoj ? zielony : sojusz ? aqua : czerwony;
                team.color(kol);
                team.prefix(tag.isEmpty() ? Component.empty() : Component.text("[" + tag + "] ", kol));
            }
            Team stary = sb.getEntryTeam(t.getName());
            if (stary != null && stary != team && stary.getName().startsWith("fsk_")) {
                stary.removeEntry(t.getName());
            }
            if (!team.hasEntry(t.getName())) team.addEntry(t.getName());
        }

        for (Team tm : new ArrayList<>(sb.getTeams())) {
            if (tm.getName().startsWith("fsk_") && tm.getEntries().isEmpty()) tm.unregister();
        }
    }

    /** Po /klan reload - usuwa nasze teamy, zeby nowe kolory z configu zadzialaly. */
    public void przebuduj() {
        for (Player v : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = v.getScoreboard();
            for (Team tm : new ArrayList<>(sb.getTeams())) {
                if (tm.getName().startsWith("fsk_")) tm.unregister();
            }
        }
        odswiezWszystkich();
    }

    public void wylacz() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = p.getScoreboard();
            for (Team tm : new ArrayList<>(sb.getTeams())) {
                if (tm.getName().startsWith("fsk_")) tm.unregister();
            }
            if (tablice.containsValue(sb)) p.setScoreboard(main);
        }
        tablice.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, this::odswiezWszystkich, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        tablice.remove(e.getPlayer().getUniqueId());
    }
}
