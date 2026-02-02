package me.drex.betteritemviewer.ui.hud;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class HudUtils {
    public static final PluginIdentifier MULTIPLE_HUD = new PluginIdentifier("Buuz135", "MultipleHUD");

    public static void updateHud(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        // TODO MultiHud
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;
        if (PluginManager.get().getPlugin(MULTIPLE_HUD) != null) {
            com.buuz135.mhud.MultipleHUD.getInstance().setCustomHud(player, playerRef, "BetterItemViewer", new PinnedRecipesHud(playerRef));
        } else {
            player.getHudManager().setCustomHud(playerRef, new PinnedRecipesHud(playerRef));
        }

    }
}
