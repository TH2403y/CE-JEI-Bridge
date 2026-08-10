package com.ceclientmod.cache;

import com.ceclientbridge.protocol.JadeIconProtocol;
import com.ceclientmod.net.FurnitureProbePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** On-demand Jade furniture icons. Requests and responses are correlated to reject stale replies. */
public final class CeFurnitureIconRegistry {
    private static final long REQUEST_TIMEOUT_NANOS = 2_000_000_000L;

    private final Map<Integer, ItemStack> iconsByEntity = new HashMap<>();
    private final Map<Integer, Integer> entityByRequest = new HashMap<>();
    private final Map<Integer, Long> pendingEntities = new HashMap<>();
    private final Set<Integer> negativeEntities = new HashSet<>();
    private int nextRequestId;
    private boolean ready;

    public Optional<ItemStack> iconFor(Entity entity) {
        int entityId = entity.getId();
        ItemStack icon = iconsByEntity.get(entityId);
        if (icon != null) return Optional.of(icon);
        if (!ready || negativeEntities.contains(entityId)) return Optional.empty();
        long now = System.nanoTime();
        Long requestedAt = pendingEntities.get(entityId);
        if (requestedAt != null && now - requestedAt < REQUEST_TIMEOUT_NANOS) return Optional.empty();
        if (requestedAt != null) {
            pendingEntities.remove(entityId);
            entityByRequest.entrySet().removeIf(entry -> entry.getValue() == entityId);
        }
        if (!ClientPlayNetworking.canSend(FurnitureProbePayload.TYPE)) return Optional.empty();

        int requestId = nextRequestId++ & Integer.MAX_VALUE;
        entityByRequest.put(requestId, entityId);
        pendingEntities.put(entityId, now);
        ClientPlayNetworking.send(new FurnitureProbePayload(requestId, entityId));
        return Optional.empty();
    }

    public void accept(byte[] payload) throws IOException {
        JadeIconProtocol.FurnitureIcon response = JadeIconProtocol.decodeFurnitureIcon(payload);
        Integer expectedEntity = entityByRequest.remove(response.requestId());
        if (expectedEntity == null) return;
        pendingEntities.remove(expectedEntity);
        if (expectedEntity != response.entityId()) return;
        if (response.appearance().length == 0) {
            negativeEntities.add(response.entityId());
            return;
        }
        ItemStack stack = CeItemRegistry.readAppearance(
                new DataInputStream(new ByteArrayInputStream(response.appearance())));
        iconsByEntity.put(response.entityId(), stack);
    }

    public void clear() {
        iconsByEntity.clear();
        entityByRequest.clear();
        pendingEntities.clear();
        negativeEntities.clear();
        ready = false;
    }

    public void resetForSync() {
        clear();
        ready = true;
    }
}
