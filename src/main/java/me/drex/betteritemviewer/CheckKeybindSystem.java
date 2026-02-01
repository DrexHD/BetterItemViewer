package me.drex.betteritemviewer;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.gui.BetterItemViewerGui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

public class CheckKeybindSystem extends EntityTickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        MovementStatesComponent statesComponent = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType());
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) return;
        World world = player.getWorld();
        MovementStates movementStates = statesComponent.getMovementStates();
        if (movementStates.walking && !Main.get().getConfig().disableKeybind) {
            Ref<EntityStore> ref = player.getReference();
            PlayerRef playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
            if (ref == null || !ref.isValid()) return;
            if (playerRefComponent == null) return;
            BetterItemViewerComponent component = commandBuffer.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());
            world.execute(() -> {
                try {
                    PageManager pageManager = player.getPageManager();
                    if (component.altKeybind && pageManager.getCustomPage() == null) {
                        pageManager.openCustomPage(ref, store, new BetterItemViewerGui(playerRefComponent, CustomPageLifetime.CanDismiss));
                    }
                } catch (Exception e) {
                    Main.get().getLogger().at(Level.SEVERE).withCause(e).log("Failed to open BetterItemViewerGui");
                    component.clearFilters();
                }
            });
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return MovementStatesComponent.getComponentType();
    }
}
