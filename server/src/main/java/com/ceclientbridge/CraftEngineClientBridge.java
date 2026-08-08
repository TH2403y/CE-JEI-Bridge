package com.ceclientbridge;

import com.ceclientbridge.net.BridgeChannels;
import com.ceclientbridge.protocol.BridgeCapabilities;
import com.ceclientbridge.protocol.BridgeCompatibilityGate;
import com.ceclientbridge.protocol.BridgeHandshake;
import com.ceclientbridge.protocol.BridgeHello;
import com.ceclientbridge.protocol.BridgeHelloCodec;
import com.ceclientbridge.recipe.RecipeSyncListener;
import com.ceclientbridge.sync.SyncManager;
import com.ceclientbridge.version.BridgeServerTarget;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bridges CraftEngine's custom item/block/brewing-recipe registries (public Bukkit API only, no NMS)
 * to any connected client running the companion CraftEngineClientMod, so that mod's JEI/Jade
 * integrations can tell CraftEngine's custom items/blocks apart from vanilla ones.
 */
public final class CraftEngineClientBridge extends JavaPlugin implements Listener, PluginMessageListener {

    private SyncManager syncManager;
    private final BridgeCompatibilityGate compatibilityGate = new BridgeCompatibilityGate();

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
        try {
            BridgeHello clientHello = BridgeHelloCodec.decode(message);
            BridgeHello serverHello = new BridgeHello(
                    com.ceclientbridge.protocol.BridgeProtocol.CURRENT_VERSION,
                    BridgeServerTarget.minecraftTarget(),
                    BridgeCapabilities.ALL
            );
            var negotiation = BridgeHandshake.negotiate(serverHello, clientHello);
            if (!negotiation.accepted()) {
                compatibilityGate.clear(player.getUniqueId());
                getLogger().warning("Rejected CraftEngine client bridge from " + player.getName() + ": " + negotiation.reason());
                return;
            }
        } catch (IllegalArgumentException invalidHello) {
            getLogger().warning("Rejected malformed CraftEngine client bridge hello from " + player.getName()
                    + ": " + invalidHello.getMessage());
            compatibilityGate.clear(player.getUniqueId());
            return;
        }
        compatibilityGate.markCompatible(player.getUniqueId());
        pushAllTo(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // sendPluginMessage silently no-ops until the client's channel-registration packet reaches the
        // server (getListeningPluginChannels() is still empty at PlayerJoinEvent) - delay a moment so it
        // has time to arrive. The client-side HELLO handshake (onPluginMessageReceived above) is the
        // primary trigger. The gate below prevents this fallback from sending before compatibility is
        // established.
        Player player = event.getPlayer();
        compatibilityGate.clear(player.getUniqueId());
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        compatibilityGate.clear(event.getPlayer().getUniqueId());
    }

    public void pushAllTo(Player player) {
        if (!compatibilityGate.isCompatible(player.getUniqueId())) {
            return;
        }
        long generation = syncManager.generation();
        BridgeChannels.send(this, player, BridgeChannels.ITEMS, generation, syncManager.itemsPayload());
        BridgeChannels.send(this, player, BridgeChannels.BLOCKS, generation, syncManager.blocksPayload());
        BridgeChannels.send(this, player, BridgeChannels.BREWING, generation, syncManager.brewingPayload());
        BridgeChannels.send(this, player, BridgeChannels.CRAFTING_DISPLAY, generation, syncManager.craftingDisplayPayload());
        BridgeChannels.send(this, player, BridgeChannels.SMITHING_DISPLAY, generation, syncManager.smithingDisplayPayload());
    }
}
