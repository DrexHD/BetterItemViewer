package me.drex.betteritemviewer.ui.page;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.*;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageClass;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.drex.betteritemviewer.BetterItemViewerPlugin;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;
import me.drex.betteritemviewer.component.NearbyContainersComponent;
import me.drex.betteritemviewer.config.BetterItemViewerConfig;
import me.drex.betteritemviewer.item.ItemDetails;
import me.drex.betteritemviewer.item.ItemManager;
import me.drex.betteritemviewer.item.Range;
import me.drex.betteritemviewer.ui.hud.HudUtils;

import javax.annotation.Nonnull;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.drex.betteritemviewer.ui.page.ItemViewerPage.GuiData.*;

public class ItemViewerPage extends InteractiveCustomUIPage<ItemViewerPage.GuiData> {
    public static final Function<PlayerRef, Comparator<Item>> NAME_COMPARATOR = playerRef ->
        Comparator.comparing(item ->
            Objects.requireNonNullElse(
                I18nModule.get().getMessage(playerRef.getLanguage(), item.getTranslationKey()),
                item.getTranslationKey()
            )
        );

    public static final Function<PlayerRef, Comparator<Item>> CATEGORY_COMPARATOR = playerRef ->
        Comparator.<Item, String>comparing(item -> {
            String[] categories = item.getCategories();
            if (categories == null || categories.length == 0) return "!";
            else return categories[0];
        }).thenComparing(NAME_COMPARATOR.apply(playerRef));

    public static final Function<PlayerRef, Comparator<Item>> QUALITY_COMPARATOR = playerRef ->
        Comparator.<Item>comparingInt(item -> {
            int qualityIndex = item.getQualityIndex();
            ItemQuality itemQuality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            return (itemQuality != null ? itemQuality : ItemQuality.DEFAULT_ITEM_QUALITY).getQualityValue();
        }).reversed().thenComparing(NAME_COMPARATOR.apply(playerRef));

    public static final Function<PlayerRef, Comparator<Item>> WEAPON_DAMAGE = playerRef ->
        Comparator.<Item>comparingInt(item -> {
            ItemDetails itemDetails = ItemManager.get().getOrCreateDetails(item.getId());
            int max = 0;
            Collection<Range> values = itemDetails.damageInteractions.values();
            for (Range range : values) {
                max = Math.max(max, range.max());
            }
            return max;
        }).reversed().thenComparing(CATEGORY_COMPARATOR.apply(playerRef));

    private static final Map<String, Function<PlayerRef, Comparator<Item>>> COMPARATORS =
        new LinkedHashMap<>() {{
            put("Category", CATEGORY_COMPARATOR);
            put("Name (A-Z)", NAME_COMPARATOR);
            put("Name (Z-A)", playerRef -> NAME_COMPARATOR.apply(playerRef).reversed());
            put("Weapon Damage", WEAPON_DAMAGE);
            put("Quality", QUALITY_COMPARATOR);
        }};
    private static final String RESET_FILTERS_ACTION = "ResetFilters";

    public ItemViewerPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, GuiData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Drex_BetterItemViewer_Gui.ui");
        BetterItemViewerComponent settings = store.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());

        setInputValues(settings, commandBuilder);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput", EventData.of(KEY_SEARCH_QUERY, "#SearchInput.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ShowSalvager #CheckBox", EventData.of(KEY_SHOW_SALVAGER_RECIPES, "#ShowSalvager #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ShowHiddenItems #CheckBox", EventData.of(KEY_SHOW_HIDDEN_ITEMS, "#ShowHiddenItems #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ShowCreatorInfo #CheckBox", EventData.of(KEY_SHOW_CREATOR_INFO, "#ShowCreatorInfo #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#IncludeContainerItems #CheckBox", EventData.of(KEY_INCLUDE_CONTAINERS, "#IncludeContainerItems #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AltKeybind #CheckBox", EventData.of(KEY_ALT_KEYBIND, "#AltKeybind #CheckBox.Value"), false);
        this.build(ref, store, commandBuilder, eventBuilder);
    }

    // TODO fuel
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull GuiData data) {
        super.handleDataEvent(ref, store, data);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        BetterItemViewerComponent settings = store.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        if (data.action != null) {
            if (data.action.equals(RESET_FILTERS_ACTION)) {
                settings.clearFilters();
                setInputValues(settings, commandBuilder);
            }
        }

        if (data.selectItem != null) {
            settings.selectedItem = data.selectItem;
            if (!settings.recentItems.contains(settings.selectedItem)) {
                settings.recentItems.addFirst(settings.selectedItem);
                if (settings.recentItems.size() > 24) {
                    settings.recentItems.removeLast();
                }
            }
        }

        if (data.setSearch != null) {
            settings.searchQuery = data.setSearch.trim().toLowerCase();
            settings.selectedPage = 0;
            commandBuilder.set("#SearchInput.Value", settings.searchQuery);
        }

        if (data.giveItem != null) {
            giveItem(ref, store, data.giveItem, false);
        }

        if (data.giveItemStack != null) {
            giveItem(ref, store, data.giveItemStack, true);
        }

        if (data.pinRecipe != null) {
            if (settings.pinnedRecipes.contains(data.pinRecipe)) {
                settings.pinnedRecipes.remove(data.pinRecipe);
            } else {
                settings.pinnedRecipes.add(data.pinRecipe);
            }
            HudUtils.updateHud(ref);
        }

        if (data.recipeInPage != null) {
            try {
                settings.selectedRecipeInPage += Integer.parseInt(data.recipeInPage);
            } catch (NumberFormatException e) {
                settings.selectedRecipeInPage = 0;
            }
        }

        if (data.recipeOutPage != null) {
            try {
                settings.selectedRecipeOutPage += Integer.parseInt(data.recipeOutPage);
            } catch (NumberFormatException e) {
                settings.selectedRecipeOutPage = 0;
            }
        }

        if (data.listPage != null) {
            try {
                settings.selectedPage += Integer.parseInt(data.listPage);
            } catch (NumberFormatException e) {
                settings.selectedPage = 0;
            }
        }

        if (data.searchQuery != null) {
            settings.searchQuery = data.searchQuery.trim().toLowerCase();
            settings.selectedPage = 0;
        }

        if (data.modFilter != null) {
            settings.modFilter = data.modFilter;
            settings.selectedPage = 0;
        }

        if (data.categoryFilter != null) {
            settings.categoryFilter = data.categoryFilter;
            settings.selectedPage = 0;
        }

        if (data.craftableFilter != null) {
            try {
                settings.craftableFilter = BetterItemViewerComponent.Filter.valueOf(data.craftableFilter);
            } catch (Exception _) {
            }
            settings.selectedPage = 0;
        }

        if (data.pinnedFilter != null) {
            try {
                settings.pinnedFilter = BetterItemViewerComponent.Filter.valueOf(data.pinnedFilter);
            } catch (Exception _) {
            }
            settings.selectedPage = 0;
        }

        if (data.gridLayout != null) {
            try {
                String[] parts = data.gridLayout.split("x");

                settings.itemListColumns = Math.max(Integer.parseInt(parts[0]), 1);
                settings.itemListRows = Math.max(Integer.parseInt(parts[1]), 1);
            } catch (Exception _) {

            }
            settings.selectedPage = 0;
        }

        if (data.sortMode != null) {
            settings.sortMode = data.sortMode;
            settings.selectedPage = 0;
        }

        if (data.altKeybind != null) {
            settings.altKeybind = data.altKeybind;
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }

        if (data.showSalvagerRecipes != null) {
            settings.showSalvagerRecipes = data.showSalvagerRecipes;
        }

        if (data.showHiddenItems != null) {
            settings.showHiddenItems = data.showHiddenItems;
        }

        if (data.showCreatorInfo != null) {
            settings.showCreatorInfo = data.showCreatorInfo;
        }

        if (data.includeContainers != null) {
            settings.includeContainers = data.includeContainers;
        }

        this.build(ref, store, commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void setInputValues(BetterItemViewerComponent settings, UICommandBuilder commandBuilder) {
        commandBuilder.set("#SearchInput.Value", settings.searchQuery);
        commandBuilder.set("#ModFilter.Value", settings.modFilter);
        commandBuilder.set("#CategoryFilter.Value", settings.categoryFilter);
        commandBuilder.set("#SortMode.Value", settings.sortMode);
        commandBuilder.set("#FilterCraftable.Value", settings.craftableFilter.name());
        commandBuilder.set("#FilterPinned.Value", settings.pinnedFilter.name());
        commandBuilder.set("#GridLayout.Value", settings.itemListColumns + "x" + settings.itemListRows);
        commandBuilder.set("#ShowSalvager #CheckBox.Value", settings.showSalvagerRecipes);
        commandBuilder.set("#ShowHiddenItems #CheckBox.Value", settings.showHiddenItems);
        commandBuilder.set("#ShowCreatorInfo #CheckBox.Value", settings.showCreatorInfo);
        commandBuilder.set("#IncludeContainerItems #CheckBox.Value", settings.includeContainers);
        commandBuilder.set("#AltKeybind #CheckBox.Value", settings.altKeybind);
        commandBuilder.set("#CategoryFilter.Style.EntriesInViewport", 24);

        BetterItemViewerConfig config = BetterItemViewerPlugin.get().config();
        commandBuilder.set("#ModFilter.Visible", !config.disableModFilter);
        commandBuilder.set("#ShowHiddenItems.Visible", !config.disableHiddenItemsSetting);
        commandBuilder.set("#ShowCreatorInfo.Visible", !config.disableCreatorInfoSetting);
        commandBuilder.set("#IncludeContainerItems.Visible", !config.disableIncludeContainersSetting);
        commandBuilder.set("#AltKeybind.Visible", !config.disableKeybind);
    }

    private void giveItem(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, String itemId, boolean maxStack) {
        if (!ref.isValid()) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;
        if (!player.hasPermission("drex.betteritemviewer.give")) {
            return;
        }

        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) return;
        ItemStack stack = new ItemStack(itemId);
        if (maxStack) {
            stack = stack.withQuantity(item.getMaxStack());
        }
        ItemContainer itemContainer = player.getInventory().getCombinedHotbarFirst();
        itemContainer.addItemStack(stack);
    }

    private void build(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        if (!ref.isValid()) return;
        BetterItemViewerComponent settings = store.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());
        NearbyContainersComponent nearbyContainers = store.ensureAndGetComponent(ref, NearbyContainersComponent.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        this.buildList(settings, commandBuilder, eventBuilder);
        this.buildStats(player, settings, nearbyContainers, commandBuilder, eventBuilder);
    }

    private void buildList(BetterItemViewerComponent settings, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        List<Item> items = new LinkedList<>(Item.getAssetMap().getAssetMap().values());
        Set<String> modItems = Item.getAssetMap().getKeysForPack(settings.modFilter);
        Set<String> pinnedOutputItems = settings.pinnedRecipes.stream()
            .map(key -> CraftingRecipe.getAssetMap().getAsset(key))
            .filter(Objects::nonNull)
            .flatMap(craftingRecipe -> Arrays.stream(craftingRecipe.getOutputs()))
            .map(MaterialQuantity::getItemId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        items.removeIf(item -> {
            if (item.getId().equals("Unknown")) return true;
            var itemName = I18nModule.get().getMessage(this.playerRef.getLanguage(), item.getTranslationKey());

            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);

            if (quality != null && (quality.isHiddenFromSearch() || BetterItemViewerPlugin.get().config().hiddenQualities.contains(quality.getId())) && !settings.showHiddenItems) {
                return true;
            }

            if (modItems != null && !modItems.contains(item.getId())) {
                return true;
            }

            if (!settings.categoryFilter.isEmpty()) {
                if (item.getCategories() == null) return true;

                boolean matchesCategory = false;
                for (String category : item.getCategories()) {
                    if (category.startsWith(settings.categoryFilter)) {
                        matchesCategory = true;
                        break;
                    }
                }
                if (!matchesCategory) {
                    return true;
                }
            }

            if (settings.craftableFilter != BetterItemViewerComponent.Filter.ALL) {
                boolean hasRecipe = !ItemManager.get().getOrCreateDetails(item.getId()).craftingRecipes.isEmpty();
                if (hasRecipe && settings.craftableFilter == BetterItemViewerComponent.Filter.NO) {
                    return true;
                } else if (!hasRecipe && settings.craftableFilter == BetterItemViewerComponent.Filter.YES) {
                    return true;
                }
            }

            if (settings.pinnedFilter != BetterItemViewerComponent.Filter.ALL) {
                boolean isPinned = pinnedOutputItems.contains(item.getId());
                if (isPinned && settings.pinnedFilter == BetterItemViewerComponent.Filter.NO) {
                    return true;
                } else if (!isPinned && settings.pinnedFilter == BetterItemViewerComponent.Filter.YES) {
                    return true;
                }
            }

            if (!settings.searchQuery.isEmpty()) {
                String searchQuery = settings.searchQuery;
                if (searchQuery.startsWith("#")) {
                    searchQuery = searchQuery.substring(1);

                    ItemResourceType[] resourceTypes = item.getResourceTypes();
                    if (resourceTypes == null) return true;

                    boolean matchesTag = false;
                    for (ItemResourceType resourceType : resourceTypes) {
                        if (resourceType.id != null && resourceType.id.toLowerCase().contains(searchQuery)) {
                            matchesTag = true;
                            break;
                        }
                    }
                    if (!matchesTag) {
                        return true;
                    }
                } else {
                    boolean matchesNameOrId = item.getId().toLowerCase().contains(searchQuery);
                    if (itemName != null && itemName.toLowerCase().contains(searchQuery)) {
                        matchesNameOrId = true;
                    }
                    return !matchesNameOrId;
                }

            }

            return false;
        });

        Comparator<Item> comparator = COMPARATORS.getOrDefault(settings.sortMode, CATEGORY_COMPARATOR).apply(playerRef);
        items.sort(comparator);

        if (settings.selectedItem == null && !items.isEmpty()) {
            settings.selectedItem = items.getFirst().getId();
        }

        int entriesPerPage = settings.itemListRows * settings.itemListColumns;

        int size = items.size();
        int pages = Math.ceilDiv(size, entriesPerPage);
        if (settings.selectedPage >= pages) {
            settings.selectedPage = 0;
        } else if (settings.selectedPage < 0) {
            settings.selectedPage = Math.max(0, pages - 1);
        }

        items = items.stream().skip((long) settings.selectedPage * entriesPerPage).limit(entriesPerPage).toList();

        commandBuilder.set("#ListSection #PaginationInfo.Text", (settings.selectedPage + 1) + " / " + pages);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ListSection #PrevPageButton", EventData.of(KEY_LIST_PAGE, String.valueOf(-1)));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ListSection #NextPageButton", EventData.of(KEY_LIST_PAGE, String.valueOf(+1)));

        // Mod filter
        ObjectArrayList<DropdownEntryInfo> mods = new ObjectArrayList<>();
        mods.add(new DropdownEntryInfo(LocalizableString.fromString("All Mods"), ""));

        for (AssetPack assetPack : AssetModule.get().getAssetPacks()) {
            Set<String> keysForPack = Item.getAssetMap().getKeysForPack(assetPack.getName());
            if (keysForPack == null || keysForPack.isEmpty()) continue;
            mods.add(new DropdownEntryInfo(LocalizableString.fromString(assetPack.getManifest().getName()), assetPack.getName()));
        }
        commandBuilder.set("#ModFilter.Entries", mods);

        // Category filter
        ObjectArrayList<DropdownEntryInfo> categories = new ObjectArrayList<>();
        categories.add(new DropdownEntryInfo(LocalizableString.fromString("All Categories"), ""));

        ItemCategory.getAssetMap().getAssetMap().values().forEach(category -> {

            categories.add(new DropdownEntryInfo(LocalizableString.fromString(category.getId()), category.getId()));
            ItemCategory[] children = category.getChildren();
            if (children != null) {
                for (ItemCategory child : children) {
                    categories.add(new DropdownEntryInfo(LocalizableString.fromString("    " + child.getId().toLowerCase()), category.getId() + "." + child.getId()));
                }
            }
        });
        commandBuilder.set("#CategoryFilter.Entries", categories);

        // Sorting
        ObjectArrayList<DropdownEntryInfo> sortModes = new ObjectArrayList<>();
        COMPARATORS.forEach((name, _) -> sortModes.add(new DropdownEntryInfo(LocalizableString.fromString(name), name)));
        commandBuilder.set("#SortMode.Entries", sortModes);

        // Craftable
        ObjectArrayList<DropdownEntryInfo> craftable = new ObjectArrayList<>();
        for (BetterItemViewerComponent.Filter value : BetterItemViewerComponent.Filter.values()) {
            craftable.add(new DropdownEntryInfo(LocalizableString.fromString("Craftable: " + value.name()), value.name()));
        }
        commandBuilder.set("#FilterCraftable.Entries", craftable);

        // Pinned
        ObjectArrayList<DropdownEntryInfo> pinned = new ObjectArrayList<>();
        for (BetterItemViewerComponent.Filter value : BetterItemViewerComponent.Filter.values()) {
            pinned.add(new DropdownEntryInfo(LocalizableString.fromString("Pinned: " + value.name()), value.name()));
        }
        commandBuilder.set("#FilterPinned.Entries", pinned);


        // Grid Layout
        ObjectArrayList<DropdownEntryInfo> gridLayouts = new ObjectArrayList<>();
        for (int cols = 5; cols <= 10; cols++) {
            for (int rows = 5; rows <= 10; rows++) {
                gridLayouts.add(new DropdownEntryInfo(LocalizableString.fromString("Grid Layout: " + cols + "x" + rows), cols + "x" + rows));
            }
        }

        commandBuilder.set("#GridLayout.Entries", gridLayouts);

        commandBuilder.set("#ResetFiltersButton.Disabled", !settings.hasFilters());

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ModFilter", EventData.of(KEY_MOD_FILTER, "#ModFilter.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CategoryFilter", EventData.of(KEY_CATEGORY_FILTER, "#CategoryFilter.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SortMode", EventData.of(KEY_SORT_MODE, "#SortMode.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FilterCraftable", EventData.of(KEY_CRAFTABLE_FILTER, "#FilterCraftable.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FilterPinned", EventData.of(KEY_PINNED_FILTER, "#FilterPinned.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#GridLayout", EventData.of(KEY_GRID_LAYOUT, "#GridLayout.Value"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetFiltersButton", EventData.of(KEY_ACTION, RESET_FILTERS_ACTION));

        this.updateItemList(settings, items, commandBuilder, eventBuilder);
        this.updateRecentItemList(settings, commandBuilder, eventBuilder);

        Anchor itemSectionAnchor = new Anchor();
        itemSectionAnchor.setHeight(Value.of(settings.itemListRows * 100));
        itemSectionAnchor.setWidth(Value.of(settings.itemListColumns * 128));
        commandBuilder.setObject("#ItemSection.Anchor", itemSectionAnchor);

        Anchor itemInfoSectionAnchor = new Anchor();
        itemInfoSectionAnchor.setHeight(Value.of((settings.itemListRows * 100) + 40));
        itemInfoSectionAnchor.setWidth(Value.of(550));
        commandBuilder.setObject("#ItemInfoSection.Anchor", itemInfoSectionAnchor);
    }

    private void buildStats(Player player, BetterItemViewerComponent settings, NearbyContainersComponent nearbyContainers, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#ItemStats");
        if (settings.selectedItem == null) return;
        Item selectedItem = Item.getAssetMap().getAsset(settings.selectedItem);
        if (selectedItem == null) return;
        commandBuilder.set("#ItemTitle.Visible", true);
        commandBuilder.set("#ItemTitle #ItemIcon.ItemId", selectedItem.getId());
        commandBuilder.set("#ItemTitle #ItemName.TextSpans", Message.translation(selectedItem.getTranslationKey()));
        commandBuilder.set("#ItemTitle #ItemId.TextSpans", Message.raw("ID: " + selectedItem.getId()));
        AtomicInteger index = new AtomicInteger(0);

        if (settings.showCreatorInfo) {
            addCreatorInfo(selectedItem, commandBuilder, index);
        }
        ItemContainer itemContainer = player.getInventory().getCombinedEverything();
        if (settings.includeContainers) {
            itemContainer = new CombinedItemContainer(itemContainer, nearbyContainers.itemContainer);
        }
        addDescription(selectedItem, commandBuilder, index);
        addGeneral(selectedItem, commandBuilder, index);
        addArmorInfo(selectedItem, commandBuilder, index);
        addWeaponInfo(selectedItem, commandBuilder, index);
        addToolInfo(selectedItem, commandBuilder, index);
        addNpcLoot(selectedItem, commandBuilder, index);
        addRecipes(itemContainer, settings, selectedItem, commandBuilder, eventBuilder);

        boolean canCheat = player.hasPermission("drex.betteritemviewer.give");

        commandBuilder.set("#GiveItemButton.Visible", canCheat);
        commandBuilder.set("#GiveItemStackButton.Visible", canCheat);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiveItemButton", EventData.of(KEY_GIVE_ITEM, selectedItem.getId()));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiveItemStackButton", EventData.of(KEY_GIVE_ITEM_STACK, selectedItem.getId()));

        commandBuilder.set("#ItemStats.Visible", true);
    }

    private void updateItemList(BetterItemViewerComponent settings, Collection<Item> items, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#ItemSection");

        int rowIndex = 0;
        int cardsInCurrentRow = 0;

        for (Item item : items) {
            if (cardsInCurrentRow == 0) {
                commandBuilder.appendInline("#ItemSection", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            commandBuilder.append("#ItemSection[" + rowIndex + "]", "Pages/Drex_BetterItemViewer_Item.ui");

            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                String slotTexture = quality.getSlotTexture();
                commandBuilder.set("#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "].AssetPath", slotTexture);
            }

            commandBuilder.set("#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.ItemId", item.getId());
            commandBuilder.set("#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemName.TextSpans", Message.translation(item.getTranslationKey()));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemButton", EventData.of(KEY_ITEM, item.getId()));
            eventBuilder.addEventBinding(CustomUIEventBindingType.RightClicking, "#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemButton", EventData.of(KEY_GIVE_ITEM, item.getId()));
            ++cardsInCurrentRow;
            if (cardsInCurrentRow >= settings.itemListColumns) {
                cardsInCurrentRow = 0;
                ++rowIndex;
            }
        }
    }

    private void updateRecentItemList(BetterItemViewerComponent settings, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#RecentItemSection");
        List<Item> items = settings.recentItems.stream().map(s -> Item.getAssetMap().getAsset(s)).filter(Objects::nonNull).toList();

        int rowIndex = 0;
        int cardsInCurrentRow = 0;

        for (Item item : items) {
            if (cardsInCurrentRow == 0) {
                commandBuilder.appendInline("#RecentItemSection", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            commandBuilder.append("#RecentItemSection[" + rowIndex + "]", "Pages/Drex_BetterItemViewer_RecentItem.ui");

            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                String slotTexture = quality.getSlotTexture();
                commandBuilder.set("#RecentItemSection[" + rowIndex + "][" + cardsInCurrentRow + "].AssetPath", slotTexture);
            }

            commandBuilder.set("#RecentItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.ItemId", item.getId());
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RecentItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemButton", EventData.of(KEY_ITEM, item.getId()));
            eventBuilder.addEventBinding(CustomUIEventBindingType.RightClicking, "#RecentItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemButton", EventData.of(KEY_GIVE_ITEM, item.getId()));
            ++cardsInCurrentRow;
            if (cardsInCurrentRow >= 8) {
                cardsInCurrentRow = 0;
                ++rowIndex;
            }
        }
    }

    private void addCreatorInfo(Item selectedItem, UICommandBuilder commandBuilder, AtomicInteger index) {
        List<Message> lines = new ArrayList<>();
        lines.add(formatSimpleStat("ID", selectedItem.getId()));

        String[] categories = selectedItem.getCategories();
        if (categories != null) {
            lines.add(formatSimpleStat("Categories", String.join(", ", categories)));
        }
        String icon = selectedItem.getIcon();
        if (icon != null) {
            lines.add(formatSimpleStat("Icon", icon));
        }
        Path path = Item.getAssetMap().getPath(selectedItem.getId());
        if (path != null) {
            lines.add(formatSimpleStat("Asset", path.toString()));
        }

        String blockId = selectedItem.getBlockId();
        if (blockId != null) {
            lines.add(formatSimpleStat("Block ID", blockId));
        }
        String model = selectedItem.getModel();
        if (model != null) {
            lines.add(formatSimpleStat("Model", model));
        }

        String texture = selectedItem.getTexture();
        if (texture != null) {
            lines.add(formatSimpleStat("Texture", texture));
        }

        addStatsSection(commandBuilder, index, "Creator Info", lines);
    }

    private void addDescription(Item item, UICommandBuilder commandBuilder, AtomicInteger index) {
        String description = I18nModule.get().getMessage(this.playerRef.getLanguage(), item.getDescriptionTranslationKey());
        if (description == null) return;
        addStatsSection(commandBuilder, index, "Description", Message.translation(item.getDescriptionTranslationKey()));
    }

    private void addGeneral(Item item, UICommandBuilder commandBuilder, AtomicInteger index) {
        List<Message> lines = new ArrayList<>();

        double maxDurability = item.getMaxDurability();
        if (maxDurability > 0) {
            lines.add(formatSimpleStat("Durability", String.format("%.0f", maxDurability)));
        }

        lines.add(formatSimpleStat("Max Stack", item.getMaxStack() + ""));

        int qualityIndex = item.getQualityIndex();
        ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
        if (quality != null) {
            int rgb = ColorParseUtil.colorToARGBInt(quality.getTextColor());
            lines.add(Message.raw("Item Quality: ").insert(Message.translation(quality.getLocalizationKey()).color(new Color(rgb))));
        }

        String assetPackId = Item.getAssetMap().getAssetPack(item.getId());
        if (assetPackId != null) {
            AssetPack assetPack = AssetModule.get().getAssetPack(assetPackId);
            if (assetPack != null) {
                PluginManifest manifest = assetPack.getManifest();
                lines.add(formatSimpleStat("Mod", manifest.getName()));
            }
        }
        addStatsSection(commandBuilder, index, "General", lines);
    }

    private void addToolInfo(Item item, UICommandBuilder commandBuilder, AtomicInteger index) {
        ItemTool tool = item.getTool();
        if (tool == null) return;
        ItemToolSpec[] specs = tool.getSpecs();
        if (specs == null) return;

        List<Message> lines = new ArrayList<>();
        for (ItemToolSpec spec : specs) {
            lines.add(formatSimpleStat(spec.getGatherType(), String.format("%.2f", spec.getPower())));
        }
        addStatsSection(commandBuilder, index, "Breaking Speed", lines);
    }

    private void addArmorInfo(Item item, UICommandBuilder commandBuilder, AtomicInteger index) {
        ItemArmor armor = item.getArmor();
        if (armor == null) return;

        List<Message> lines = new ArrayList<>();

        Map<DamageCause, StaticModifier[]> damageEnhancementValues = armor.getDamageEnhancementValues();
        Map<DamageCause, StaticModifier[]> damageResistanceValues = armor.getDamageResistanceValues();
        Map<DamageClass, StaticModifier[]> damageClassEnhancement = armor.getDamageClassEnhancement();
        Int2ObjectMap<StaticModifier[]> statModifiers = armor.getStatModifiers();

        if (statModifiers != null) {
            for (Int2ObjectMap.Entry<StaticModifier[]> entry : statModifiers.int2ObjectEntrySet()) {
                EntityStatType entityStatType = EntityStatType.getAssetMap().getAsset(entry.getIntKey());
                if (entityStatType == null) continue;
                for (StaticModifier staticModifier : entry.getValue()) {
                    lines.add(formatSimpleStat(entityStatType.getId(), formatStaticModifier(staticModifier)));
                }
            }
        }

        if (damageResistanceValues != null) {
            for (Map.Entry<DamageCause, StaticModifier[]> damageCauseEntry : damageResistanceValues.entrySet()) {
                for (StaticModifier staticModifier : damageCauseEntry.getValue()) {
                    lines.add(formatSimpleStat(damageCauseEntry.getKey().getId() + " Resistance", formatStaticModifier(staticModifier)));
                }
            }
        }

        if (damageClassEnhancement != null) {
            for (Map.Entry<DamageClass, StaticModifier[]> damageClassEntry : damageClassEnhancement.entrySet()) {
                for (StaticModifier staticModifier : damageClassEntry.getValue()) {
                    lines.add(formatSimpleStat(firstLetterUppercase(damageClassEntry.getKey().name()) + " Attack Damage", formatStaticModifier(staticModifier)));
                }
            }
        }

        if (damageEnhancementValues != null) {
            for (Map.Entry<DamageCause, StaticModifier[]> damageCauseEntry : damageEnhancementValues.entrySet()) {
                for (StaticModifier staticModifier : damageCauseEntry.getValue()) {
                    lines.add(formatSimpleStat(firstLetterUppercase(damageCauseEntry.getKey().getId()) + " Attack Damage", formatStaticModifier(staticModifier)));
                }
            }
        }
        if (lines.isEmpty()) return;
        addStatsSection(commandBuilder, index, "Armor", lines);
    }

    private void addWeaponInfo(Item item, UICommandBuilder commandBuilder, AtomicInteger index) {
        List<Message> lines = new ArrayList<>();

        ItemDetails itemDetails = ItemManager.get().getOrCreateDetails(item.getId());

        combineDamageInteractions(itemDetails.damageInteractions).forEach((s, range) -> {
            lines.add(Message.raw(formatDamageInteractionVariable(s) + ": ").insert(Message.raw(range.format()).color("#ee7777")));
        });

        ItemWeapon weapon = item.getWeapon();
        if (weapon != null) {
            Int2ObjectMap<StaticModifier[]> statModifiers = weapon.getStatModifiers();
            if (statModifiers != null) {
                statModifiers.forEach((entityStatTypeIndex, staticModifiers) -> {
                    EntityStatType entityStatType = EntityStatType.getAssetMap().getAsset(entityStatTypeIndex);
                    for (StaticModifier staticModifier : staticModifiers) {
                        lines.add(Message.raw(entityStatType.getId() + ": ").insert(Message.raw(formatStaticModifier(staticModifier)).color("#eeeeaa")));
                    }
                });
            }
        }

        if (lines.isEmpty()) return;
        addStatsSection(commandBuilder, index, "Weapon", lines);
    }

    private void addNpcLoot(Item item, UICommandBuilder commandBuilder, AtomicInteger index) {
        ItemDetails itemDetails = ItemManager.get().getOrCreateDetails(item.getId());
        if (itemDetails.mobLoot.isEmpty()) return;

        AtomicInteger i = new AtomicInteger();
        String tag = "#MobLoot";
        commandBuilder.appendInline("#ItemStats", "Group " + tag + " {LayoutMode: Top; Padding: (Top: 12);}");
        commandBuilder.appendInline(tag, "Label {Style: (FontSize: 20, TextColor: #ffffff);}");
        commandBuilder.set(tag + "[" + i + "].TextSpans", Message.raw("Mob Loot"));
        i.getAndIncrement();


        itemDetails.mobLoot.forEach((roleName, mobDropDetails) -> {
            commandBuilder.append(tag, "Pages/Drex_BetterItemViewer_RoleEntry.ui");
            commandBuilder.set(tag + "[" + i.get() + "] #RoleIcon.AssetPath", "UI/Custom/Pages/Memories/npcs/" + roleName + ".png");
            commandBuilder.set(tag + "[" + i.get() + "] #RoleName.TextSpans", Message.translation(mobDropDetails.translationKey()));
            commandBuilder.set(tag + "[" + i.get() + "] #DropItemQuantity.TextSpans", Message.raw(mobDropDetails.range().format() + " Items").color("#aaaaaa"));
            i.getAndIncrement();
        });

        index.getAndIncrement();
    }

    private void addRecipes(ItemContainer itemContainer, BetterItemViewerComponent settings, Item item, UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        ItemDetails itemDetails = ItemManager.get().getOrCreateDetails(item.getId());
        addRecipes(itemContainer, settings, commandBuilder, eventBuilder, "Recipes", "#Recipes", itemDetails.craftingRecipes, settings.selectedRecipeInPage, currentPage -> settings.selectedRecipeInPage = currentPage, KEY_RECIPE_IN_PAGE);
        addRecipes(itemContainer, settings, commandBuilder, eventBuilder, "Usages", "#UsedIn", itemDetails.usageRecipes, settings.selectedRecipeOutPage, currentPage -> settings.selectedRecipeOutPage = currentPage, KEY_RECIPE_OUT_PAGE);
    }

    private void addRecipes(
        ItemContainer itemContainer, BetterItemViewerComponent settings, UICommandBuilder commandBuilder,
        UIEventBuilder eventBuilder, String title, String tag, Map<String, CraftingRecipe> craftingRecipesById,
        int currentPage, Consumer<Integer> pageChange, String eventKey
    ) {
        if (craftingRecipesById.isEmpty()) return;

        List<CraftingRecipe> craftingRecipes = craftingRecipesById.values().stream().filter(craftingRecipe -> {
            BenchRequirement[] benchRequirements = craftingRecipe.getBenchRequirement();
            if (benchRequirements == null) return true;
            for (BenchRequirement benchRequirement : benchRequirements) {
                if (benchRequirement.id != null && benchRequirement.id.equals("Salvagebench") && !settings.showSalvagerRecipes) {
                    return false;
                }
            }
            return true;
        }).toList();

        if (craftingRecipes.isEmpty()) return;


        AtomicInteger i = new AtomicInteger();
        commandBuilder.appendInline("#ItemStats", "Group " + tag + " {LayoutMode: Top; Padding: (Top: 12);}");
        commandBuilder.appendInline(tag, "Label {Style: (FontSize: 20, TextColor: #ffffff);}");
        commandBuilder.set(tag + "[" + i + "].TextSpans", Message.raw(title));
        i.getAndIncrement();

        if (currentPage >= craftingRecipes.size()) {
            pageChange.accept(0);
            currentPage = 0;
        } else if (currentPage < 0) {
            pageChange.accept(craftingRecipes.size() - 1);
            currentPage = craftingRecipes.size() - 1;
        }

        if (craftingRecipes.size() > 1) {
            commandBuilder.append(tag, "Pages/Drex_BetterItemViewer_RecipePagination.ui");
            commandBuilder.set(tag + "[" + i + "] #PaginationInfo.Text", (currentPage + 1) + " / " + craftingRecipes.size());
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, tag + "[" + i + "] #PrevPageButton", EventData.of(eventKey, String.valueOf(-1)));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, tag + "[" + i + "] #NextPageButton", EventData.of(eventKey, String.valueOf(+1)));

            i.getAndIncrement();
        }

        CraftingRecipe recipe = craftingRecipes.get(currentPage);

        commandBuilder.append(tag, "Pages/Drex_BetterItemViewer_RecipePin.ui");
        if (settings.pinnedRecipes.contains(recipe.getId())) {
            commandBuilder.set(tag + "[" + i + "] #PinRecipeButton.Text", "Unpin Recipe");
        } else {
            commandBuilder.set(tag + "[" + i + "] #PinRecipeButton.Text", "Pin Recipe");
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, tag + "[" + i + "] #PinRecipeButton", EventData.of(KEY_PIN_RECIPE, recipe.getId()));
        i.getAndIncrement();

        BenchRequirement[] benchRequirements = recipe.getBenchRequirement();
        if (benchRequirements != null && benchRequirements.length > 0) {
            for (BenchRequirement benchRequirement : recipe.getBenchRequirement()) {
                for (Item benchItem : Item.getAssetMap().getAssetMap().values()) {
                    String blockId = benchItem.getBlockId();
                    BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
                    if (blockType == null || blockType.getBench() == null) continue;
                    String id = blockType.getBench().getId();
                    if (Objects.equals(id, benchRequirement.id)) {
                        commandBuilder.append(tag, "Pages/Drex_BetterItemViewer_BenchEntry.ui");
                        commandBuilder.set(tag + "[" + i + "] #ItemIcon.ItemId", benchItem.getId());
                        Message requirementText = Message.translation(benchItem.getTranslationKey());
                        if (benchRequirement.requiredTierLevel > 0) {
                            requirementText.insert(" (Tier " + benchRequirement.requiredTierLevel + ")");
                        }

                        commandBuilder.set(tag + "[" + i + "] #ItemName.TextSpans", requirementText);
                        i.getAndIncrement();

                        break;
                    }
                }
            }
        }

        commandBuilder.appendInline(tag, "Label {Style: (FontSize: 18, TextColor: #aaaaaa);}");
        commandBuilder.set(tag + "[" + i + "].TextSpans", Message.raw("Input"));
        i.getAndIncrement();

        for (MaterialQuantity input : recipe.getInput()) {
            addMaterialQuantity(itemContainer, input, commandBuilder, eventBuilder, tag, true, i);
        }

        commandBuilder.appendInline(tag, "Label {Style: (FontSize: 18, TextColor: #aaaaaa);}");
        commandBuilder.set(tag + "[" + i + "].TextSpans", Message.raw("Output"));
        i.getAndIncrement();

        for (MaterialQuantity output : recipe.getOutputs()) {
            addMaterialQuantity(itemContainer, output, commandBuilder, eventBuilder, tag, false, i);
        }
    }

    private void addMaterialQuantity(ItemContainer itemContainer, MaterialQuantity materialQuantity, UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String tag, boolean countInventory, AtomicInteger i) {
        String itemId = materialQuantity.getItemId();
        String resourceTypeId = materialQuantity.getResourceTypeId();

        if (itemId != null) {
            Item inputItem = Item.getAssetMap().getAsset(itemId);
            if (inputItem == null) return;

            int count = itemContainer.countItemStacks(itemStack -> itemStack.getItemId().equals(itemId));

            commandBuilder.append(tag, "Pages/Drex_BetterItemViewer_RecipeEntry.ui");

            commandBuilder.set(tag + "[" + i + "] #ItemIcon.ItemId", itemId);
            commandBuilder.set(tag + "[" + i + "] #ItemIcon.Visible", true);
            String value = countInventory ? count + "/" + materialQuantity.getQuantity() + " " : materialQuantity.getQuantity() + " ";
            commandBuilder.set(tag + "[" + i + "] #ItemName.TextSpans", Message.translation(inputItem.getTranslationKey()));
            commandBuilder.set(tag + "[" + i + "] #ItemQuantity.TextSpans", Message.raw(value));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, tag + "[" + i + "] #ItemButton", EventData.of(KEY_ITEM, itemId));
            if (countInventory) {
                if (count < materialQuantity.getQuantity()) {
                    commandBuilder.set(tag + "[" + i + "] #ItemQuantity.Style.TextColor", "#ff2222aa");
                } else {
                    commandBuilder.set(tag + "[" + i + "] #ItemQuantity.Style.TextColor", "#22ff22aa");
                }
            }

            i.getAndIncrement();
        } else if (resourceTypeId != null) {
            ResourceType resourceType = ResourceType.getAssetMap().getAsset(resourceTypeId);
            if (resourceType == null) return;

            int count = itemContainer.countItemStacks(itemStack -> {
                ItemResourceType[] resourceTypes = itemStack.getItem().getResourceTypes();
                if (resourceTypes != null) {
                    for (ItemResourceType type : resourceTypes) {
                        if (type.id != null && type.id.equals(resourceTypeId)) return true;
                    }
                }
                return false;
            });
            String icon = resourceType.getIcon();
            if (icon == null) {
                icon = "Icons/ResourceTypes/Unknown.png";
            }

            commandBuilder.append(tag, "Pages/Drex_BetterItemViewer_RecipeEntry.ui");
            commandBuilder.set(tag + "[" + i + "] #ResourceIcon.AssetPath", icon);
            commandBuilder.set(tag + "[" + i + "] #ResourceIcon.Visible", true);
            String value = countInventory ? count + "/" + materialQuantity.getQuantity() + " " : materialQuantity.getQuantity() + " ";
            commandBuilder.set(tag + "[" + i + "] #ItemName.TextSpans", Message.translation("server.resourceType." + resourceTypeId + ".name"));
            commandBuilder.set(tag + "[" + i + "] #ItemQuantity.TextSpans", Message.raw(value));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, tag + "[" + i + "] #ItemButton", EventData.of(KEY_SET_SEARCH, "#" + resourceTypeId).append(KEY_ACTION, RESET_FILTERS_ACTION));
            if (countInventory) {
                if (count < materialQuantity.getQuantity()) {
                    commandBuilder.set(tag + "[" + i + "] #ItemQuantity.Style.TextColor", "#ff2222aa");
                } else {
                    commandBuilder.set(tag + "[" + i + "] #ItemQuantity.Style.TextColor", "#22ff22aa");
                }
            }
            i.getAndIncrement();
        }
    }

    private void addStatsSection(UICommandBuilder commandBuilder, AtomicInteger index, String title, Message stats) {
        commandBuilder.append("#ItemStats", "Pages/Drex_BetterItemViewer_StatsSection.ui");
        commandBuilder.set("#ItemStats[" + index.get() + "] #StatsSectionTitle.TextSpans", Message.raw(title));

        commandBuilder.set("#ItemStats[" + index.get() + "] #StatsSectionDescription.TextSpans", stats);
        index.getAndIncrement();
    }

    private void addStatsSection(UICommandBuilder commandBuilder, AtomicInteger index, String title, List<Message> lines) {
        Message stats = Message.empty();
        boolean first = true;
        for (Message line : lines) {
            if (first) {
                first = false;
            } else {
                stats.insert("\n");
            }
            stats.insert(line);
        }
        addStatsSection(commandBuilder, index, title, stats);
    }

    private static String firstLetterUppercase(String input) {
        String result = input.toLowerCase();
        if (!result.isEmpty()) {
            return result.substring(0, 1).toUpperCase() + result.substring(1);
        }
        return result;
    }

    private static String formatStaticModifier(StaticModifier staticModifier) {
        StaticModifier.CalculationType calculationType = staticModifier.getCalculationType();
        String value = "";

        switch (calculationType) {
            case ADDITIVE -> value = String.format("%.0f", staticModifier.getAmount());
            case MULTIPLICATIVE -> value = String.format("%.0f", staticModifier.getAmount() * 100) + "%";
        }
        if (staticModifier.getAmount() >= 0) {
            return "+" + value;
        }
        return value;
    }

    private static Message formatSimpleStat(String type, String value) {
        return Message.raw(type + ": ").insert(Message.raw(value).color("#eeeeee"));
    }

    private static Map<String, Range> combineDamageInteractions(Map<String, Range> damageInteractions) {
        Map<String, Range> result = new LinkedHashMap<>();

        Set<String> handledInteractions = new HashSet<>();

        // A map from truncated id + damage to their original id
        Map<Map.Entry<String, Range>, List<String>> directionGroups = new LinkedHashMap<>();

        damageInteractions.forEach((interactionId, damageRange) -> {
            if (handledInteractions.contains(interactionId)) return;
            String removedDirections = interactionId.replace("_Up", "").replace("_Down", "").replace("_Left", "").replace("_Right", "");

            directionGroups.computeIfAbsent(Map.entry(removedDirections, damageRange), (key) -> new ArrayList<>()).add(interactionId);
        });

        directionGroups.forEach((interactionGroup, interactionIds) -> {
            if (interactionIds.size() > 1) {
                handledInteractions.addAll(interactionIds);
                result.put(interactionGroup.getKey(), interactionGroup.getValue());
            }
        });

        // Group strength into one
        Map<String, Range> strengthGroups = new LinkedHashMap<>();
        damageInteractions.forEach((interactionId, damageRange) -> {
            if (handledInteractions.contains(interactionId)) return;
            if (!interactionId.contains("_Damage_Strength_")) return;

            String removedDamageStrength = interactionId.replaceAll("_Damage_Strength_\\d+", "");

            handledInteractions.add(interactionId);
            strengthGroups.compute(removedDamageStrength, (_, oldDamageRange) -> {
                if (oldDamageRange != null) {
                    return oldDamageRange.merge(damageRange);
                } else {
                    return damageRange;
                }
            });
        });
        result.putAll(strengthGroups);


        damageInteractions.forEach((interactionId, damageInteraction) -> {
            if (handledInteractions.contains(interactionId)) return;
            handledInteractions.add(interactionId);
            result.put(interactionId, damageInteraction);
        });

        return result;
    }

    private static String formatDamageInteractionVariable(String damageInteractionVar) {
        return damageInteractionVar.replace("_Damage", "").replace("_", " ");
    }

    public static class GuiData {
        static final String KEY_SEARCH_QUERY = "@SearchQuery";
        static final String KEY_MOD_FILTER = "@ModFilter";
        static final String KEY_CATEGORY_FILTER = "@CategoryFilter";
        static final String KEY_CRAFTABLE_FILTER = "@CraftableFilter";
        static final String KEY_PINNED_FILTER = "@PinnedFilter";
        static final String KEY_GRID_LAYOUT = "@GridLayout";
        static final String KEY_SORT_MODE = "@SortMode";
        static final String KEY_ALT_KEYBIND = "@AltKeybind";
        static final String KEY_SHOW_SALVAGER_RECIPES = "@ShowSalvagerRecipes";
        static final String KEY_SHOW_HIDDEN_ITEMS = "@ShowHiddenItems";
        static final String KEY_SHOW_CREATOR_INFO = "@ShowCreatorInfo";
        static final String KEY_INCLUDE_CONTAINERS = "@IncludeContainers";
        static final String KEY_ITEM = "SelectItem";
        static final String KEY_SET_SEARCH = "SetSearch";
        static final String KEY_GIVE_ITEM = "GiveItem";
        static final String KEY_GIVE_ITEM_STACK = "GiveItemStack";
        static final String KEY_LIST_PAGE = "ListPage";
        static final String KEY_PIN_RECIPE = "PinRecipe";
        static final String KEY_RECIPE_IN_PAGE = "RecipeInPage";
        static final String KEY_RECIPE_OUT_PAGE = "RecipeOutPage";
        static final String KEY_ACTION = "Action";
        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.builder(GuiData.class, GuiData::new)
            .addField(new KeyedCodec<>(KEY_SEARCH_QUERY, Codec.STRING), (guiData, s) -> guiData.searchQuery = s, guiData -> guiData.searchQuery)
            .addField(new KeyedCodec<>(KEY_MOD_FILTER, Codec.STRING), (guiData, s) -> guiData.modFilter = s, guiData -> guiData.modFilter)
            .addField(new KeyedCodec<>(KEY_CATEGORY_FILTER, Codec.STRING), (guiData, s) -> guiData.categoryFilter = s, guiData -> guiData.categoryFilter)
            .addField(new KeyedCodec<>(KEY_CRAFTABLE_FILTER, Codec.STRING), (guiData, s) -> guiData.craftableFilter = s, guiData -> guiData.craftableFilter)
            .addField(new KeyedCodec<>(KEY_PINNED_FILTER, Codec.STRING), (guiData, s) -> guiData.pinnedFilter = s, guiData -> guiData.pinnedFilter)
            .addField(new KeyedCodec<>(KEY_GRID_LAYOUT, Codec.STRING), (guiData, s) -> guiData.gridLayout = s, guiData -> guiData.gridLayout)
            .addField(new KeyedCodec<>(KEY_SORT_MODE, Codec.STRING), (guiData, s) -> guiData.sortMode = s, guiData -> guiData.sortMode)
            .addField(new KeyedCodec<>(KEY_ALT_KEYBIND, Codec.BOOLEAN), (guiData, s) -> guiData.altKeybind = s, guiData -> guiData.altKeybind)
            .addField(new KeyedCodec<>(KEY_SHOW_SALVAGER_RECIPES, Codec.BOOLEAN), (guiData, s) -> guiData.showSalvagerRecipes = s, guiData -> guiData.showSalvagerRecipes)
            .addField(new KeyedCodec<>(KEY_SHOW_HIDDEN_ITEMS, Codec.BOOLEAN), (guiData, s) -> guiData.showHiddenItems = s, guiData -> guiData.showHiddenItems)
            .addField(new KeyedCodec<>(KEY_SHOW_CREATOR_INFO, Codec.BOOLEAN), (guiData, s) -> guiData.showCreatorInfo = s, guiData -> guiData.showCreatorInfo)
            .addField(new KeyedCodec<>(KEY_INCLUDE_CONTAINERS, Codec.BOOLEAN), (guiData, s) -> guiData.includeContainers = s, guiData -> guiData.includeContainers)
            .addField(new KeyedCodec<>(KEY_ITEM, Codec.STRING), (guiData, s) -> guiData.selectItem = s, guiData -> guiData.selectItem)
            .addField(new KeyedCodec<>(KEY_SET_SEARCH, Codec.STRING), (guiData, s) -> guiData.setSearch = s, guiData -> guiData.setSearch)
            .addField(new KeyedCodec<>(KEY_GIVE_ITEM, Codec.STRING), (guiData, s) -> guiData.giveItem = s, guiData -> guiData.giveItem)
            .addField(new KeyedCodec<>(KEY_GIVE_ITEM_STACK, Codec.STRING), (guiData, s) -> guiData.giveItemStack = s, guiData -> guiData.giveItemStack)
            .addField(new KeyedCodec<>(KEY_LIST_PAGE, Codec.STRING), (guiData, s) -> guiData.listPage = s, guiData -> guiData.listPage)
            .addField(new KeyedCodec<>(KEY_PIN_RECIPE, Codec.STRING), (guiData, s) -> guiData.pinRecipe = s, guiData -> guiData.pinRecipe)
            .addField(new KeyedCodec<>(KEY_RECIPE_IN_PAGE, Codec.STRING), (guiData, s) -> guiData.recipeInPage = s, guiData -> guiData.recipeInPage)
            .addField(new KeyedCodec<>(KEY_RECIPE_OUT_PAGE, Codec.STRING), (guiData, s) -> guiData.recipeOutPage = s, guiData -> guiData.recipeOutPage)
            .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (guiData, s) -> guiData.action = s, guiData -> guiData.action)
            .build();

        private String selectItem;
        private String setSearch;
        private String giveItem;
        private String giveItemStack;
        private String searchQuery;
        private String modFilter;
        private String categoryFilter;
        private String craftableFilter;
        public String pinnedFilter;
        private String gridLayout;
        private String sortMode;
        private Boolean altKeybind;
        private Boolean showSalvagerRecipes;
        private Boolean showHiddenItems;
        private Boolean showCreatorInfo;
        private Boolean includeContainers;
        private String listPage;
        private String pinRecipe;
        private String recipeInPage;
        private String recipeOutPage;
        private String action;

    }

}
