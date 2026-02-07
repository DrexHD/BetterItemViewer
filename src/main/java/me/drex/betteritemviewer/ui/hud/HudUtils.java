package me.drex.betteritemviewer.ui.hud;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.component.NearbyContainersComponent;

public class HudUtils {
    public static final PluginIdentifier MULTIPLE_HUD = new PluginIdentifier("Buuz135", "MultipleHUD");

    public static void updateHud(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;
        BetterItemViewerComponent viewerComponent = store.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());
        NearbyContainersComponent nearbyContainers = store.ensureAndGetComponent(ref, NearbyContainersComponent.getComponentType());
        ItemContainer itemContainer = player.getInventory().getCombinedEverything();
        if (viewerComponent.includeContainers) {
            itemContainer = new CombinedItemContainer(itemContainer, nearbyContainers.itemContainer);
        }

        PinnedRecipesHud pinnedRecipesHud = new PinnedRecipesHud(playerRef, viewerComponent.pinnedRecipes, itemContainer);
        if (PluginManager.get().getPlugin(MULTIPLE_HUD) != null) {
            com.buuz135.mhud.MultipleHUD.getInstance().setCustomHud(player, playerRef, "BetterItemViewer", pinnedRecipesHud);
        } else {
            player.getHudManager().setCustomHud(playerRef, pinnedRecipesHud);
        }

    }
}
