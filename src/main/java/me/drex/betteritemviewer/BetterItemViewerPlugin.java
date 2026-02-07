package me.drex.betteritemviewer;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import me.drex.betteritemviewer.command.BetterItemViewerCommand;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.component.NearbyContainersComponent;
import me.drex.betteritemviewer.config.BetterItemViewerConfig;
import me.drex.betteritemviewer.system.CollectNearbyContainersSystem;
import me.drex.betteritemviewer.ui.hud.HudUtils;
import me.drex.betteritemviewer.interaction.OpenBetterItemViewerInteraction;
import me.drex.betteritemviewer.item.ItemManager;
import me.drex.betteritemviewer.system.CheckKeybindSystem;

import javax.annotation.Nonnull;

public class BetterItemViewerPlugin extends JavaPlugin {

    private static BetterItemViewerPlugin instance;

    private final Config<BetterItemViewerConfig> config = this.withConfig("BetterItemViewer", BetterItemViewerConfig.CODEC);
    private ComponentType<EntityStore, BetterItemViewerComponent> mainComponentType;
    private ComponentType<EntityStore, NearbyContainersComponent> nearbyContainersComponentType;

    public static BetterItemViewerPlugin get() {
        return instance;
    }

    public BetterItemViewerPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public ComponentType<EntityStore, BetterItemViewerComponent> getMainComponentType() {
        return this.mainComponentType;
    }

    public ComponentType<EntityStore, NearbyContainersComponent> getNearbyContainersComponentType() {
        return this.nearbyContainersComponentType;
    }

    @Override
    protected void setup() {
        instance = this;
        config.save();
        this.mainComponentType = this.getEntityStoreRegistry().registerComponent(BetterItemViewerComponent.class, "Drex_BetterItemViewer", BetterItemViewerComponent.CODEC);
        this.nearbyContainersComponentType = this.getEntityStoreRegistry().registerComponent(NearbyContainersComponent.class, NearbyContainersComponent::new);
        this.getCommandRegistry().registerCommand(new BetterItemViewerCommand());
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, BetterItemViewerPlugin::onItemLoad);
        this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, BetterItemViewerPlugin::onRecipeLoad);
        this.getEventRegistry().register(AllWorldsLoadedEvent.class, BetterItemViewerPlugin::onReady);
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, BetterItemViewerPlugin::onPlayerReady);
        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, BetterItemViewerPlugin::onInventoryChange);
        this.getEntityStoreRegistry().registerSystem(new CheckKeybindSystem());
        if (!config().disableIncludeContainersSetting) {
            this.getEntityStoreRegistry().registerSystem(new CollectNearbyContainersSystem());
        }
        Interaction.CODEC.register("OpenBetterItemViewer", OpenBetterItemViewerInteraction.class, OpenBetterItemViewerInteraction.CODEC);
    }

    private static void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        World world = event.getEntity().getWorld();
        world.execute(() -> {
            HudUtils.updateHud(event.getEntity().getReference());
        });
    }

    private static void onPlayerReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        HudUtils.updateHud(ref);
    }

    public BetterItemViewerConfig config() {
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