package me.drex.betteritemviewer;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

public class BetterItemViewerComponent implements Component<EntityStore> {

    public static final Codec<Filter> FILTER_CODEC = new EnumCodec<>(Filter.class);

    public static final BuilderCodec<BetterItemViewerComponent> CODEC = BuilderCodec.builder(BetterItemViewerComponent.class, BetterItemViewerComponent::new)
        .append(new KeyedCodec<>("SearchQuery", Codec.STRING), (o, v) -> o.searchQuery = v, o -> o.searchQuery)
        .add()
        .append(new KeyedCodec<>("ModFilter", Codec.STRING), (o, v) -> o.modFilter = v, o -> o.modFilter)
        .add()
        .append(new KeyedCodec<>("CategoryFilter", Codec.STRING), (o, v) -> o.categoryFilter = v, o -> o.categoryFilter)
        .add()
        .append(new KeyedCodec<>("CraftableFilter", FILTER_CODEC), (o, v) -> o.craftableFilter = v, o -> o.craftableFilter)
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
        .append(new KeyedCodec<>("ItemListRows", Codec.INTEGER), (o, v) -> o.itemListRows = v, o -> o.itemListRows)
        .add()
        .append(new KeyedCodec<>("ItemListColumns", Codec.INTEGER), (o, v) -> o.itemListColumns = v, o -> o.itemListColumns)
        .add()
        .append(new KeyedCodec<>("ShowSalvagerRecipes", Codec.BOOLEAN), (o, v) -> o.showSalvagerRecipes = v, o -> o.showSalvagerRecipes)
        .add()
        .append(new KeyedCodec<>("ShowHiddenItems", Codec.BOOLEAN), (o, v) -> o.showHiddenItems = v, o -> o.showHiddenItems)
        .add()
        .append(new KeyedCodec<>("AltKeybind", Codec.BOOLEAN), (o, v) -> o.altKeybind = v, o -> o.altKeybind)
        .add()
        .build();

    public String searchQuery = "";
    public String modFilter = "";
    public String categoryFilter = "";
    public Filter craftableFilter = Filter.ALL;
    public String sortMode = "Item Name (Ascending)";
    public String selectedItem;
    public int selectedPage = 0;
    public int selectedRecipeInPage = 0;
    public int selectedRecipeOutPage = 0;
    public int itemListRows = 9;
    public int itemListColumns = 8;
    public boolean showSalvagerRecipes = false;
    public boolean showHiddenItems = false;
    public boolean altKeybind = Main.getInstance().config.get().defaultAltKeybind;

    public static ComponentType<EntityStore, BetterItemViewerComponent> getComponentType() {
        return Main.getInstance().getComponentType();
    }

    private BetterItemViewerComponent() {
    }

    public BetterItemViewerComponent(
        String searchQuery, String modFilter, String categoryFilter, Filter craftableFilter, String sortMode,
        String selectedItem, int selectedPage, int selectedRecipeInPage, int selectedRecipeOutPage, int itemListRows,
        int itemListColumns, boolean showSalvagerRecipes, boolean showHiddenItems, boolean altKeybind
    ) {
        this.searchQuery = searchQuery;
        this.modFilter = modFilter;
        this.categoryFilter = categoryFilter;
        this.craftableFilter = craftableFilter;
        this.sortMode = sortMode;
        this.selectedItem = selectedItem;
        this.selectedPage = selectedPage;
        this.selectedRecipeInPage = selectedRecipeInPage;
        this.selectedRecipeOutPage = selectedRecipeOutPage;
        this.itemListRows = itemListRows;
        this.itemListColumns = itemListColumns;
        this.showSalvagerRecipes = showSalvagerRecipes;
        this.showHiddenItems = showHiddenItems;
        this.altKeybind = altKeybind;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new BetterItemViewerComponent(
            searchQuery, modFilter, categoryFilter, craftableFilter, sortMode, selectedItem, selectedPage,
            selectedRecipeInPage, selectedRecipeOutPage, itemListRows, itemListColumns, showSalvagerRecipes, showHiddenItems, altKeybind
        );
    }

    public enum Filter {
        ALL, YES, NO
    }
}
