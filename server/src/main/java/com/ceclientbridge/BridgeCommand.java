package com.ceclientbridge;

import com.ceclientbridge.sync.SyncManager;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** /cebridge resync|reload|info|dump <id> - `dump` exists to verify what SyncManager actually ships to
 * clients: it prints both the server-bound stack (CraftEngineItems#byId(...).buildItem(...), which never
 * carries item_model) and the client-bound stack (SyncManager#toClientBoundStack) the sync payload is
 * actually built from. */
public final class BridgeCommand implements CommandExecutor, TabCompleter {

    private final CraftEngineClientBridge plugin;
    private final SyncManager syncManager;

    public BridgeCommand(CraftEngineClientBridge plugin, SyncManager syncManager) {
        this.plugin = plugin;
        this.syncManager = syncManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7/cebridge <resync|reload|info|dump>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                syncManager.rebuild();
                sender.sendMessage("§aRebuilt item/block/brewing sync caches from CraftEngine.");
            }
            case "resync" -> {
                if (sender instanceof Player player) {
                    plugin.pushAllTo(player);
                    sender.sendMessage("§aPushed a fresh sync to you.");
                } else {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        plugin.pushAllTo(player);
                    }
                    sender.sendMessage("§aPushed a fresh sync to all online players.");
                }
            }
            case "info" -> sender.sendMessage(
                    "§7items=" + syncManager.itemsPayload().length + "B blocks="
                            + syncManager.blocksPayload().length + "B brewing="
                            + syncManager.brewingPayload().length + "B");
            case "dump" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /cebridge dump <craftengine-item-id>");
                    return true;
                }
                dump(sender, args[1]);
            }
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    private void dump(CommandSender sender, String rawId) {
        try {
            var def = CraftEngineItems.byId(Key.of(rawId));
            if (def == null) {
                sender.sendMessage("§cNo such CraftEngine item: " + rawId);
                return;
            }
            ItemStack serverBound = def.buildItem(ItemBuildContext.empty(), 1).getBukkitItem();
            ItemStack clientBound = SyncManager.toClientBoundStack(serverBound);
            sender.sendMessage("§7" + rawId + " server-bound -> " + serverBound);
            sender.sendMessage("§7" + rawId + " client-bound -> " + clientBound);
            plugin.getLogger().info("Dump of '" + rawId + "' server-bound: " + serverBound);
            plugin.getLogger().info("Dump of '" + rawId + "' client-bound: " + clientBound);
        } catch (Throwable t) {
            sender.sendMessage("§cFailed: " + t);
            plugin.getLogger().warning("Dump failed for '" + rawId + "': " + t);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return List.of("resync", "reload", "info", "dump");
        }
        return List.of();
    }
}
