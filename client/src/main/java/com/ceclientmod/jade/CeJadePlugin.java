package com.ceclientmod.jade;

import com.ceclientmod.CraftEngineClientModInit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.JadeUI;

/**
 * Instantiated by Jade via the "jade" fabric.mod.json entrypoint (Fabric's own entrypoint mechanism -
 * explicit class name declared there, no classpath scanning) - never referenced from our own client
 * entrypoint, so the game still launches fine without Jade installed.
 *
 * CraftEngine blocks and furniture arrive as vanilla block states/entities. The bridge replaces Jade's
 * icon with the exact client-bound source item and also replaces disguised blocks' titles with that
 * item's client-resolved hover name.
 */
public final class CeJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath("ceclientmod", "jade_plugin");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        CeBlockComponentProvider blocks = new CeBlockComponentProvider();
        registration.registerBlockComponent(blocks, Block.class);
        registration.registerBlockIcon(blocks, Block.class);
        registration.registerEntityIcon(new CeFurnitureIconProvider(), Entity.class);
        registration.addTooltipCollectedCallback(Integer.MAX_VALUE, (box, accessor) -> {
            if (!(accessor instanceof BlockAccessor blockAccessor)) return;
            CraftEngineClientModInit.blockIcons().iconFor(blockAccessor.getBlockState())
                    .ifPresent(stack -> box.getTooltip().replace(
                            JadeIds.CORE_OBJECT_NAME,
                            IThemeHelper.get().title(stack.getHoverName())));
        });
    }

    private static final class CeBlockComponentProvider implements IBlockComponentProvider {

        @Override
        public Element getIcon(BlockAccessor accessor, IPluginConfig config, Element currentIcon) {
            return CraftEngineClientModInit.blockIcons().iconFor(accessor.getBlockState())
                    .<Element>map(JadeUI::item)
                    .orElse(currentIcon);
        }

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

    private static final class CeFurnitureIconProvider implements IEntityComponentProvider {
        @Override
        public Element getIcon(EntityAccessor accessor, IPluginConfig config, Element currentIcon) {
            return CraftEngineClientModInit.furnitureIcons().iconFor(accessor.getEntity())
                    .<Element>map(JadeUI::item)
                    .orElse(currentIcon);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
