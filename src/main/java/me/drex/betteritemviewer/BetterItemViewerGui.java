package me.drex.betteritemviewer;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static me.drex.betteritemviewer.BetterItemViewerGui.SearchGuiData.KEY_ITEM;
import static me.drex.betteritemviewer.BetterItemViewerGui.SearchGuiData.KEY_SEARCH_QUERY;

public class BetterItemViewerGui extends InteractiveCustomUIPage<BetterItemViewerGui.SearchGuiData> {

    private String searchQuery;
    private Item selectedItem;
    private static final String[] PRIMARY_INTERACTION_VARS = new String[]{
        "Swing_Left_Damage",
        "Longsword_Swing_Left_Damage",
        "Swing_Down_Left_Damage",
        "Axe_Swing_Down_Left_Damage",
        "Club_Swing_Left_Damage",
        "Spear_Stab_Damage"
    };

    public BetterItemViewerGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, String defaultSearchQuery) {
        super(playerRef, lifetime, SearchGuiData.CODEC);
        this.searchQuery = defaultSearchQuery;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        long start = System.currentTimeMillis();
        System.out.println(start + " Building BetterItemViewerGui");
        uiCommandBuilder.append("Pages/Drex_BetterItemViewer_Gui.ui");
        uiCommandBuilder.set("#SearchInput.Value", this.searchQuery);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput", EventData.of(KEY_SEARCH_QUERY, "#SearchInput.Value"), false);
        this.buildList(uiCommandBuilder, uiEventBuilder);
        this.buildStats(uiCommandBuilder, uiEventBuilder);
        System.out.println(System.currentTimeMillis() - start + "ms finishing BetterItemViewerGui");
    }

    // TODO fuel

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull SearchGuiData data) {
        super.handleDataEvent(ref, store, data);
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        if (data.item != null) {
            Main.ITEMS.stream().filter(item -> Objects.equals(item.getId(), data.item)).findFirst().ifPresent(item -> this.selectedItem = item);
            this.buildStats(commandBuilder, eventBuilder);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }
        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase();
            this.buildList(commandBuilder, eventBuilder);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }
    }

    private void buildStats(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        this.updateStats(this.selectedItem, commandBuilder, eventBuilder);
    }

    private void buildList(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        List<Item> items = new LinkedList<>(Main.ITEMS);

        items.removeIf(item -> {
            if (item.getId().equals("Unknown")) return true;
            var itemName = I18nModule.get().getMessage(this.playerRef.getLanguage(), item.getTranslationKey());

            if (!this.searchQuery.isEmpty()) {
                boolean matchesQuery = itemName != null && itemName.toLowerCase().contains(searchQuery) ||
                    item.getTranslationKey().toLowerCase().contains(searchQuery);
                if (itemName != null && itemName.toLowerCase().contains(searchQuery)) {
                    matchesQuery = true;
                }
                return !matchesQuery;
            }

            return false;
        });

        items.sort((item1, item2) -> {
            String name1 = Objects.requireNonNullElse(I18nModule.get().getMessage(this.playerRef.getLanguage(), item1.getTranslationKey()), item1.getTranslationKey());
            String name2 = Objects.requireNonNullElse(I18nModule.get().getMessage(this.playerRef.getLanguage(), item2.getTranslationKey()), item2.getTranslationKey());
            return name1.compareTo(name2);
        });

        if (selectedItem == null && !items.isEmpty()) {
            selectedItem = items.getFirst();
        }

        this.updateItemList(items, commandBuilder, eventBuilder);
    }

    private void updateStats(Item selectedItem, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#ItemStats");
        if (selectedItem == null) return;
        commandBuilder.set("#ItemTitle.Visible", true);
        commandBuilder.set("#ItemTitle #ItemIcon.ItemId", selectedItem.getId());
        commandBuilder.set("#ItemTitle #ItemName.TextSpans", Message.translation(selectedItem.getTranslationKey()));
        commandBuilder.set("#ItemTitle #ItemId.TextSpans", Message.raw("ID: " + selectedItem.getId()));
        addDescription(selectedItem, commandBuilder);
        addGeneral(selectedItem, commandBuilder);
        addWeaponInfo(selectedItem, commandBuilder);
        addToolInfo(selectedItem, commandBuilder);
        addNpcLoot(selectedItem, commandBuilder);

        commandBuilder.set("#ItemStats.Visible", true);
    }

    private void updateItemList(Collection<Item> items, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#ItemSection");

        int rowIndex = 0;
        int cardsInCurrentRow = 0;

        for (Item item : items) {
            if (cardsInCurrentRow == 0) {
                commandBuilder.appendInline("#ItemSection", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            commandBuilder.append("#ItemSection[" + rowIndex + "]", "Pages/Drex_BetterItemViewer_Item.ui");

            commandBuilder.set("#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.ItemId", item.getId());
            commandBuilder.set("#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemName.TextSpans", Message.translation(item.getTranslationKey()));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ItemSection[" + rowIndex + "][" + cardsInCurrentRow + "]", EventData.of(KEY_ITEM, item.getId()));
            ++cardsInCurrentRow;
            if (cardsInCurrentRow >= 7) {
                cardsInCurrentRow = 0;
                ++rowIndex;
            }
        }
    }

    private void addDescription(Item item, UICommandBuilder commandBuilder) {
        String description = I18nModule.get().getMessage(this.playerRef.getLanguage(), item.getDescriptionTranslationKey());
        if (description == null) return;

        int i = 0;
        commandBuilder.appendInline("#ItemStats", "Group #Description {LayoutMode: Top;}");
        commandBuilder.appendInline("#Description", "Label {Style: (FontSize: 20, TextColor: #ffffff);}");
        commandBuilder.set("#Description[" + i + "].TextSpans", Message.raw("Description"));
        i++;

        commandBuilder.appendInline("#Description", "Label {Style: (FontSize: 16, TextColor: #aaaaaa);}");
        commandBuilder.set("#Description[" + i + "].TextSpans", Message.translation(item.getDescriptionTranslationKey()));
        i++;
    }

    private void addToolInfo(Item item, UICommandBuilder commandBuilder) {
        ItemTool tool = item.getTool();
        if (tool == null) return;
        ItemToolSpec[] specs = tool.getSpecs();
        if (specs == null) return;

        int i = 0;
        commandBuilder.appendInline("#ItemStats", "Group #Tools {LayoutMode: Top;}");
        commandBuilder.appendInline("#Tools", "Label {Style: (FontSize: 20, TextColor: #ffffff);}");
        commandBuilder.set("#Tools[" + i + "].TextSpans", Message.raw("Breaking Speed"));
        i++;

        for (ItemToolSpec spec : specs) {
            commandBuilder.appendInline("#Tools", "Label {Style: (FontSize: 16, TextColor: #aaaaaa);}");
            commandBuilder.set("#Tools[" + i + "].TextSpans", Message.raw(spec.getGatherType() + ": " + String.format("%.2f", spec.getPower())));
            i++;
        }
    }

    private void addWeaponInfo(Item item, UICommandBuilder commandBuilder) {
        // All of this is cursed, I am sorry for my crimes. But I don't think there is a better way at the moment.
        String initialDamageInteraction = null;
        Map<String, String> interactionVars = item.getInteractionVars();
        for (String primaryInteractionVar : PRIMARY_INTERACTION_VARS) {
            if (interactionVars.containsKey(primaryInteractionVar)) {
                initialDamageInteraction = interactionVars.get(primaryInteractionVar);
                break;
            }
        }
        if (initialDamageInteraction == null) return;

        String interactionId = String.format("*%s_Interactions_0", initialDamageInteraction);

        Interaction interaction = Interaction.getAssetMap().getAsset(interactionId);
        if (interaction instanceof DamageEntityInteraction damageEntityInteraction) {
            AtomicInteger i = new AtomicInteger();
            commandBuilder.appendInline("#ItemStats", "Group #Weapons {LayoutMode: Top;}");
            commandBuilder.appendInline("#Weapons", "Label {Style: (FontSize: 20, TextColor: #ffffff);}");
            commandBuilder.set("#Weapons[" + i + "].TextSpans", Message.raw("Weapon"));

            i.getAndIncrement();
            try {
                Field damageCalculatorField = DamageEntityInteraction.class.getDeclaredField("damageCalculator");
                damageCalculatorField.setAccessible(true);
                DamageCalculator damageCalculator = (DamageCalculator) damageCalculatorField.get(damageEntityInteraction);
                Field baseDamageRawField = DamageCalculator.class.getDeclaredField("baseDamageRaw");
                baseDamageRawField.setAccessible(true);
                Object2FloatMap<String> baseDamageRaw = (Object2FloatMap<String>) baseDamageRawField.get(damageCalculator);

                Field randomPercentageModifierField = DamageCalculator.class.getDeclaredField("randomPercentageModifier");
                randomPercentageModifierField.setAccessible(true);
                float randomPercentageModifier = randomPercentageModifierField.getFloat(damageCalculator);


                baseDamageRaw.forEach((damageCause, damage) -> {
                    String value;
                    if (randomPercentageModifier > 0) {
                        value = String.format("%.0f ±%.0f%%", damage, randomPercentageModifier * 100);
                    } else {
                        value = String.format("%.0f", damage);
                    }
                    commandBuilder.appendInline("#Weapons", "Label {Style: (FontSize: 16, TextColor: #aaaaaa);}");
                    commandBuilder.set("#Weapons[" + i + "].TextSpans", Message.raw(damageCause + ": " + value));
                    i.getAndIncrement();
                });

            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private boolean addNpcLoot(Item item, UICommandBuilder commandBuilder) {
        Map<String, Map.Entry<Integer, Integer>> itemDrops = Main.MOB_LOOT.get(item.getId());
        if (itemDrops == null) return false;
        AtomicInteger i = new AtomicInteger();
        commandBuilder.appendInline("#ItemStats", "Group #Loot {LayoutMode: Top;}");
        commandBuilder.appendInline("#Loot", "Label {Style: (FontSize: 20, TextColor: #ffffff);}");
        commandBuilder.set("#Loot[" + i + "].TextSpans", Message.raw("Mob Loot"));
        i.getAndIncrement();

        itemDrops.forEach((role, drop) -> {

            int min = drop.getKey();
            int max = drop.getValue();
            String value = String.format("%d - %d", min, max);
            if (Objects.equals(min, max)) {
                value = String.valueOf(min);
            }
            commandBuilder.appendInline("#Loot", "Label {Style: (FontSize: 16, TextColor: #aaaaaa);}");
            commandBuilder.set("#Loot[" + i + "].TextSpans", Message.translation(role).insert(": " + value));
            i.getAndIncrement();
        });
        return true;
    }

    private void addGeneral(Item item, UICommandBuilder commandBuilder) {
        double maxDurability = item.getMaxDurability();
        int i = 0;
        commandBuilder.appendInline("#ItemStats", "Group #General {LayoutMode: Top;}");
        commandBuilder.appendInline("#General", "Label {Style: (FontSize: 20, TextColor: #ffffff);}");
        commandBuilder.set("#General[" + i + "].TextSpans", Message.raw("General"));
        i++;

        if (maxDurability > 0) {
            commandBuilder.appendInline("#General", "Label {Style: (FontSize: 16, TextColor: #aaaaaa);}");
            commandBuilder.set("#General[" + i + "].TextSpans", Message.raw("Durability: " + String.format("%.0f", maxDurability)));
            i++;
        }

        commandBuilder.appendInline("#General", "Label {Style: (FontSize: 16, TextColor: #aaaaaa);}");
        commandBuilder.set("#General[" + i + "].TextSpans", Message.raw("Max Stack: " + item.getMaxStack()));
        i++;
    }

    public static class SearchGuiData {
        static final String KEY_ITEM = "Item";
        static final String KEY_SEARCH_QUERY = "@SearchQuery";
        public static final BuilderCodec<SearchGuiData> CODEC = BuilderCodec.builder(SearchGuiData.class, SearchGuiData::new)
            .addField(new KeyedCodec<>(KEY_SEARCH_QUERY, Codec.STRING), (searchGuiData, s) -> searchGuiData.searchQuery = s, searchGuiData -> searchGuiData.searchQuery)
            .addField(new KeyedCodec<>(KEY_ITEM, Codec.STRING), (searchGuiData, s) -> searchGuiData.item = s, searchGuiData -> searchGuiData.item).build();

        private String item;
        private String searchQuery;

    }

}
