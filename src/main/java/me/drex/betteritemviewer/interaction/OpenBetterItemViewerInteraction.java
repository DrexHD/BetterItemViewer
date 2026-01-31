package me.drex.betteritemviewer.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.Main;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.gui.BetterItemViewerGui;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class OpenBetterItemViewerInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<OpenBetterItemViewerInteraction> CODEC = BuilderCodec.builder(
            OpenBetterItemViewerInteraction.class, OpenBetterItemViewerInteraction::new, SimpleInstantInteraction.CODEC
        )
        .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Player player = buffer.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;
        PlayerRef playerRef = buffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            BetterItemViewerComponent settings = buffer.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());
            world.execute(() -> {
                try {
                    player.getPageManager().openCustomPage(ref, store, new BetterItemViewerGui(playerRef, CustomPageLifetime.CanDismiss));
                } catch (Exception e) {
                    Main.getInstance().getLogger().at(Level.SEVERE).withCause(e).log("Failed to open BetterItemViewerGui");
                    settings.clearFilters();
                }
            });
        }
    }
}
