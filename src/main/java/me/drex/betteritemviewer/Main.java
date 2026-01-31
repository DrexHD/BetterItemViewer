package me.drex.betteritemviewer;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import me.drex.betteritemviewer.command.BetterItemViewerCommand;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.config.BetterItemViewerConfig;
import me.drex.betteritemviewer.interaction.OpenBetterItemViewerInteraction;
import me.drex.betteritemviewer.item.ItemManager;

import javax.annotation.Nonnull;

public class Main extends JavaPlugin {

    private static Main instance;

    private final Config<BetterItemViewerConfig> config = this.withConfig("BetterItemViewer", BetterItemViewerConfig.CODEC);
    private ComponentType<EntityStore, BetterItemViewerComponent> componentType;

    public static Main get() {
        return instance;
    }

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public ComponentType<EntityStore, BetterItemViewerComponent> getComponentType() {
        return this.componentType;
    }

    @Override
    protected void setup() {
        instance = this;
        config.save();
        this.componentType = this.getEntityStoreRegistry().registerComponent(BetterItemViewerComponent.class, "Drex_BetterItemViewer", BetterItemViewerComponent.CODEC);
        this.getCommandRegistry().registerCommand(new BetterItemViewerCommand());
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, Main::onItemLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, Main::onRecipeLoad);
        this.getEventRegistry().register(AllWorldsLoadedEvent.class, Main::onReady);
        this.getEntityStoreRegistry().registerSystem(new CheckKeybindSystem());
        Interaction.CODEC.register("OpenBetterItemViewer", OpenBetterItemViewerInteraction.class, OpenBetterItemViewerInteraction.CODEC);

    }

    public BetterItemViewerConfig getConfig() {
        return config.get();
    }

    private static void onReady(AllWorldsLoadedEvent event) {
        ItemManager.get().findMobDrops();
    }

    private static void onRecipeLoad(LoadedAssetsEvent<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> event) {
        event.getLoadedAssets().forEach((id, recipe) -> ItemManager.get().registerRecipe(id, recipe));
    }

    private static void onItemLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        event.getLoadedAssets().forEach((id, item) -> ItemManager.get().registerItem(id, item));
    }

}