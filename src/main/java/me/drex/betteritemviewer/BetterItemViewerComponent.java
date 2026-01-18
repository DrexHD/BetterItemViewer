package me.drex.betteritemviewer;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

public class BetterItemViewerComponent implements Component<EntityStore> {

    public static final BuilderCodec<BetterItemViewerComponent> CODEC = BuilderCodec.builder(BetterItemViewerComponent.class, BetterItemViewerComponent::new)
        .append(new KeyedCodec<>("SearchQuery", Codec.STRING), (o, v) -> o.searchQuery = v, o -> o.searchQuery)
        .add()
        .append(new KeyedCodec<>("ModFilter", Codec.STRING), (o, v) -> o.modFilter = v, o -> o.modFilter)
        .add()
        .append(new KeyedCodec<>("SortMode", Codec.STRING), (o, v) -> o.sortMode = v, o -> o.sortMode)
        .add()
        .append(new KeyedCodec<>("SelectedItem", Codec.STRING), (o, v) -> o.selectedItem = v, o -> o.selectedItem)
        .add()
        .append(new KeyedCodec<>("SelectedPage", Codec.INTEGER), (o, v) -> o.selectedPage = v, o -> o.selectedPage)
        .add()
        .append(new KeyedCodec<>("SelectedRecipeInPage", Codec.INTEGER), (o, v) -> o.selectedRecipeInPage = v, o -> o.selectedRecipeInPage)
        .add()
        .append(new KeyedCodec<>("SelectedRecipeOutPage", Codec.INTEGER), (o, v) -> o.selectedRecipeOutPage = v, o -> o.selectedRecipeOutPage)
        .add()
        .build();

    public String searchQuery = "";
    public String modFilter = "";
    public String sortMode;
    public String selectedItem;
    public int selectedPage = 0;
    public int selectedRecipeInPage = 0;
    public int selectedRecipeOutPage = 0;

    public static ComponentType<EntityStore, BetterItemViewerComponent> getComponentType() {
        return Main.getInstance().getComponentType();
    }

    private BetterItemViewerComponent() {
    }

    public BetterItemViewerComponent(String searchQuery, String modFilter, String sortMode, String selectedItem, int selectedPage, int selectedRecipeInPage, int selectedRecipeOutPage) {
        this.searchQuery = searchQuery;
        this.modFilter = modFilter;
        this.sortMode = sortMode;
        this.selectedItem = selectedItem;
        this.selectedPage = selectedPage;
        this.selectedRecipeInPage = selectedRecipeInPage;
        this.selectedRecipeOutPage = selectedRecipeOutPage;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new BetterItemViewerComponent(searchQuery, modFilter, sortMode, selectedItem, selectedPage, selectedRecipeInPage, selectedRecipeOutPage);
    }
}
