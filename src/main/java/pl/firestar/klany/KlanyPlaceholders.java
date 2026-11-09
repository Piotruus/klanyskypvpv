package pl.firestar.klany;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Gracz:   %firestarklany_tag% _tag_prefix _nazwa _rola _wlasciciel _czlonkowie _czlonkowie_online
 *          _max_czlonkow _rekrutacja _cena _ma_klan _liczba_klanow _sojusznicy
 *          _punkty _pozycja _zabojstwa _smierci _kd
 * Klan:    _klan_punkty _klan_pozycja
 * Topki:   _top_gracz_N_nick _top_gracz_N_punkty _top_klan_N_tag _top_klan_N_punkty   (N = 1, 2, 3 ...)
 * Rel:     %rel_firestarklany_kolor% (&a swoj / &b sojusznik / &c obcy), _sojusznik, _sojusz, _tag
 */
public class KlanyPlaceholders extends PlaceholderExpansion implements Relational {

    private final FirestarKlany plugin;

    public KlanyPlaceholders(FirestarKlany plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "firestarklany"; }
    @Override public String getAuthor() { return "FIRESTAR.PL"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onRequest(OfflinePlayer gracz, String params) {
        KlanManager m = plugin.manager();
        RankingManager r = plugin.ranking();
        String p = params.toLowerCase(Locale.ROOT);

        if (p.equals("liczba_klanow")) return String.valueOf(m.wszystkie().size());
        if (p.startsWith("top_")) return topka(p.split("_"));

        UUID id = gracz == null ? null : gracz.getUniqueId();
        Klan k = id == null ? null : m.klanGracza(id);
        RankingManager.Stat st = id == null ? null : r.stat(id);

        switch (p) {
            case "ma_klan": return String.valueOf(k != null);
            case "tag":
            case "nazwa": return k == null ? "" : k.tag();
            case "tag_prefix": return k == null ? "" : "[" + k.tag() + "] ";
            case "rola": return k == null ? "Brak" : k.rola(id).nazwa;
            case "wlasciciel": return k == null ? "" : m.nazwa(k.wlasciciel());
            case "czlonkowie": return k == null ? "0" : String.valueOf(k.czlonkowie().size());
            case "czlonkowie_online":
                if (k == null) return "0";
                long online = 0;
                for (UUID u : k.czlonkowie()) if (m.widoczny(u)) online++;
                return String.valueOf(online);
            case "max_czlonkow": return String.valueOf(m.maxCzlonkow());
            case "rekrutacja": return k == null ? "" : (k.rekrutacjaOtwarta() ? "otwarta" : "zamknięta");
            case "cena": return k == null ? "" : Text.kwota(k.cena());
            case "sojusznicy": return k == null ? "0" : String.valueOf(k.sojusznicy().size());

            case "punkty": return id == null ? "0" : String.valueOf(r.punkty(id));
            case "pozycja": {
                int poz = id == null ? 0 : r.pozycjaGracza(id);
                return poz == 0 ? "-" : String.valueOf(poz);
            }
            case "zabojstwa": return st == null ? "0" : String.valueOf(st.zabojstwa);
            case "smierci": return st == null ? "0" : String.valueOf(st.smierci);
            case "kd": return st == null ? "0.00" : RankingCommand.kd(st);

            case "klan_punkty": return k == null ? "0" : String.valueOf(r.klanPunkty(k));
            case "klan_pozycja": {
                int poz = k == null ? 0 : r.pozycjaKlanu(k);
                return poz == 0 ? "-" : String.valueOf(poz);
            }
            default: return null;
        }
    }

    /** top_gracz_1_nick, top_gracz_1_punkty, top_klan_1_tag, top_klan_1_punkty */
    private String topka(String[] cz) {
        if (cz.length != 4) return null;
        int n;
        try { n = Integer.parseInt(cz[2]); } catch (NumberFormatException e) { return null; }
        if (n < 1) return null;
        RankingManager r = plugin.ranking();

        if (cz[1].equals("gracz")) {
            List<UUID> top = r.topGracze();
            if (n > top.size()) return cz[3].equals("nick") ? "-" : "0";
            RankingManager.Stat st = r.stat(top.get(n - 1));
            return switch (cz[3]) {
                case "nick" -> st.nick;
                case "punkty" -> String.valueOf(st.punkty);
                default -> null;
            };
        }
        if (cz[1].equals("klan")) {
            List<Klan> top = r.topKlany();
            if (n > top.size()) return cz[3].equals("tag") ? "-" : "0";
            Klan k = top.get(n - 1);
            return switch (cz[3]) {
                case "tag" -> k.tag();
                case "punkty" -> String.valueOf(r.klanPunkty(k));
                default -> null;
            };
        }
        return null;
    }

    @Override
    public String onPlaceholderRequest(Player jeden, Player dwa, String identyfikator) {
        if (jeden == null || dwa == null) return "";
        KlanManager m = plugin.manager();
        Klan a = m.klanGracza(jeden.getUniqueId());
        Klan b = m.klanGracza(dwa.getUniqueId());
        boolean swoj = jeden.equals(dwa) || (a != null && a == b);
        boolean sojusz = !swoj && m.czySojusznicy(a, b);
        switch (identyfikator.toLowerCase(Locale.ROOT)) {
            case "kolor": return swoj ? "&a" : sojusz ? "&b" : "&c";
            case "sojusznik": return String.valueOf(swoj || sojusz);
            case "sojusz": return String.valueOf(sojusz);
            case "tag": return b == null ? "" : b.tag();
            default: return null;
        }
    }
}
