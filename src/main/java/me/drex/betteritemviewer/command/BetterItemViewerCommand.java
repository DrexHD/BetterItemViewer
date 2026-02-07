package me.drex.betteritemviewer.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.BetterItemViewerPlugin;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.ui.page.ItemViewerPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

public class BetterItemViewerCommand extends AbstractCommand {

    public BetterItemViewerCommand() {
        super("betteritemviewer", "Displays tools, weapons and other items and shows detailed information, like tool stats or weapon damage.", false);
        this.addAliases("biv", "jei");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        CommandSender sender = context.sender();
        if (sender instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRefComponent == null) return;
                    BetterItemViewerComponent settings = store.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());
                    try {
                        player.getPageManager().openCustomPage(ref, store, new ItemViewerPage(playerRefComponent, CustomPageLifetime.CanDismiss));
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("An error occurred while opening the GUI."));
                        BetterItemViewerPlugin.get().getLogger().at(Level.SEVERE).withCause(e).log("Failed to open BetterItemViewerGui");
                        settings.clearFilters();
                    }
                }, world);
            } else {
                context.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
                return CompletableFuture.completedFuture(null);
            }
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public boolean hasPermission(@Nonnull CommandSender sender) {
        return !BetterItemViewerPlugin.get().config().disableCommand && super.hasPermission(sender);
    }
}
