package me.drex.betteritemviewer.ui.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.component.NearbyContainersComponent;

public class HudUtils {
    public static void updateHud(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;
        BetterItemViewerComponent viewerComponent = store.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());
        NearbyContainersComponent nearbyContainers = store.ensureAndGetComponent(ref, NearbyContainersComponent.getComponentType());
        ItemContainer itemContainer = player.getInventory().getCombinedHotbarFirst();
        if (viewerComponent.includeContainers) {
            itemContainer = new CombinedItemContainer(itemContainer, nearbyContainers.itemContainer);
        }

        PinnedRecipesHud pinnedRecipesHud = new PinnedRecipesHud(playerRef, viewerComponent.pinnedRecipes, itemContainer);
        player.getHudManager().addCustomHud(playerRef, pinnedRecipesHud);
    }
}
