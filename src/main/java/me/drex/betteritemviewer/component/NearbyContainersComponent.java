package me.drex.betteritemviewer.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.BetterItemViewerPlugin;

import javax.annotation.Nullable;

public class NearbyContainersComponent implements Component<EntityStore> {

    public ItemContainer itemContainer = EmptyItemContainer.INSTANCE;

    public static ComponentType<EntityStore, NearbyContainersComponent> getComponentType() {
        return BetterItemViewerPlugin.get().getNearbyContainersComponentType();
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new NearbyContainersComponent();
    }

}
