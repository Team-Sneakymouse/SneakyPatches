package com.sneakyrp.sneakypatches.trackingrange;

import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * {@code /trackingranges} shows the categories in force; {@code /trackingranges reload} rereads the file. Registered
 * next to Paper's own {@code /paper} command, so it needs no plugin. Permission {@value #PERMISSION} (ops and the
 * console by default; grant it with LuckPerms like any other).
 */
public final class TrackingRangesCommand extends Command {
    public static final String NAME = "trackingranges";
    public static final String PERMISSION = "sneakypatches.trackingranges";

    public TrackingRangesCommand() {
        super(NAME, "Show or reload SneakyPatches tracking range categories", "/" + NAME + " [reload]", List.of());
        setPermission(PERMISSION);
    }

    /** Called once at startup, where Paper registers its own commands. */
    public static void register(MinecraftServer server) {
        server.server.getCommandMap().register("sneakypatches", new TrackingRangesCommand());
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length == 0) {
            show(sender);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!MinecraftServer.getServer().isSameThread()) {
                sender.sendMessage("Run this from the server thread (in game or the console).");
                return true;
            }
            TrackingRanges.ReloadResult result = TrackingRanges.reload();
            if (result.applied()) {
                sender.sendMessage("Reloaded: " + result.refreshed() + " loaded entities changed range.");
                show(sender);
            } else {
                sender.sendMessage("Reload refused; the current ranges are unchanged. Fix the file and try again:");
                for (String problem : result.problems()) sender.sendMessage("  " + problem);
            }
        } else {
            sender.sendMessage("Usage: " + usageMessage);
        }
        return true;
    }

    private static void show(CommandSender sender) {
        RangeCategories current = TrackingRanges.categories();
        sender.sendMessage("Tracking range categories (" + TrackingRanges.configPath() + "):");
        if (current.ranges().isEmpty()) sender.sendMessage("  none");
        current.ranges().entrySet().stream().sorted(java.util.Map.Entry.comparingByValue())
            .forEach(e -> sender.sendMessage("  " + RangeCategories.TAG_PREFIX + e.getKey() + " = " + e.getValue()
                + " blocks"));
        current.tagAliases().forEach((tag, category) -> sender.sendMessage("  tag " + tag + " -> "
            + RangeCategories.TAG_PREFIX + category + " = " + current.ranges().get(category) + " blocks ("
            + RangeCategories.TAG_ALIAS_PREFIX + tag + ")"));
        for (String type : RangeCategories.TYPES) {
            Integer override = current.typeDefaults().get(type);
            sender.sendMessage("  untagged " + type + " = " + (override != null
                ? override + " blocks (@default." + type + ")"
                : spigotRange(type) + " blocks (spigot.yml)"));
        }
        sender.sendMessage("  display limit: " + (current.displayViewScale() == 0 ? "off"
            : "view range x 64 x " + current.displayViewScale()));
    }

    /** The overworld's spigot.yml entity-tracking-range for a type (per-world spigot.yml sections may differ). */
    private static int spigotRange(String type) {
        org.spigotmc.SpigotWorldConfig config = MinecraftServer.getServer().overworld().spigotConfig;
        return switch (type) {
            case "players" -> config.playerTrackingRange;
            case "animals" -> config.animalTrackingRange;
            case "monsters" -> config.monsterTrackingRange;
            case "misc" -> config.miscTrackingRange;
            case "display" -> config.displayTrackingRange;
            case "other" -> config.otherTrackingRange;
            default -> throw new IllegalArgumentException(type);
        };
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) && testPermissionSilent(sender)) {
            return List.of("reload");
        }
        return List.of();
    }
}
