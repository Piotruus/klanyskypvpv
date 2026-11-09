package pl.firestar.klany;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public final class FirestarKlany extends JavaPlugin {

    private KlanManager manager;
    private Menus menus;
    private RankingManager ranking;
    private NametagManager nametagi;
    private Economy eco;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Text.setPrefix(getConfig().getString("prefix", "&6&lKLANY &8» &7"));

        ranking = new RankingManager(this);
        manager = new KlanManager(this);
        ranking.wczytaj();
        manager.wczytaj();

        menus = new Menus(this);
        getServer().getPluginManager().registerEvents(menus, this);

        getServer().getPluginManager().registerEvents(new RankingListener(this), this);
        getCommand("ranking").setExecutor(new RankingCommand(this));

        nametagi = new NametagManager(this);
        getServer().getPluginManager().registerEvents(nametagi, this);

        KlanCommand cmd = new KlanCommand(this);
        getCommand("klan").setExecutor(cmd);
        getCommand("klan").setTabCompleter(cmd);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new KlanyPlaceholders(this).register();
            getLogger().info("PlaceholderAPI wykryte - placeholdery %firestarklany_...% aktywne.");
        }
        if (getServer().getPluginManager().isPluginEnabled("Essentials")) {
            getLogger().info("Essentials wykryte - uzywam jego ekonomii (Vault) i ukrywam graczy w /vanish.");
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            manager.zapiszJesliTrzeba();
            ranking.zapiszJesliTrzeba();
        }, 20L * 30, 20L * 30);
        getServer().getScheduler().runTask(this, nametagi::odswiezWszystkich);
        getLogger().info("FIRESTAR KLANY wlaczony. Klanow: " + manager.wszystkie().size());
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.zapisz();
        if (ranking != null) ranking.zapisz();
        if (nametagi != null) nametagi.wylacz();
    }

    public void przeladuj() {
        reloadConfig();
        Text.setPrefix(getConfig().getString("prefix", "&6&lKLANY &8» &7"));
        eco = null;
        nametagi.przebuduj();
    }

    /** Ekonomia z Vault - jesli jest Essentials, preferujemy jego ekonomie. */
    public Economy eco() {
        if (eco != null) return eco;
        Collection<RegisteredServiceProvider<Economy>> regs =
                getServer().getServicesManager().getRegistrations(Economy.class);
        RegisteredServiceProvider<Economy> wybrany = null;
        for (RegisteredServiceProvider<Economy> r : regs) {
            if (r.getPlugin().getName().equalsIgnoreCase("Essentials")) { wybrany = r; break; }
            if (wybrany == null) wybrany = r;
        }
        if (wybrany != null) eco = wybrany.getProvider();
        return eco;
    }

    public void odswiezNametagi() {
        if (nametagi != null) nametagi.odswiezWszystkich();
    }

    public KlanManager manager() { return manager; }
    public Menus menus() { return menus; }
    public RankingManager ranking() { return ranking; }
}
