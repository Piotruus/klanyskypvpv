package pl.firestar.klany;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static String prefix = "&6&lKLANY &8» &7";

    private Text() {}

    public static void setPrefix(String p) { prefix = p; }
    public static String prefix() { return prefix; }

    public static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String msg) { to.sendMessage(c(prefix + msg)); }
    public static void raw(CommandSender to, String msg) { to.sendMessage(c(msg)); }

    public static String kwota(double d) {
        if (d == Math.floor(d)) return ((long) d) + "$";
        return String.format(Locale.US, "%.2f$", d);
    }
}
