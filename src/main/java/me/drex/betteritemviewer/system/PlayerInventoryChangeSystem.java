package me.drex.betteritemviewer.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.ui.hud.HudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PlayerInventoryChangeSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    public PlayerInventoryChangeSystem() {
        super(InventoryChangeEvent.class);
    }

    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InventoryChangeEvent event
    ) {
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());

        assert player != null;

        World world = store.getExternalData().getWorld();

        world.execute(() -> HudUtils.updateHud(player.getReference()));
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }
}
