package com.ceclientbridge;

import com.ceclientbridge.net.BridgeChannels;
import com.ceclientbridge.recipe.RecipeSyncListener;
import com.ceclientbridge.sync.SyncManager;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bridges CraftEngine's custom item/block/brewing-recipe registries (public Bukkit API only, no NMS)
 * to any connected client running the companion CraftEngineClientMod, so that mod's JEI/Jade
 * integrations can tell CraftEngine's custom items/blocks apart from vanilla ones.
 */
public final class CraftEngineClientBridge extends JavaPlugin implements Listener, PluginMessageListener {

    private SyncManager syncManager;

    @Override
    public void onEnable() {
        syncManager = new SyncManager(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeChannels.ITEMS);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeChannels.BLOCKS);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeChannels.BREWING);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeChannels.CRAFTING_DISPLAY);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeChannels.SMITHING_DISPLAY);
        getServer().getMessenger().registerIncomingPluginChannel(this, BridgeChannels.HELLO, this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new RecipeSyncListener(this, syncManager), this);

        BridgeCommand command = new BridgeCommand(this, syncManager);
        getCommand("cebridge").setExecutor(command);
        getCommand("cebridge").setTabCompleter(command);

        if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            syncManager.rebuild();
        } else {
            getLogger().warning("CraftEngine not found - sync payloads will stay empty until it loads and reloads.");
        }
    }

    public SyncManager syncManager() {
        return syncManager;
    }

    /** Client mod says "I'm here" on {@link BridgeChannels#HELLO} right after it registers the channel; push a full sync back. */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BridgeChannels.HELLO.equals(channel)) return;
        pushAllTo(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // sendPluginMessage silently no-ops until the client's channel-registration packet reaches the
        // server (getListeningPluginChannels() is still empty at PlayerJoinEvent) - delay a moment so it
        // has time to arrive. The client-side HELLO handshake (onPluginMessageReceived above) is the
        // primary trigger; this is a best-effort fallback for players whose hello never arrives.
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                pushAllTo(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        syncManager.rebuild();
        for (Player player : getServer().getOnlinePlayers()) {
            pushAllTo(player);
        }
    }

    public void pushAllTo(Player player) {
        BridgeChannels.send(this, player, BridgeChannels.ITEMS, syncManager.itemsPayload());
        BridgeChannels.send(this, player, BridgeChannels.BLOCKS, syncManager.blocksPayload());
        BridgeChannels.send(this, player, BridgeChannels.BREWING, syncManager.brewingPayload());
        BridgeChannels.send(this, player, BridgeChannels.CRAFTING_DISPLAY, syncManager.craftingDisplayPayload());
        BridgeChannels.send(this, player, BridgeChannels.SMITHING_DISPLAY, syncManager.smithingDisplayPayload());
    }
}
