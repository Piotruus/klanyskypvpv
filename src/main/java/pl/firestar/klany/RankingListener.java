package pl.firestar.klany;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class RankingListener implements Listener {

    private final FirestarKlany plugin;

    public RankingListener(FirestarKlany plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.ranking().rejestruj(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player ofiara = e.getEntity();
        Player zabojca = ofiara.getKiller();
        if (zabojca == null || zabojca.equals(ofiara)) return;

        RankingManager r = plugin.ranking();
        RankingManager.Wynik w = r.przetworz(zabojca, ofiara);

        if (plugin.getConfig().getBoolean("ranking.broadcast-zabicia", true)) {
            e.deathMessage(null);
            r.ogloszZabicie(zabojca, ofiara, w);
        }
        if (w.powod != null) {
            Text.send(zabojca, "&7Brak punktów za to zabójstwo: &f" + w.powod + "&7.");
        }
    }
}
