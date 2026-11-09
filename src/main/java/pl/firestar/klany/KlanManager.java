package pl.firestar.klany;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class KlanManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final FirestarKlany plugin;
    private final File plik;
    private final Map<String, Klan> klany = new LinkedHashMap<>();
    private final Map<UUID, String> gracze = new HashMap<>();
    private final Map<UUID, Map<String, Long>> zaproszenia = new HashMap<>();
    private final Map<String, Map<String, Long>> proszeSojusz = new HashMap<>(); // tag odbiorcy -> (tag nadawcy -> wygasa)
    private boolean brudne = false;

    public KlanManager(FirestarKlany plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "klany.yml");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    // ---------- zapis / odczyt ----------

    public void wczytaj() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(plik);
        ConfigurationSection sec = y.getConfigurationSection("klany");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(key);
            if (c == null) continue;
            try {
                String tag = c.getString("tag", key.replaceFirst("^k_", ""));
                UUID wlasciciel = UUID.fromString(c.getString("wlasciciel", ""));
                Klan k = new Klan(tag, wlasciciel, c.getLong("utworzony", System.currentTimeMillis()));
                for (String s : c.getStringList("czlonkowie")) k.czlonkowie().add(UUID.fromString(s));
                for (String s : c.getStringList("zastepcy")) k.zastepcy().add(UUID.fromString(s));
                k.setRekrutacja(c.getBoolean("rekrutacja.otwarta", false));
                k.setCena(c.getDouble("rekrutacja.cena", 0));
                k.logi().addAll(c.getStringList("logi"));
                k.sojusznicy().addAll(c.getStringList("sojusznicy"));
                klany.put(tag, k);
                for (UUID u : k.czlonkowie()) gracze.put(u, tag);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Pominieto uszkodzony wpis klanu: " + key);
            }
        }
    }

    public void zapisz() {
        YamlConfiguration y = new YamlConfiguration();
        for (Klan k : klany.values()) {
            String s = "klany.k_" + k.tag();
            y.set(s + ".tag", k.tag());
            y.set(s + ".wlasciciel", k.wlasciciel().toString());
            y.set(s + ".utworzony", k.utworzony());
            y.set(s + ".czlonkowie", k.czlonkowie().stream().map(UUID::toString).toList());
            y.set(s + ".zastepcy", k.zastepcy().stream().map(UUID::toString).toList());
            y.set(s + ".rekrutacja.otwarta", k.rekrutacjaOtwarta());
            y.set(s + ".rekrutacja.cena", k.cena());
            y.set(s + ".logi", new ArrayList<>(k.logi()));
            y.set(s + ".sojusznicy", new ArrayList<>(k.sojusznicy()));
        }
        try {
            y.save(plik);
            brudne = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udalo sie zapisac klany.yml: " + ex.getMessage());
        }
    }

    public void oznacz() {
        brudne = true;
        if (plugin.ranking() != null) plugin.ranking().zmiana();
    }
    public void zapiszJesliTrzeba() { if (brudne) zapisz(); }

    // ---------- gettery ----------

    public Klan klan(String tag) {
        return tag == null ? null : klany.get(tag.toUpperCase(Locale.ROOT));
    }

    public Klan klanGracza(UUID u) {
        String tag = gracze.get(u);
        return tag == null ? null : klany.get(tag);
    }

    public Collection<Klan> wszystkie() { return klany.values(); }

    public int maxCzlonkow() { return cfg().getInt("klan.max-czlonkow", 20); }

    public String nazwa(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() != null ? op.getName() : "nieznany";
    }

    /** Online i nie w vanishu (Essentials ustawia metadane "vanished"). */
    public boolean widoczny(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p == null) return false;
        for (MetadataValue v : p.getMetadata("vanished")) {
            if (v.asBoolean()) return false;
        }
        return true;
    }

    public String dataTekst(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(FMT);
    }

    // ---------- akcje ----------

    /** @return komunikat bledu albo null gdy sukces */
    public String stworz(Player p, String surowyTag) {
        if (klanGracza(p.getUniqueId()) != null) return "&cJesteś już w klanie. Najpierw go opuść.";
        String tag = surowyTag.toUpperCase(Locale.ROOT);
        int min = cfg().getInt("klan.min-tag", 2);
        int max = cfg().getInt("klan.max-tag", 5);
        if (!tag.matches("[A-Z0-9]{" + min + "," + max + "}"))
            return "&cTag musi mieć &e" + min + "-" + max + " &cznaków (litery A-Z i cyfry).";
        for (String z : cfg().getStringList("klan.zablokowane-tagi"))
            if (z.equalsIgnoreCase(tag)) return "&cTen tag jest zablokowany.";
        if (klany.containsKey(tag)) return "&cKlan o takim tagu już istnieje.";

        double koszt = cfg().getDouble("klan.koszt-zalozenia", 0);
        if (koszt > 0) {
            Economy eco = plugin.eco();
            if (eco == null) return "&cBrak ekonomii (Vault) - zgłoś to administracji.";
            if (!eco.has(p, koszt)) return "&cZałożenie klanu kosztuje &e" + Text.kwota(koszt) + "&c.";
            if (!eco.withdrawPlayer(p, koszt).transactionSuccess()) return "&cNie udało się pobrać opłaty.";
        }

        Klan k = new Klan(tag, p.getUniqueId(), System.currentTimeMillis());
        klany.put(tag, k);
        gracze.put(p.getUniqueId(), tag);
        log(k, p.getName() + " założył klan");
        plugin.odswiezNametagi();
        return null;
    }

    public void dodaj(Klan k, UUID u) {
        k.czlonkowie().add(u);
        gracze.put(u, k.tag());
        zaproszenia.remove(u);
        oznacz();
        plugin.odswiezNametagi();
    }

    public void usun(Klan k, UUID u) {
        k.czlonkowie().remove(u);
        k.zastepcy().remove(u);
        gracze.remove(u);
        oznacz();
        plugin.odswiezNametagi();
    }

    public void rozwiaz(Klan k) {
        for (UUID u : new ArrayList<>(k.czlonkowie())) gracze.remove(u);
        klany.remove(k.tag());
        for (Klan inny : klany.values()) inny.sojusznicy().remove(k.tag());
        proszeSojusz.remove(k.tag());
        for (Map<String, Long> mapa : proszeSojusz.values()) mapa.remove(k.tag());
        oznacz();
        plugin.odswiezNametagi();
    }

    public void wyrzuc(Klan k, UUID cel, String przez) {
        String nick = nazwa(cel);
        usun(k, cel);
        log(k, nick + " został wyrzucony przez " + przez);
        Player t = Bukkit.getPlayer(cel);
        if (t != null) Text.send(t, "&cZostałeś wyrzucony z klanu &6[" + k.tag() + "]&c.");
        wyslij(k, "&e" + nick + " &7został wyrzucony z klanu przez &e" + przez + "&7.");
    }

    public void log(Klan k, String tekst) {
        k.logi().addFirst(dataTekst(System.currentTimeMillis()) + "|" + tekst);
        int max = cfg().getInt("klan.max-logow", 200);
        while (k.logi().size() > max) k.logi().removeLast();
        oznacz();
    }

    public void wyslij(Klan k, String msg) {
        for (UUID u : k.czlonkowie()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) Text.send(p, msg);
        }
    }

    // ---------- zaproszenia ----------

    public void zapros(Klan k, UUID cel) {
        long wygasa = System.currentTimeMillis() + cfg().getInt("klan.zaproszenie-wygasa-sekundy", 60) * 1000L;
        zaproszenia.computeIfAbsent(cel, x -> new HashMap<>()).put(k.tag(), wygasa);
    }

    public boolean maZaproszenie(UUID u, String tag) {
        Map<String, Long> mapa = zaproszenia.get(u);
        if (mapa == null) return false;
        Long t = mapa.get(tag);
        if (t == null) return false;
        if (t < System.currentTimeMillis()) { mapa.remove(tag); return false; }
        return true;
    }

    public String dolaczZaproszenie(Player p, String tagRaw) {
        Klan k = klan(tagRaw);
        if (k == null) return "&cNie ma takiego klanu.";
        if (klanGracza(p.getUniqueId()) != null) return "&cJesteś już w klanie.";
        if (!maZaproszenie(p.getUniqueId(), k.tag())) return "&cNie masz zaproszenia do tego klanu (albo wygasło).";
        if (k.czlonkowie().size() >= maxCzlonkow()) return "&cKlan jest pełny.";
        dodaj(k, p.getUniqueId());
        log(k, p.getName() + " dołączył do klanu (zaproszenie)");
        wyslij(k, "&e" + p.getName() + " &7dołączył do klanu!");
        return null;
    }

    /** Dolaczenie przez /klan rekrutacje - oplata trafia do wlasciciela klanu. */
    public String dolaczRekrutacja(Player p, Klan k) {
        if (klanGracza(p.getUniqueId()) != null) return "&cJesteś już w klanie.";
        if (!k.rekrutacjaOtwarta()) return "&cRekrutacja w tym klanie jest zamknięta.";
        if (k.czlonkowie().size() >= maxCzlonkow()) return "&cKlan jest pełny.";

        double cena = k.cena();
        if (cena > 0) {
            Economy eco = plugin.eco();
            if (eco == null) return "&cBrak ekonomii (Vault) - zgłoś to administracji.";
            if (!eco.has(p, cena)) return "&cNie stać Cię. Potrzebujesz &e" + Text.kwota(cena) + "&c.";
            if (!eco.withdrawPlayer(p, cena).transactionSuccess()) return "&cNie udało się pobrać opłaty.";
            OfflinePlayer wlasciciel = Bukkit.getOfflinePlayer(k.wlasciciel());
            if (!eco.depositPlayer(wlasciciel, cena).transactionSuccess()) {
                eco.depositPlayer(p, cena); // zwrot
                return "&cNie udało się przelać opłaty właścicielowi. Pieniądze zwrócone.";
            }
            Player w = Bukkit.getPlayer(k.wlasciciel());
            if (w != null) Text.send(w, "&a+" + Text.kwota(cena) + " &7za dołączenie gracza &e" + p.getName() + "&7.");
        }

        dodaj(k, p.getUniqueId());
        log(k, p.getName() + " dołączył do klanu (rekrutacja" + (cena > 0 ? ", opłata " + Text.kwota(cena) : "") + ")");
        wyslij(k, "&e" + p.getName() + " &7dołączył do klanu!");
        return null;
    }

    // ---------- sojusze ----------

    public boolean czySojusznicy(Klan a, Klan b) {
        return a != null && b != null && a != b && a.sojusznicy().contains(b.tag());
    }

    /** Kolorowy znacznik [TAG] z perspektywy widza: zielony - jego klan, aqua - sojusznik, czerwony - reszta. */
    public String znacznik(Klan widz, Klan cel) {
        if (cel == null) return "";
        String kol = (widz != null && widz == cel) ? "&a" : czySojusznicy(widz, cel) ? "&b" : "&c";
        return kol + "[" + cel.tag() + "] ";
    }

    private int maxSojuszy() { return cfg().getInt("sojusz.max-sojusznikow", 3); }

    public String zaproponujSojusz(Klan od, Klan doK) {
        if (od == doK) return "&cNie możesz zawrzeć sojuszu z własnym klanem.";
        if (od.sojusznicy().contains(doK.tag())) return "&cMacie już sojusz z tym klanem.";
        if (od.sojusznicy().size() >= maxSojuszy()) return "&cTwój klan ma już maksymalną liczbę sojuszy (&e" + maxSojuszy() + "&c).";
        if (doK.sojusznicy().size() >= maxSojuszy()) return "&cTen klan ma już maksymalną liczbę sojuszy.";
        long wygasa = System.currentTimeMillis() + cfg().getInt("sojusz.zaproszenie-wygasa-sekundy", 120) * 1000L;
        proszeSojusz.computeIfAbsent(doK.tag(), x -> new HashMap<>()).put(od.tag(), wygasa);
        return null;
    }

    public String akceptujSojusz(Klan odbiorca, String tagNadawcy) {
        Klan nadawca = klan(tagNadawcy);
        if (nadawca == null) return "&cNie ma takiego klanu.";
        Map<String, Long> mapa = proszeSojusz.get(odbiorca.tag());
        Long t = mapa == null ? null : mapa.get(nadawca.tag());
        if (t == null || t < System.currentTimeMillis()) {
            if (mapa != null) mapa.remove(nadawca.tag());
            return "&cBrak oczekującej prośby o sojusz od tego klanu (albo wygasła).";
        }
        if (odbiorca.sojusznicy().size() >= maxSojuszy() || nadawca.sojusznicy().size() >= maxSojuszy())
            return "&cKtóryś z klanów ma już maksymalną liczbę sojuszy.";
        mapa.remove(nadawca.tag());
        odbiorca.sojusznicy().add(nadawca.tag());
        nadawca.sojusznicy().add(odbiorca.tag());
        log(odbiorca, "Zawarto sojusz z klanem [" + nadawca.tag() + "]");
        log(nadawca, "Zawarto sojusz z klanem [" + odbiorca.tag() + "]");
        plugin.odswiezNametagi();
        return null;
    }

    public String zerwijSojusz(Klan a, String tagB) {
        Klan b = klan(tagB);
        if (b == null || !a.sojusznicy().contains(b.tag())) return "&cNie macie sojuszu z takim klanem.";
        a.sojusznicy().remove(b.tag());
        b.sojusznicy().remove(a.tag());
        log(a, "Zerwano sojusz z klanem [" + b.tag() + "]");
        log(b, "Klan [" + a.tag() + "] zerwał sojusz");
        plugin.odswiezNametagi();
        return null;
    }
}
