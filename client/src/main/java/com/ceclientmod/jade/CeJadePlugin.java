package com.ceclientmod.jade;

import com.ceclientmod.CraftEngineClientModInit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Instantiated by Jade via the "jade" fabric.mod.json entrypoint (Fabric's own entrypoint mechanism -
 * explicit class name declared there, no classpath scanning) - never referenced from our own client
 * entrypoint, so the game still launches fine without Jade installed.
 *
 * CraftEngine blocks are, over the wire, real vanilla blocks (note_block/tripwire/mushroom_stem/...)
 * chosen as a "visual disguise" - see CeBlockRegistry. This appends the true CraftEngine identity to
 * Jade's tooltip whenever the block being looked at matches an entry in that table.
 *
 * RISK (see design plan): appending an extra tooltip line is the safely-verifiable minimum; whether
 * Jade also exposes a way to replace the primary name/icon line outright should be revisited once the
 * real Jade API jar is available to inspect (this class only needs its imports adjusted, not redesigned,
 * if so).
 */
public final class CeJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath("ceclientmod", "jade_plugin");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new CeBlockComponentProvider(), Block.class);
    }

    private static final class CeBlockComponentProvider implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CraftEngineClientModInit.blocks().ceIdFor(accessor.getBlockState())
                    .ifPresent(ceId -> tooltip.add(Component.literal("CraftEngine: " + ceId)));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
