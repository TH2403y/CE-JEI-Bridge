package com.ceclientmod;

import com.ceclientmod.cache.CeBlockRegistry;
import com.ceclientmod.cache.CeBrewingRegistry;
import com.ceclientmod.cache.CeCraftingRegistry;
import com.ceclientmod.cache.CeItemRegistry;
import com.ceclientmod.cache.CeSmithingRegistry;
import com.ceclientmod.net.BridgeChannels;
import com.ceclientmod.net.ChunkAssembler;
import com.ceclientmod.net.ChunkPayload;
import com.ceclientmod.net.HelloPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * Client-only entrypoint. Talks to the companion CraftEngineClientBridge Paper plugin over three
 * S2C custom-payload channels (items/blocks/brewing, chunked - see ChunkPayload) plus a C2S "hello"
 * handshake sent once we're ready to receive. Populates the shared registries that the optional
 * JEI (com.ceclientmod.jei) and Jade (com.ceclientmod.jade) integrations read from - those two are
 * never referenced from here, so this mod loads fine with neither, either, or both installed.
 */
public final class CraftEngineClientModInit implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ceclientmod");

    private static final CeItemRegistry ITEMS = new CeItemRegistry();
    private static final CeBlockRegistry BLOCKS = new CeBlockRegistry();
    private static final CeBrewingRegistry BREWING = new CeBrewingRegistry();
    private static final CeCraftingRegistry CRAFTING_DISPLAY = new CeCraftingRegistry();
    private static final CeSmithingRegistry SMITHING_DISPLAY = new CeSmithingRegistry();

    private static final int HELLO_MAX_ATTEMPTS = 200; // ~10s at 20 ticks/sec
    private static boolean helloPending = false;
    private static int helloAttempts = 0;

    public static CeItemRegistry items() {
        return ITEMS;
    }

    public static CeBlockRegistry blocks() {
        return BLOCKS;
    }

    public static CeBrewingRegistry brewing() {
        return BREWING;
    }

    public static CeCraftingRegistry craftingDisplay() {
        return CRAFTING_DISPLAY;
    }

    public static CeSmithingRegistry smithingDisplay() {
        return SMITHING_DISPLAY;
    }

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(BridgeChannels.ITEMS, ChunkPayload.codecFor(BridgeChannels.ITEMS));
        PayloadTypeRegistry.playS2C().register(BridgeChannels.BLOCKS, ChunkPayload.codecFor(BridgeChannels.BLOCKS));
        PayloadTypeRegistry.playS2C().register(BridgeChannels.BREWING, ChunkPayload.codecFor(BridgeChannels.BREWING));
        PayloadTypeRegistry.playS2C().register(BridgeChannels.CRAFTING_DISPLAY, ChunkPayload.codecFor(BridgeChannels.CRAFTING_DISPLAY));
        PayloadTypeRegistry.playS2C().register(BridgeChannels.SMITHING_DISPLAY, ChunkPayload.codecFor(BridgeChannels.SMITHING_DISPLAY));
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);

        ChunkAssembler itemsAssembler = new ChunkAssembler("items");
        ChunkAssembler blocksAssembler = new ChunkAssembler("blocks");
        ChunkAssembler brewingAssembler = new ChunkAssembler("brewing");
        ChunkAssembler craftingDisplayAssembler = new ChunkAssembler("crafting_display");
        ChunkAssembler smithingDisplayAssembler = new ChunkAssembler("smithing_display");

        ClientPlayNetworking.registerGlobalReceiver(BridgeChannels.ITEMS, (payload, context) ->
                itemsAssembler.accept(payload).ifPresent(full -> {
                    try {
                        ITEMS.readFrom(new DataInputStream(new ByteArrayInputStream(full)));
                        LOGGER.info("ceclientmod: loaded " + ITEMS.all().size() + " CraftEngine items");
                        notifyJeiItemsUpdated();
                    } catch (Exception e) {
                        LOGGER.warn("ceclientmod: failed to parse items sync", e);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(BridgeChannels.BLOCKS, (payload, context) ->
                blocksAssembler.accept(payload).ifPresent(full -> {
                    try {
                        BLOCKS.readFrom(new DataInputStream(new ByteArrayInputStream(full)));
                        LOGGER.info("ceclientmod: loaded CraftEngine block visual mappings");
                    } catch (Exception e) {
                        LOGGER.warn("ceclientmod: failed to parse blocks sync", e);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(BridgeChannels.BREWING, (payload, context) ->
                brewingAssembler.accept(payload).ifPresent(full -> {
                    try {
                        BREWING.readFrom(new DataInputStream(new ByteArrayInputStream(full)));
                        LOGGER.info("ceclientmod: loaded " + BREWING.all().size() + " CraftEngine brewing recipes");
                    } catch (Exception e) {
                        LOGGER.warn("ceclientmod: failed to parse brewing sync", e);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(BridgeChannels.CRAFTING_DISPLAY, (payload, context) ->
                craftingDisplayAssembler.accept(payload).ifPresent(full -> {
                    try {
                        CRAFTING_DISPLAY.readFrom(new DataInputStream(new ByteArrayInputStream(full)));
                        LOGGER.info("ceclientmod: loaded " + CRAFTING_DISPLAY.all().size() + " CraftEngine crafting display entries");
                        notifyJeiCraftingDisplayUpdated();
                    } catch (Exception e) {
                        LOGGER.warn("ceclientmod: failed to parse crafting display sync", e);
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(BridgeChannels.SMITHING_DISPLAY, (payload, context) ->
                smithingDisplayAssembler.accept(payload).ifPresent(full -> {
                    try {
                        SMITHING_DISPLAY.readFrom(new DataInputStream(new ByteArrayInputStream(full)));
                        LOGGER.info("ceclientmod: loaded " + SMITHING_DISPLAY.all().size() + " CraftEngine smithing display entries");
                        notifyJeiSmithingDisplayUpdated();
                    } catch (Exception e) {
                        LOGGER.warn("ceclientmod: failed to parse smithing display sync", e);
                    }
                }));

        // canSend(HELLO) is frequently still false right at JOIN - the server's channel-advertisement
        // packet hasn't necessarily been processed client-side yet, so a one-shot send here silently
        // did nothing most of the time. Retry every client tick until it succeeds (typically within a
        // handful of ticks) instead of gambling on a single attempt.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            itemsAssembler.clear();
            blocksAssembler.clear();
            brewingAssembler.clear();
            craftingDisplayAssembler.clear();
            smithingDisplayAssembler.clear();
            helloPending = true;
            helloAttempts = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!helloPending) return;
            if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                ClientPlayNetworking.send(new HelloPayload());
                helloPending = false;
            } else if (++helloAttempts > HELLO_MAX_ATTEMPTS) {
                LOGGER.warn("ceclientmod: giving up waiting to send HELLO after " + HELLO_MAX_ATTEMPTS + " ticks");
                helloPending = false;
            }
        });
    }

    /** Guarded so com.ceclientmod.jei (which references mezz.jei.api.*) is only ever classloaded if JEI is present. */
    private static void notifyJeiItemsUpdated() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei")) {
            com.ceclientmod.jei.CeJeiPlugin.onItemsUpdated();
        }
    }

    private static void notifyJeiCraftingDisplayUpdated() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei")) {
            com.ceclientmod.jei.CeJeiPlugin.onCraftingDisplayUpdated();
        }
    }

    private static void notifyJeiSmithingDisplayUpdated() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei")) {
            com.ceclientmod.jei.CeJeiPlugin.onSmithingDisplayUpdated();
        }
    }
}
