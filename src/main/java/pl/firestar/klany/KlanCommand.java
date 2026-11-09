package pl.firestar.klany;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class KlanCommand implements CommandExecutor, TabCompleter {

    private final FirestarKlany plugin;

    public KlanCommand(FirestarKlany plugin) { this.plugin = plugin; }

    private KlanManager m() { return plugin.manager(); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) { pomoc(s); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!s.hasPermission("firestarklany.admin")) { Text.send(s, "&cBrak uprawnień."); return true; }
            plugin.przeladuj();
            Text.send(s, "&aPrzeładowano konfigurację.");
            return true;
        }
        if (sub.equals("info")) { info(s, a); return true; }
        if (sub.equals("topka") || sub.equals("top")) { topka(s); return true; }

        if (!(s instanceof Player p)) {
            Text.send(s, "&cTa komenda jest tylko dla graczy.");
            return true;
        }

        switch (sub) {
            case "stworz", "zaloz" -> stworz(p, a);
            case "zapros" -> zapros(p, a);
            case "dolacz" -> dolacz(p, a);
            case "opusc" -> opusc(p);
            case "usun" -> usun(p, a);
            case "sojusz" -> sojusz(p, a);
            case "akceptujsojusz" -> akceptujSojusz(p, a);
            case "zerwijsojusz" -> zerwijSojusz(p, a);
            case "panel" -> plugin.menus().otworzPanel(p);
            case "rekrutacje" -> plugin.menus().otworzRekrutacje(p, 0, false);
            default -> pomoc(s);
        }
        return true;
    }

    private void pomoc(CommandSender s) {
        Text.raw(s, plugin.getConfig().getString("naglowek", "&4&lFIRE&6&lSTAR&e&l.PL"));
        linia(s, "/klan stworz", "(TAG)", "Tworzy nowy klan.");
        linia(s, "/klan zapros", "(nick)", "Zaprasza gracza do klanu.");
        linia(s, "/klan dolacz", "(TAG)", "Przyjmuje zaproszenie do klanu.");
        linia(s, "/klan opusc", "", "Opuszcza aktualny klan.");
        linia(s, "/klan panel", "", "Informacje o klanie i zarządzanie nim.");
        linia(s, "/klan rekrutacje", "", "Klany z otwartą rekrutacją.");
        linia(s, "/klan topka", "", "Top 10 klanów.");
        linia(s, "/klan sojusz", "(TAG)", "Wysyła prośbę o sojusz.");
        linia(s, "/klan akceptujsojusz", "(TAG)", "Akceptuje prośbę o sojusz.");
        linia(s, "/klan zerwijsojusz", "(TAG)", "Zrywa sojusz.");
        linia(s, "/klan info", "[nazwa]", "Informacje o klanie.");
        linia(s, "/klan usun", "", "Rozwiązuje klan (właściciel).");
    }

    private void linia(CommandSender s, String komenda, String arg, String opis) {
        Text.raw(s, "&8» &6" + komenda + (arg.isEmpty() ? "" : " &e" + arg) + " &8- &7" + opis);
    }

    // ---------- podstawowe ----------

    private void stworz(Player p, String[] a) {
        if (a.length < 2) { Text.send(p, "&cUżycie: &6/klan stworz (TAG)"); return; }
        String err = m().stworz(p, a[1]);
        if (err != null) { Text.send(p, err); return; }
        String tag = a[1].toUpperCase(Locale.ROOT);
        Text.send(p, "&aStworzyłeś klan &6[" + tag + "]&a! Zarządzaj nim przez &e/klan panel&a.");
        Bukkit.broadcast(Text.c(Text.prefix() + "&e" + p.getName() + " &7założył klan &6[" + tag + "]&7!"));
    }

    private void zapros(Player p, String[] a) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) { Text.send(p, "&cNie należysz do żadnego klanu."); return; }
        if (!k.mozeZarzadzac(p.getUniqueId())) { Text.send(p, "&cTylko właściciel i zastępca mogą zapraszać."); return; }
        if (a.length < 2) { Text.send(p, "&cUżycie: &6/klan zapros (nick)"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Text.send(p, "&cTen gracz nie jest online."); return; }
        if (m().klanGracza(t.getUniqueId()) != null) { Text.send(p, "&cTen gracz ma już klan."); return; }
        if (k.czlonkowie().size() >= m().maxCzlonkow()) { Text.send(p, "&cTwój klan jest pełny."); return; }

        m().zapros(k, t.getUniqueId());
        m().log(k, p.getName() + " zaprosił " + t.getName());
        Text.send(p, "&aZaprosiłeś &e" + t.getName() + " &ado klanu.");

        Component przycisk = Text.c("&a&l[DOŁĄCZ]")
                .clickEvent(ClickEvent.runCommand("/klan dolacz " + k.tag()))
                .hoverEvent(HoverEvent.showText(Text.c("&7Kliknij, aby dołączyć")));
        t.sendMessage(Text.c(Text.prefix() + "&7Zaproszenie do klanu &6[" + k.tag() + "] &7od &e" + p.getName() + "&7. ")
                .append(przycisk));
    }

    private void dolacz(Player p, String[] a) {
        if (a.length < 2) { Text.send(p, "&cUżycie: &6/klan dolacz (TAG)"); return; }
        String err = m().dolaczZaproszenie(p, a[1]);
        if (err != null) { Text.send(p, err); return; }
        Text.send(p, "&aDołączyłeś do klanu &6[" + a[1].toUpperCase(Locale.ROOT) + "]&a!");
    }

    private void opusc(Player p) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) { Text.send(p, "&cNie należysz do żadnego klanu."); return; }
        if (k.czyWlasciciel(p.getUniqueId())) {
            Text.send(p, "&cJesteś właścicielem. Możesz rozwiązać klan: &e/klan usun potwierdz");
            return;
        }
        m().usun(k, p.getUniqueId());
        m().log(k, p.getName() + " opuścił klan");
        m().wyslij(k, "&e" + p.getName() + " &7opuścił klan.");
        Text.send(p, "&aOpuściłeś klan &6[" + k.tag() + "]&a.");
    }

    private void usun(Player p, String[] a) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) { Text.send(p, "&cNie należysz do żadnego klanu."); return; }
        if (!k.czyWlasciciel(p.getUniqueId())) { Text.send(p, "&cTylko właściciel może rozwiązać klan."); return; }
        if (a.length < 2 || !a[1].equalsIgnoreCase("potwierdz")) {
            Text.send(p, "&cTo trwale usunie klan! Potwierdź: &e/klan usun potwierdz");
            return;
        }
        m().wyslij(k, "&cKlan &6[" + k.tag() + "] &czostał rozwiązany przez właściciela.");
        m().rozwiaz(k);
    }

    // ---------- sojusze ----------

    private Klan klanZarzadcy(Player p) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) { Text.send(p, "&cNie należysz do żadnego klanu."); return null; }
        if (!k.mozeZarzadzac(p.getUniqueId())) {
            Text.send(p, "&cTylko właściciel i zastępca mogą zarządzać sojuszami.");
            return null;
        }
        return k;
    }

    private void sojusz(Player p, String[] a) {
        Klan k = klanZarzadcy(p);
        if (k == null) return;
        if (a.length < 2) { Text.send(p, "&cUżycie: &6/klan sojusz (TAG)"); return; }
        Klan cel = m().klan(a[1]);
        if (cel == null) { Text.send(p, "&cNie ma takiego klanu."); return; }
        String err = m().zaproponujSojusz(k, cel);
        if (err != null) { Text.send(p, err); return; }

        m().log(k, p.getName() + " wysłał prośbę o sojusz do [" + cel.tag() + "]");
        Text.send(p, "&aWysłano prośbę o sojusz do klanu &6[" + cel.tag() + "]&a.");

        Component przycisk = Text.c("&a&l[AKCEPTUJ]")
                .clickEvent(ClickEvent.runCommand("/klan akceptujsojusz " + k.tag()))
                .hoverEvent(HoverEvent.showText(Text.c("&7Kliknij, aby zawrzeć sojusz")));
        for (UUID u : cel.czlonkowie()) {
            if (!cel.mozeZarzadzac(u)) continue;
            Player t = Bukkit.getPlayer(u);
            if (t == null) continue;
            t.sendMessage(Text.c(Text.prefix() + "&7Klan &6[" + k.tag() + "] &7proponuje sojusz. ").append(przycisk));
        }
    }

    private void akceptujSojusz(Player p, String[] a) {
        Klan k = klanZarzadcy(p);
        if (k == null) return;
        if (a.length < 2) { Text.send(p, "&cUżycie: &6/klan akceptujsojusz (TAG)"); return; }
        Klan nadawca = m().klan(a[1]);
        String err = m().akceptujSojusz(k, a[1]);
        if (err != null) { Text.send(p, err); return; }
        m().wyslij(k, "&aSojusz z klanem &b[" + nadawca.tag() + "] &azawarty!");
        m().wyslij(nadawca, "&aSojusz z klanem &b[" + k.tag() + "] &azawarty!");
        Bukkit.broadcast(Text.c(Text.prefix() + "&6[" + k.tag() + "] &7i &6[" + nadawca.tag() + "] &7zawarły sojusz!"));
    }

    private void zerwijSojusz(Player p, String[] a) {
        Klan k = klanZarzadcy(p);
        if (k == null) return;
        if (a.length < 2) { Text.send(p, "&cUżycie: &6/klan zerwijsojusz (TAG)"); return; }
        Klan drugi = m().klan(a[1]);
        String err = m().zerwijSojusz(k, a[1]);
        if (err != null) { Text.send(p, err); return; }
        m().wyslij(k, "&cZerwano sojusz z klanem &6[" + drugi.tag() + "]&c.");
        m().wyslij(drugi, "&cKlan &6[" + k.tag() + "] &czerwał sojusz z Wami.");
    }

    // ---------- info / topka ----------

    private void info(CommandSender s, String[] a) {
        Klan k = null;
        if (a.length > 1) k = m().klan(a[1]);
        else if (s instanceof Player p) k = m().klanGracza(p.getUniqueId());
        if (k == null) { Text.send(s, "&cNie znaleziono klanu. Użycie: &6/klan info (nazwa)"); return; }

        List<String> nicki = new ArrayList<>();
        for (UUID u : k.czlonkowie()) {
            String kol = m().widoczny(u) ? "&a" : "&7";
            String znak = k.czyWlasciciel(u) ? "&c★ " : k.czyZastepca(u) ? "&6☆ " : "";
            nicki.add(znak + kol + m().nazwa(u));
        }
        RankingManager r = plugin.ranking();
        Text.raw(s, plugin.getConfig().getString("naglowek", ""));
        Text.raw(s, "&8» &7Klan: &6[" + k.tag() + "]");
        Text.raw(s, "&8» &7Punkty: &e" + r.klanPunkty(k) + " &7(miejsce &e#" + r.pozycjaKlanu(k) + "&7)");
        Text.raw(s, "&8» &7Założony: &f" + m().dataTekst(k.utworzony()));
        Text.raw(s, "&8» &7Członkowie &8(&f" + k.czlonkowie().size() + "&8/&f" + m().maxCzlonkow() + "&8): " + String.join("&8, ", nicki));
        Text.raw(s, "&8» &7Sojusznicy: " + (k.sojusznicy().isEmpty() ? "&8brak" : "&b" + String.join("&7, &b", k.sojusznicy())));
        Text.raw(s, "&8» &7Rekrutacja: " + (k.rekrutacjaOtwarta()
                ? "&aotwarta &7(cena: &a" + Text.kwota(k.cena()) + "&7)" : "&czamknięta"));
    }

    private void topka(CommandSender s) {
        RankingManager r = plugin.ranking();
        int ile = plugin.getConfig().getInt("ranking.top-klanow", 10);
        List<Klan> top = r.topKlany();
        Klan moj = s instanceof Player p ? m().klanGracza(p.getUniqueId()) : null;

        Text.raw(s, plugin.getConfig().getString("naglowek", ""));
        Text.raw(s, "&8» &6&lTOP " + ile + " KLANÓW &8(suma punktów członków)");
        if (top.isEmpty()) Text.raw(s, "&7Nie ma jeszcze żadnych klanów.");
        for (int i = 0; i < Math.min(ile, top.size()); i++) wierszTopki(s, i + 1, top.get(i), moj, r);
        if (moj != null) {
            int poz = r.pozycjaKlanu(moj);
            if (poz > ile) {
                Text.raw(s, "&8...");
                wierszTopki(s, poz, moj, moj, r);
            }
        }
    }

    private void wierszTopki(CommandSender s, int miejsce, Klan k, Klan moj, RankingManager r) {
        String kol = k == moj ? "&a" : miejsce == 1 ? "&6" : miejsce == 2 ? "&f" : miejsce == 3 ? "&c" : "&7";
        Text.raw(s, "&e" + miejsce + ". " + kol + "[" + k.tag() + "] &8- &f" + r.klanPunkty(k)
                + " pkt &7(" + k.czlonkowie().size() + " czł.)");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("stworz", "zapros", "dolacz", "opusc", "panel", "rekrutacje", "topka",
                    "sojusz", "akceptujsojusz", "zerwijsojusz", "info", "usun"))
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2) {
            String pre = a[1].toLowerCase(Locale.ROOT);
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "zapros" -> {
                    for (Player p : Bukkit.getOnlinePlayers())
                        if (m().klanGracza(p.getUniqueId()) == null && p.getName().toLowerCase(Locale.ROOT).startsWith(pre))
                            out.add(p.getName());
                }
                case "info", "dolacz", "sojusz", "akceptujsojusz", "zerwijsojusz" -> {
                    for (Klan k : m().wszystkie())
                        if (k.tag().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(k.tag());
                }
                case "usun" -> { if ("potwierdz".startsWith(pre)) out.add("potwierdz"); }
                default -> {}
            }
        }
        return out;
    }
}
