package pl.firestar.klany;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Ranking graczy (system ELO) i klanow (suma punktow czlonkow).
 * Start: 1000 pkt. Przy rownych rankingach zabojstwo = ok. 16 pkt (K=32).
 */
public class RankingManager {

    public static final class Stat {
        public String nick = "?";
        public int punkty;
        public int zabojstwa;
        public int smierci;
        Stat(int punkty) { this.punkty = punkty; }
    }

    public static final class Wynik {
        public int zysk;
        public int strata;
        public String powod; // null = punkty przyznane
    }

    private final FirestarKlany plugin;
    private final File plik;
    private final Map<UUID, Stat> staty = new HashMap<>();
    private final Map<String, Long> cooldowny = new HashMap<>();
    private boolean brudne = false;

    private boolean cacheStare = true;
    private List<UUID> topGracze = List.of();
    private List<Klan> topKlany = List.of();
    private Map<Klan, Integer> klanPkt = new HashMap<>();

    public RankingManager(FirestarKlany plugin) {
        this.plugin = plugin;
        this.plik = new File(plugin.getDataFolder(), "gracze.yml");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }
    private int start() { return cfg().getInt("ranking.punkty-startowe", 1000); }

    // ---------- zapis / odczyt ----------

    public void wczytaj() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(plik);
        ConfigurationSection sec = y.getConfigurationSection("gracze");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(key);
            if (c == null) continue;
            try {
                UUID u = UUID.fromString(key);
                Stat s = new Stat(c.getInt("punkty", start()));
                s.nick = c.getString("nick", "?");
                s.zabojstwa = c.getInt("zabojstwa", 0);
                s.smierci = c.getInt("smierci", 0);
                staty.put(u, s);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void zapisz() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Stat> e : staty.entrySet()) {
            String s = "gracze." + e.getKey();
            y.set(s + ".nick", e.getValue().nick);
            y.set(s + ".punkty", e.getValue().punkty);
            y.set(s + ".zabojstwa", e.getValue().zabojstwa);
            y.set(s + ".smierci", e.getValue().smierci);
        }
        try {
            y.save(plik);
            brudne = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udalo sie zapisac gracze.yml: " + ex.getMessage());
        }
    }

    public void zapiszJesliTrzeba() { if (brudne) zapisz(); }

    public void zmiana() { cacheStare = true; }

    // ---------- statystyki ----------

    public Stat stat(Player p) {
        Stat s = staty.get(p.getUniqueId());
        if (s == null) {
            s = new Stat(start());
            staty.put(p.getUniqueId(), s);
            brudne = true;
            zmiana();
        }
        if (!p.getName().equals(s.nick)) { s.nick = p.getName(); brudne = true; }
        return s;
    }

    public Stat stat(UUID u) { return staty.get(u); }

    public int punkty(UUID u) {
        Stat s = staty.get(u);
        return s == null ? start() : s.punkty;
    }

    public void rejestruj(Player p) { stat(p); }

    // ---------- inteligentne liczenie punktow (ELO) ----------

    public Wynik przetworz(Player z, Player o) {
        Wynik w = new Wynik();
        KlanManager m = plugin.manager();
        Klan kz = m.klanGracza(z.getUniqueId());
        Klan ko = m.klanGracza(o.getUniqueId());

        if (kz != null && kz == ko) { w.powod = "ten sam klan"; return w; }
        if (m.czySojusznicy(kz, ko)) { w.powod = "sojusznik"; return w; }
        if (cfg().getBoolean("ranking.blokuj-to-samo-ip", true)
                && z.getAddress() != null && o.getAddress() != null
                && z.getAddress().getAddress().equals(o.getAddress().getAddress())) {
            w.powod = "to samo IP";
            return w;
        }

        long teraz = System.currentTimeMillis();
        long cd = cfg().getLong("ranking.cooldown-ta-sama-ofiara-sekundy", 300) * 1000L;
        String klucz = z.getUniqueId() + ":" + o.getUniqueId();
        Long ostatnio = cooldowny.get(klucz);
        if (ostatnio != null && teraz - ostatnio < cd) { w.powod = "ta sama ofiara (cooldown)"; return w; }
        cooldowny.put(klucz, teraz);
        if (cooldowny.size() > 5000) cooldowny.values().removeIf(t -> teraz - t >= cd);

        Stat sz = stat(z);
        Stat so = stat(o);
        double k = cfg().getDouble("ranking.k", 32);
        double oczekiwane = 1.0 / (1.0 + Math.pow(10.0, (so.punkty - sz.punkty) / 400.0));
        int delta = (int) Math.round(k * (1.0 - oczekiwane));
        int min = cfg().getInt("ranking.min-zysk", 1);
        int max = cfg().getInt("ranking.max-zysk", (int) k);
        delta = Math.max(min, Math.min(max, delta));
        delta = Math.min(delta, so.punkty); // ofiara nie spada ponizej 0

        sz.punkty += delta;
        so.punkty -= delta;
        sz.zabojstwa++;
        so.smierci++;
        w.zysk = delta;
        w.strata = delta;
        brudne = true;
        zmiana();
        return w;
    }

    public void ogloszZabicie(Player z, Player o, Wynik w) {
        KlanManager m = plugin.manager();
        Klan kz = m.klanGracza(z.getUniqueId());
        Klan ko = m.klanGracza(o.getUniqueId());
        for (Player v : Bukkit.getOnlinePlayers()) {
            Klan kv = m.klanGracza(v.getUniqueId());
            String msg = "&4☠ " + m.znacznik(kv, ko) + "&7" + o.getName() + " &c(-" + w.strata + ") "
                    + "&7został zabity przez " + m.znacznik(kv, kz) + "&f" + z.getName() + " &a(+" + w.zysk + ")";
            v.sendMessage(Text.c(msg));
        }
    }

    // ---------- rankingi (cache) ----------

    private void odswiezCache() {
        if (!cacheStare) return;
        List<Map.Entry<UUID, Stat>> lista = new ArrayList<>(staty.entrySet());
        lista.sort((a, b) -> {
            int c = Integer.compare(b.getValue().punkty, a.getValue().punkty);
            if (c != 0) return c;
            c = Integer.compare(b.getValue().zabojstwa, a.getValue().zabojstwa);
            if (c != 0) return c;
            return a.getValue().nick.compareToIgnoreCase(b.getValue().nick);
        });
        List<UUID> g = new ArrayList<>();
        for (Map.Entry<UUID, Stat> e : lista) g.add(e.getKey());
        topGracze = g;

        Map<Klan, Integer> pkt = new HashMap<>();
        List<Klan> kl = new ArrayList<>(plugin.manager().wszystkie());
        for (Klan k : kl) {
            int suma = 0;
            for (UUID u : k.czlonkowie()) suma += punkty(u);
            pkt.put(k, suma);
        }
        kl.sort((a, b) -> {
            int c = Integer.compare(pkt.get(b), pkt.get(a));
            return c != 0 ? c : a.tag().compareTo(b.tag());
        });
        topKlany = kl;
        klanPkt = pkt;
        cacheStare = false;
    }

    public List<UUID> topGracze() { odswiezCache(); return topGracze; }
    public List<Klan> topKlany() { odswiezCache(); return topKlany; }

    public int pozycjaGracza(UUID u) {
        odswiezCache();
        int i = topGracze.indexOf(u);
        return i < 0 ? 0 : i + 1;
    }

    public int klanPunkty(Klan k) {
        odswiezCache();
        Integer p = klanPkt.get(k);
        return p == null ? 0 : p;
    }

    public int pozycjaKlanu(Klan k) {
        odswiezCache();
        int i = topKlany.indexOf(k);
        return i < 0 ? 0 : i + 1;
    }
}
