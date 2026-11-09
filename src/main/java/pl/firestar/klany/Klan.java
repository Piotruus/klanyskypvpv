package pl.firestar.klany;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

public class Klan {

    public enum Rola {
        WLASCICIEL("Właściciel", "&c"),
        ZASTEPCA("Zastępca", "&6"),
        CZLONEK("Członek", "&7");

        public final String nazwa;
        public final String kolor;

        Rola(String nazwa, String kolor) {
            this.nazwa = nazwa;
            this.kolor = kolor;
        }
    }

    private final String tag;
    private final UUID wlasciciel;
    private final long utworzony;
    private final Set<UUID> czlonkowie = new LinkedHashSet<>();
    private final Set<UUID> zastepcy = new LinkedHashSet<>();
    private final LinkedList<String> logi = new LinkedList<>();
    private final Set<String> sojusznicy = new LinkedHashSet<>();
    private boolean rekrutacja = false;
    private double cena = 0;

    public Klan(String tag, UUID wlasciciel, long utworzony) {
        this.tag = tag;
        this.wlasciciel = wlasciciel;
        this.utworzony = utworzony;
        this.czlonkowie.add(wlasciciel);
    }

    public String tag() { return tag; }
    public UUID wlasciciel() { return wlasciciel; }
    public long utworzony() { return utworzony; }
    public Set<UUID> czlonkowie() { return czlonkowie; }
    public Set<UUID> zastepcy() { return zastepcy; }
    public LinkedList<String> logi() { return logi; }
    public Set<String> sojusznicy() { return sojusznicy; }
    public boolean rekrutacjaOtwarta() { return rekrutacja; }
    public void setRekrutacja(boolean b) { this.rekrutacja = b; }
    public double cena() { return cena; }
    public void setCena(double c) { this.cena = c; }

    public boolean czyWlasciciel(UUID u) { return wlasciciel.equals(u); }
    public boolean czyZastepca(UUID u) { return zastepcy.contains(u); }
    public boolean mozeZarzadzac(UUID u) { return czyWlasciciel(u) || czyZastepca(u); }

    public Rola rola(UUID u) {
        if (czyWlasciciel(u)) return Rola.WLASCICIEL;
        if (czyZastepca(u)) return Rola.ZASTEPCA;
        return Rola.CZLONEK;
    }
}
