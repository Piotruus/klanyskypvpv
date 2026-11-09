package pl.firestar.klany;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Menus implements Listener {

    public enum Typ { PANEL, CZLONKOWIE, USTAWIENIA, LOGI, REKRUTACJE }

    public static final class Holder implements InventoryHolder {
        final Typ typ;
        final String tag;
        int strona = 0;
        boolean tylkoDostepne = false;
        Inventory inv;

        Holder(Typ typ, String tag) { this.typ = typ; this.tag = tag; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private static final int[] SLOTY = new int[28];
    static {
        int i = 0;
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 7; c++) SLOTY[i++] = r * 9 + c;
    }

    private static final Map<Integer, Double> ZMIANY_CENY = Map.of(
            19, -10000.0, 20, -1000.0, 21, -100.0, 23, 100.0, 24, 1000.0, 25, 10000.0);

    private final FirestarKlany plugin;
    private final Map<UUID, String> czekaNaKwote = new ConcurrentHashMap<>();

    public Menus(FirestarKlany plugin) { this.plugin = plugin; }

    private KlanManager m() { return plugin.manager(); }

    // ================= narzedzia =================

    private static ItemStack item(Material mat, String nazwa, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(nazwa));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(Text::c).toList());
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack glowa(UUID u, String nazwa, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(u));
        meta.displayName(Text.c(nazwa));
        meta.lore(lore.stream().map(Text::c).toList());
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack szyba(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(" "));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    /** Ramka jak na screenach: zolte rogi, brazowe "sasiedzi" rogow, jasnoszare brzegi, ciemne wnetrze. */
    private void ramka(Inventory inv, int rzedy) {
        ItemStack wnetrze = szyba(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack brzeg = szyba(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemStack rog = szyba(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack braz = szyba(Material.BROWN_STAINED_GLASS_PANE);
        for (int slot = 0; slot < rzedy * 9; slot++) {
            int r = slot / 9, c = slot % 9;
            boolean krawedz = r == 0 || r == rzedy - 1 || c == 0 || c == 8;
            if (!krawedz) { inv.setItem(slot, wnetrze); continue; }
            boolean naRogu = (r == 0 || r == rzedy - 1) && (c == 0 || c == 8);
            boolean obokRogu = ((r == 0 || r == rzedy - 1) && (c == 1 || c == 7))
                    || ((c == 0 || c == 8) && (r == 1 || r == rzedy - 2));
            inv.setItem(slot, naRogu ? rog : obokRogu ? braz : brzeg);
        }
    }

    private Holder nowy(Typ typ, String tag, int rzedy, String tytul) {
        Holder h = new Holder(typ, tag);
        h.inv = Bukkit.createInventory(h, rzedy * 9, Text.c(tytul));
        ramka(h.inv, rzedy);
        return h;
    }

    private void nawigacja(Holder h, int strona, int maxStrona) {
        if (strona > 0)
            h.inv.setItem(48, item(Material.ARROW, "&e« Poprzednia strona", "&7Strona &f" + strona + "&8/&f" + (maxStrona + 1)));
        if (strona < maxStrona)
            h.inv.setItem(50, item(Material.ARROW, "&eNastępna strona »", "&7Strona &f" + (strona + 2) + "&8/&f" + (maxStrona + 1)));
    }

    private static int maxStrona(int rozmiar) { return Math.max(0, (rozmiar - 1) / SLOTY.length); }

    private Klan klanMenu(Player p, Holder h) {
        Klan k = m().klan(h.tag);
        if (k == null || !k.czlonkowie().contains(p.getUniqueId())) { p.closeInventory(); return null; }
        return k;
    }

    private static String stanRekrutacji(Klan k) {
        return k.rekrutacjaOtwarta() ? "&aOtwarta" : "&cZamknięta";
    }

    // ================= PANEL =================

    public void otworzPanel(Player p) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) { Text.send(p, "&cNie należysz do żadnego klanu."); return; }
        UUID id = p.getUniqueId();
        boolean zarzadza = k.mozeZarzadzac(id);
        Klan.Rola rola = k.rola(id);

        Holder h = nowy(Typ.PANEL, k.tag(), 6, "&c&lPanel");
        Inventory inv = h.inv;

        inv.setItem(13, item(Material.ENDER_EYE, "&e&lKlan &6[" + k.tag() + "]",
                "&7Twoja rola: " + rola.kolor + rola.nazwa,
                "&7Właściciel: &f" + m().nazwa(k.wlasciciel()),
                "&7Członkowie: &f" + k.czlonkowie().size() + "&8/&f" + m().maxCzlonkow()));
        inv.setItem(21, item(Material.GOLD_BLOCK, "&6&lCena dołączenia",
                "&7Aktualna cena: &a" + Text.kwota(k.cena()), "",
                zarzadza ? "&eKliknij, aby zmienić" : "&8Tylko właściciel i zastępca"));
        inv.setItem(22, glowa(k.wlasciciel(), "&b&lCzłonkowie",
                List.of("&7Liczba członków: &f" + k.czlonkowie().size(), "",
                        zarzadza ? "&eKliknij, aby zarządzać" : "&eKliknij, aby zobaczyć listę")));
        if (k.czyWlasciciel(id)) {
            inv.setItem(24, item(Material.TNT, "&4&lRozwiąż klan", "&7Trwale usuwa klan.", "", "&cKliknij, aby rozwiązać"));
        } else {
            inv.setItem(24, item(Material.OAK_DOOR, "&c&lOpuść klan", "&7Opuszczasz klan &6[" + k.tag() + "]", "", "&cKliknij, aby opuścić"));
        }
        inv.setItem(31, item(Material.BOOK, "&d&lLogi klanu",
                "&7Historia działań w klanie.", "",
                zarzadza ? "&eKliknij, aby zobaczyć" : "&8Tylko właściciel i zastępca"));
        inv.setItem(32, item(Material.NETHER_STAR, "&f&lRanking klanu",
                "&7Punkty klanu: &e" + plugin.ranking().klanPunkty(k),
                "&7Miejsce w rankingu: &e#" + plugin.ranking().pozycjaKlanu(k),
                "&7Sojusznicy: &b" + (k.sojusznicy().isEmpty() ? "brak" : String.join("&7, &b", k.sojusznicy())),
                "&7Online: &a" + k.czlonkowie().stream().filter(u -> m().widoczny(u)).count(),
                "&7Założony: &f" + m().dataTekst(k.utworzony())));
        inv.setItem(40, item(Material.OAK_SIGN, "&a&lRekrutacja",
                "&7Status: " + stanRekrutacji(k),
                "&7Cena dołączenia: &a" + Text.kwota(k.cena()), "",
                zarzadza ? "&eKliknij, aby ustawić" : "&8Tylko właściciel i zastępca"));
        p.openInventory(inv);
    }

    private void klikPanel(Player p, Holder h, int slot) {
        Klan k = klanMenu(p, h);
        if (k == null) return;
        boolean zarzadza = k.mozeZarzadzac(p.getUniqueId());
        switch (slot) {
            case 21, 40 -> {
                if (zarzadza) otworzUstawienia(p);
                else Text.send(p, "&cTylko właściciel i zastępca mogą to zmieniać.");
            }
            case 22 -> otworzCzlonkowie(p, 0);
            case 31 -> {
                if (zarzadza) otworzLogi(p, 0);
                else Text.send(p, "&cTylko właściciel i zastępca widzą logi.");
            }
            case 24 -> {
                p.closeInventory();
                p.performCommand(k.czyWlasciciel(p.getUniqueId()) ? "klan usun" : "klan opusc");
            }
            default -> {}
        }
    }

    // ================= CZLONKOWIE =================

    public void otworzCzlonkowie(Player p, int strona) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) return;
        UUID ja = p.getUniqueId();
        List<UUID> lista = new ArrayList<>(k.czlonkowie());
        lista.sort(Comparator.comparingInt(u -> k.rola(u).ordinal()));
        int max = maxStrona(lista.size());
        strona = Math.max(0, Math.min(strona, max));

        Holder h = nowy(Typ.CZLONKOWIE, k.tag(), 6, "&b&lCzłonkowie klanu");
        h.strona = strona;
        for (int i = 0; i < SLOTY.length; i++) {
            int idx = strona * SLOTY.length + i;
            if (idx >= lista.size()) break;
            UUID u = lista.get(idx);
            Klan.Rola r = k.rola(u);
            List<String> lore = new ArrayList<>();
            lore.add("&7Rola: " + r.kolor + r.nazwa);
            lore.add("&7Status: " + (m().widoczny(u) ? "&aOnline" : "&cOffline"));
            if (!u.equals(ja) && !k.czyWlasciciel(u)) {
                if (k.czyWlasciciel(ja)) {
                    lore.add("");
                    lore.add("&eLPM: &7" + (k.czyZastepca(u) ? "odbierz rangę zastępcy" : "mianuj zastępcą"));
                    lore.add("&cPPM: &7wyrzuć z klanu");
                } else if (k.czyZastepca(ja) && !k.czyZastepca(u)) {
                    lore.add("");
                    lore.add("&cPPM: &7wyrzuć z klanu");
                }
            }
            h.inv.setItem(SLOTY[i], glowa(u, r.kolor + m().nazwa(u), lore));
        }
        nawigacja(h, strona, max);
        h.inv.setItem(49, item(Material.ARROW, "&cPowrót do panelu"));
        p.openInventory(h.inv);
    }

    private void klikCzlonkowie(Player p, Holder h, int slot, ClickType klik) {
        Klan k = klanMenu(p, h);
        if (k == null) return;
        if (slot == 49) { otworzPanel(p); return; }
        if (slot == 48 && h.inv.getItem(48) != null && h.inv.getItem(48).getType() == Material.ARROW) { otworzCzlonkowie(p, h.strona - 1); return; }
        if (slot == 50 && h.inv.getItem(50) != null && h.inv.getItem(50).getType() == Material.ARROW) { otworzCzlonkowie(p, h.strona + 1); return; }

        int pozycja = Arrays.binarySearch(SLOTY, slot);
        if (pozycja < 0) return;
        List<UUID> lista = new ArrayList<>(k.czlonkowie());
        lista.sort(Comparator.comparingInt(u -> k.rola(u).ordinal()));
        int idx = h.strona * SLOTY.length + pozycja;
        if (idx >= lista.size()) return;

        UUID cel = lista.get(idx);
        UUID ja = p.getUniqueId();
        if (cel.equals(ja) || k.czyWlasciciel(cel)) return;

        if (klik.isLeftClick() && k.czyWlasciciel(ja)) {
            if (k.czyZastepca(cel)) {
                k.zastepcy().remove(cel);
                m().log(k, p.getName() + " odebrał rangę zastępcy graczowi " + m().nazwa(cel));
            } else {
                k.zastepcy().add(cel);
                m().log(k, p.getName() + " mianował " + m().nazwa(cel) + " zastępcą");
            }
            m().oznacz();
            otworzCzlonkowie(p, h.strona);
        } else if (klik.isRightClick()) {
            boolean moze = k.czyWlasciciel(ja) || (k.czyZastepca(ja) && !k.czyZastepca(cel));
            if (!moze) { Text.send(p, "&cNie możesz wyrzucić tego gracza."); return; }
            m().wyrzuc(k, cel, p.getName());
            otworzCzlonkowie(p, h.strona);
        }
    }

    // ================= USTAWIENIA REKRUTACJI =================

    public void otworzUstawienia(Player p) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) return;
        if (!k.mozeZarzadzac(p.getUniqueId())) { Text.send(p, "&cBrak uprawnień."); return; }
        double min = plugin.getConfig().getDouble("rekrutacja.min-cena", 0);
        double max = plugin.getConfig().getDouble("rekrutacja.max-cena", 100000);

        Holder h = nowy(Typ.USTAWIENIA, k.tag(), 4, "&6&lRekrutacja klanu");
        Inventory inv = h.inv;
        if (k.rekrutacjaOtwarta()) {
            inv.setItem(11, item(Material.LIME_DYE, "&a&lRekrutacja: OTWARTA",
                    "&7Gracze mogą dołączać przez", "&7&n/klan rekrutacje", "", "&eKliknij, aby zamknąć"));
        } else {
            inv.setItem(11, item(Material.RED_DYE, "&c&lRekrutacja: ZAMKNIĘTA",
                    "&7Nikt nie może dołączyć", "&7przez rekrutację.", "", "&eKliknij, aby otworzyć"));
        }
        inv.setItem(13, item(Material.OAK_SIGN, "&f&lWymagania",
                "&7Status: " + stanRekrutacji(k),
                "&7Cena dołączenia: &a" + Text.kwota(k.cena()),
                "&7Wolne miejsca: &f" + Math.max(0, m().maxCzlonkow() - k.czlonkowie().size())));
        inv.setItem(15, item(Material.GOLD_INGOT, "&6&lWpisz kwotę",
                "&7Zakres: &f" + Text.kwota(min) + " &7- &f" + Text.kwota(max),
                "&7Opłata trafia do właściciela klanu.", "", "&eKliknij i wpisz kwotę na czacie"));

        inv.setItem(19, item(Material.RED_CONCRETE, "&c-10000$"));
        inv.setItem(20, item(Material.ORANGE_CONCRETE, "&6-1000$"));
        inv.setItem(21, item(Material.YELLOW_CONCRETE, "&e-100$"));
        inv.setItem(22, item(Material.BARRIER, "&7Ustaw 0$", "&7Darmowe dołączenie"));
        inv.setItem(23, item(Material.LIME_CONCRETE, "&a+100$"));
        inv.setItem(24, item(Material.GREEN_CONCRETE, "&2+1000$"));
        inv.setItem(25, item(Material.CYAN_CONCRETE, "&3+10000$"));
        inv.setItem(31, item(Material.ARROW, "&cPowrót do panelu"));
        p.openInventory(inv);
    }

    private void ustawCene(Player p, Klan k, double v) {
        double min = plugin.getConfig().getDouble("rekrutacja.min-cena", 0);
        double max = plugin.getConfig().getDouble("rekrutacja.max-cena", 100000);
        v = Math.round(Math.max(min, Math.min(max, v)) * 100.0) / 100.0;
        if (v == k.cena()) return;
        k.setCena(v);
        m().log(k, p.getName() + " ustawił cenę rekrutacji na " + Text.kwota(v));
        m().oznacz();
    }

    private void klikUstawienia(Player p, Holder h, int slot) {
        Klan k = klanMenu(p, h);
        if (k == null) return;
        if (!k.mozeZarzadzac(p.getUniqueId())) { p.closeInventory(); return; }

        if (slot == 31) { otworzPanel(p); return; }
        if (slot == 11) {
            k.setRekrutacja(!k.rekrutacjaOtwarta());
            m().log(k, p.getName() + (k.rekrutacjaOtwarta() ? " otworzył" : " zamknął") + " rekrutację");
            m().oznacz();
            otworzUstawienia(p);
            return;
        }
        if (slot == 15) {
            double min = plugin.getConfig().getDouble("rekrutacja.min-cena", 0);
            double max = plugin.getConfig().getDouble("rekrutacja.max-cena", 100000);
            czekaNaKwote.put(p.getUniqueId(), k.tag());
            p.closeInventory();
            Text.send(p, "&7Wpisz na czacie cenę dołączenia (&f" + Text.kwota(min) + " &7- &f" + Text.kwota(max) + "&7) albo &canuluj&7.");
            return;
        }
        if (slot == 22) { ustawCene(p, k, 0); otworzUstawienia(p); return; }
        Double zmiana = ZMIANY_CENY.get(slot);
        if (zmiana != null) {
            ustawCene(p, k, k.cena() + zmiana);
            otworzUstawienia(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!czekaNaKwote.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String tag = czekaNaKwote.remove(p.getUniqueId());
        String tekst = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Klan k = m().klan(tag);
            if (k == null || !k.mozeZarzadzac(p.getUniqueId())) return;
            if (tekst.equalsIgnoreCase("anuluj")) { Text.send(p, "&7Anulowano."); otworzUstawienia(p); return; }
            double v;
            try {
                v = Double.parseDouble(tekst.replace(",", ".").replace("$", ""));
            } catch (NumberFormatException ex) {
                Text.send(p, "&cTo nie jest poprawna kwota.");
                otworzUstawienia(p);
                return;
            }
            double min = plugin.getConfig().getDouble("rekrutacja.min-cena", 0);
            double max = plugin.getConfig().getDouble("rekrutacja.max-cena", 100000);
            if (!Double.isFinite(v) || v < min || v > max) {
                Text.send(p, "&cKwota musi być w zakresie &e" + Text.kwota(min) + " - " + Text.kwota(max) + "&c.");
                otworzUstawienia(p);
                return;
            }
            ustawCene(p, k, v);
            Text.send(p, "&aUstawiono cenę dołączenia na &e" + Text.kwota(k.cena()) + "&a.");
            otworzUstawienia(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { czekaNaKwote.remove(e.getPlayer().getUniqueId()); }

    // ================= LOGI =================

    public void otworzLogi(Player p, int strona) {
        Klan k = m().klanGracza(p.getUniqueId());
        if (k == null) return;
        if (!k.mozeZarzadzac(p.getUniqueId())) { Text.send(p, "&cBrak uprawnień."); return; }
        List<String> lista = new ArrayList<>(k.logi());
        int max = maxStrona(lista.size());
        strona = Math.max(0, Math.min(strona, max));

        Holder h = nowy(Typ.LOGI, k.tag(), 6, "&d&lLogi klanu");
        h.strona = strona;
        for (int i = 0; i < SLOTY.length; i++) {
            int idx = strona * SLOTY.length + i;
            if (idx >= lista.size()) break;
            String wpis = lista.get(idx);
            int kreska = wpis.indexOf('|');
            String data = kreska > 0 ? wpis.substring(0, kreska) : "";
            String tresc = kreska > 0 ? wpis.substring(kreska + 1) : wpis;
            h.inv.setItem(SLOTY[i], item(Material.PAPER, "&e" + data, "&f" + tresc));
        }
        if (lista.isEmpty()) h.inv.setItem(22, item(Material.BARRIER, "&7Brak wpisów"));
        nawigacja(h, strona, max);
        h.inv.setItem(49, item(Material.ARROW, "&cPowrót do panelu"));
        p.openInventory(h.inv);
    }

    private void klikLogi(Player p, Holder h, int slot) {
        Klan k = klanMenu(p, h);
        if (k == null) return;
        if (slot == 49) { otworzPanel(p); return; }
        if (slot == 48 && h.inv.getItem(48) != null && h.inv.getItem(48).getType() == Material.ARROW) otworzLogi(p, h.strona - 1);
        if (slot == 50 && h.inv.getItem(50) != null && h.inv.getItem(50).getType() == Material.ARROW) otworzLogi(p, h.strona + 1);
    }

    // ================= REKRUTACJE =================

    private List<Klan> listaRekrutacji(Player p, boolean tylkoDostepne) {
        boolean maKlan = m().klanGracza(p.getUniqueId()) != null;
        List<Klan> out = new ArrayList<>();
        for (Klan k : m().wszystkie()) {
            if (!k.rekrutacjaOtwarta()) continue;
            if (tylkoDostepne) {
                if (maKlan || k.czlonkowie().size() >= m().maxCzlonkow()) continue;
                if (k.cena() > 0) {
                    var eco = plugin.eco();
                    if (eco == null || !eco.has(p, k.cena())) continue;
                }
            }
            out.add(k);
        }
        out.sort(Comparator.comparingDouble(Klan::cena).thenComparing(k -> -k.czlonkowie().size()));
        return out;
    }

    public void otworzRekrutacje(Player p, int strona, boolean tylkoDostepne) {
        List<Klan> lista = listaRekrutacji(p, tylkoDostepne);
        int max = maxStrona(lista.size());
        strona = Math.max(0, Math.min(strona, max));

        Holder h = nowy(Typ.REKRUTACJE, "", 6, "&6&lOtwarte rekrutacje");
        h.strona = strona;
        h.tylkoDostepne = tylkoDostepne;

        boolean maKlan = m().klanGracza(p.getUniqueId()) != null;
        var eco = plugin.eco();
        for (int i = 0; i < SLOTY.length; i++) {
            int idx = strona * SLOTY.length + i;
            if (idx >= lista.size()) break;
            Klan k = lista.get(idx);
            boolean stac = k.cena() <= 0 || (eco != null && eco.has(p, k.cena()));
            List<String> lore = new ArrayList<>();
            lore.add("&7Właściciel: &f" + m().nazwa(k.wlasciciel()));
            lore.add("&7Członkowie: &f" + k.czlonkowie().size() + "&8/&f" + m().maxCzlonkow());
            lore.add("&7Cena dołączenia: " + (stac ? "&a" : "&c") + Text.kwota(k.cena()));
            lore.add("");
            if (maKlan) lore.add("&cJesteś już w klanie.");
            else if (k.czlonkowie().size() >= m().maxCzlonkow()) lore.add("&cKlan jest pełny.");
            else if (!stac) lore.add("&cNie stać Cię na dołączenie.");
            else lore.add("&aKliknij, aby dołączyć");
            h.inv.setItem(SLOTY[i], glowa(k.wlasciciel(), "&e&l[" + k.tag() + "]", lore));
        }
        if (lista.isEmpty()) h.inv.setItem(22, item(Material.BARRIER, "&7Brak klanów do wyświetlenia",
                tylkoDostepne ? "&7Spróbuj filtra &fWszystkie&7." : "&7Żaden klan nie ma otwartej rekrutacji."));

        h.inv.setItem(49, item(Material.HOPPER, "&6Filtrowanie &7(Kliknij)",
                (tylkoDostepne ? "&c" : "&a&n") + "✕ Wszystkie",
                (tylkoDostepne ? "&a&n" : "&c") + "✕ Dostępne dla Ciebie"));
        nawigacja(h, strona, max);
        p.openInventory(h.inv);
    }

    private void klikRekrutacje(Player p, Holder h, int slot) {
        if (slot == 49) { otworzRekrutacje(p, 0, !h.tylkoDostepne); return; }
        if (slot == 48 && h.inv.getItem(48) != null && h.inv.getItem(48).getType() == Material.ARROW) { otworzRekrutacje(p, h.strona - 1, h.tylkoDostepne); return; }
        if (slot == 50 && h.inv.getItem(50) != null && h.inv.getItem(50).getType() == Material.ARROW) { otworzRekrutacje(p, h.strona + 1, h.tylkoDostepne); return; }

        int pozycja = Arrays.binarySearch(SLOTY, slot);
        if (pozycja < 0) return;
        List<Klan> lista = listaRekrutacji(p, h.tylkoDostepne);
        int idx = h.strona * SLOTY.length + pozycja;
        if (idx >= lista.size()) return;

        Klan k = lista.get(idx);
        p.closeInventory();
        String err = m().dolaczRekrutacja(p, k);
        if (err != null) Text.send(p, err);
        else Text.send(p, "&aDołączyłeś do klanu &6[" + k.tag() + "]&a!");
    }

    // ================= zdarzenia GUI =================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        int slot = e.getSlot();
        ClickType klik = e.getClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (h.typ) {
                case PANEL -> klikPanel(p, h, slot);
                case CZLONKOWIE -> klikCzlonkowie(p, h, slot, klik);
                case USTAWIENIA -> klikUstawienia(p, h, slot);
                case LOGI -> klikLogi(p, h, slot);
                case REKRUTACJE -> klikRekrutacje(p, h, slot);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
