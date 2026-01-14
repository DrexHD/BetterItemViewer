package me.drex.betteritemviewer;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.command.system.MatchResult;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;

public class BetterItemViewerGui extends InteractiveCustomUIPage<BetterItemViewerGui.SearchGuiData> {

    private String searchQuery = "";
    private final Map<String, Item> visibleItems = new HashMap<>();
    private static final String[] PRIMARY_INTERACTION_VARS = new String[] {
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
        uiCommandBuilder.append("Pages/Drex_BetterItemViewer_Gui.ui");
        uiCommandBuilder.set("#SearchInput.Value", this.searchQuery);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput", EventData.of("@SearchQuery", "#SearchInput.Value"), false);
        this.buildList(ref, uiCommandBuilder, uiEventBuilder, store);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull SearchGuiData data) {
        super.handleDataEvent(ref, store, data);
        if (data.item != null) {
            this.sendUpdate();
        }
        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase();
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildList(ref, commandBuilder, eventBuilder, store);
            this.sendUpdate(commandBuilder, eventBuilder, false);
        }
    }

    private void buildList(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        HashMap<String, Item> itemList = new HashMap<>(Main.ASSETS);
        Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());

        assert playerComponent != null;

        if (this.searchQuery.isEmpty()) {
            this.visibleItems.clear();
            this.visibleItems.putAll(itemList);
            //Collections.sort(this.visibleCommands);
        } else {
            ObjectArrayList<SearchResult> results = new ObjectArrayList<>();

            for (Map.Entry<String, Item> entry : itemList.entrySet()) {
                if (entry.getValue() != null) {
                    results.add(new SearchResult(entry.getKey(), MatchResult.EXACT));
                }
            }

            String[] terms = this.searchQuery.split(" ");

            for (int termIndex = 0; termIndex < terms.length; ++termIndex) {
                String term = terms[termIndex].toLowerCase(Locale.ENGLISH);

                for (int cmdIndex = results.size() - 1; cmdIndex >= 0; --cmdIndex) {
                    SearchResult result = results.get(cmdIndex);
                    Item item = itemList.get(result.name);
                    MatchResult match = MatchResult.NONE;
                    if (item != null) {
                        var message = I18nModule.get().getMessage(this.playerRef.getLanguage(), item.getTranslationKey());
                        match = message != null && message.toLowerCase(Locale.ENGLISH).contains(term) ? MatchResult.EXACT : MatchResult.NONE;
                    }

                    if (match == MatchResult.NONE) {
                        results.remove(cmdIndex);
                    } else {
                        result.match = result.match.min(match);
                    }
                }
            }

            results.sort(SearchResult.COMPARATOR);
            this.visibleItems.clear();

            for (SearchResult result : results) {
                this.visibleItems.put(result.name, itemList.get(result.name));
            }
        }
        this.buildButtons(this.visibleItems, playerComponent, commandBuilder, eventBuilder);
    }

    private void buildButtons(Map<String, Item> items, @Nonnull Player playerComponent, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        commandBuilder.clear("#SubcommandCards");
        commandBuilder.set("#SubcommandSection.Visible", true);
        int rowIndex = 0;
        int cardsInCurrentRow = 0;

        for (Map.Entry<String, Item> entry : items.entrySet()) {
            Item item = entry.getValue();

            if (cardsInCurrentRow == 0) {
                commandBuilder.appendInline("#SubcommandCards", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
            }

            commandBuilder.append("#SubcommandCards[" + rowIndex + "]", "Pages/Drex_BetterItemViewer_SearchItemIcon.ui");

            var tooltip = Message.empty();
            tooltip.insert(Message.translation(item.getTranslationKey()).bold(true)).insert("\n");
            boolean hasInfo = false;

            hasInfo |= addToolInfo(item, tooltip);
            hasInfo |= addDamageInfo(item, tooltip);

            if (!hasInfo) continue;

            addTooltipCategory(tooltip, "General");
            addTooltipLine(tooltip, "Durability", String.format("%.0f", item.getMaxDurability()));

            commandBuilder.set("#SubcommandCards[" + rowIndex + "][" + cardsInCurrentRow + "].TooltipTextSpans", tooltip);
            commandBuilder.set("#SubcommandCards[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemIcon.ItemId", entry.getKey());
            commandBuilder.set("#SubcommandCards[" + rowIndex + "][" + cardsInCurrentRow + "] #ItemName.TextSpans", Message.translation(item.getTranslationKey()));
            //commandBuilder.set("#SubcommandCards[" + rowIndex + "][" + cardsInCurrentRow + "] #SubcommandUsage.TextSpans", this.getSimplifiedUsage(item, playerComponent));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SubcommandCards[" + rowIndex + "][" + cardsInCurrentRow + "]", EventData.of("Item", entry.getKey()));
            ++cardsInCurrentRow;
            if (cardsInCurrentRow >= 7) {
                cardsInCurrentRow = 0;
                ++rowIndex;
            }
        }
    }

    private boolean addToolInfo(Item item, Message tooltip) {
        ItemTool tool = item.getTool();
        if (tool == null) return false;
        ItemToolSpec[] specs = tool.getSpecs();
        if (specs == null) return false;
        addTooltipCategory(tooltip, "Tool");

        for (ItemToolSpec spec : specs) {
            addTooltipLine(tooltip, spec.getGatherType(), String.format("%.2f", spec.getPower()));
        }
        tooltip.insert("\n");
        return true;
    }

    private boolean addDamageInfo(Item item, Message tooltip) {
        // All of this is cursed, I am sorry for my crimes. But I don't think there is a better way at the moment.
        String initialDamageInteraction = null;
        Map<String, String> interactionVars = item.getInteractionVars();
        for (String primaryInteractionVar : PRIMARY_INTERACTION_VARS) {
            if (interactionVars.containsKey(primaryInteractionVar)) {
                initialDamageInteraction = interactionVars.get(primaryInteractionVar);
                break;
            }
        }
        if (initialDamageInteraction == null) return false;

        String interactionId = String.format("*%s_Interactions_0", initialDamageInteraction);

        Interaction interaction = Interaction.getAssetMap().getAsset(interactionId);
        if (interaction instanceof DamageEntityInteraction damageEntityInteraction) {
            addTooltipCategory(tooltip, "Damage");
            try {
                Field damageCalculatorField = DamageEntityInteraction.class.getDeclaredField("damageCalculator");
                damageCalculatorField.setAccessible(true);
                DamageCalculator damageCalculator = (DamageCalculator) damageCalculatorField.get(damageEntityInteraction);
                Field baseDamageRawField = DamageCalculator.class.getDeclaredField("baseDamageRaw");
                baseDamageRawField.setAccessible(true);
                Object2FloatMap<String> baseDamageRaw = (Object2FloatMap<String>) baseDamageRawField.get(damageCalculator);

                baseDamageRaw.forEach((damageCause, damage) -> addTooltipLine(tooltip, damageCause, String.format("%.0f", damage)));

            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            tooltip.insert("\n");
            return true;
        } else {
            return false;
        }
    }

    private void addTooltipCategory(Message tooltip, String category) {
        tooltip.insert(Message.raw(category).bold(true).color(Color.GRAY)).insert("\n");
    }

    private void addTooltipLine(Message tooltip, String label, String value) {
        tooltip.insert(Message.raw(label + ": ").bold(true).color("#93844c")).insert(value).insert("\n");
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

    private static class SearchResult {
        public static final Comparator<SearchResult> COMPARATOR = Comparator.comparing((o) -> o.match);
        private final String name;
        private MatchResult match;

        public SearchResult(String name, MatchResult match) {
            this.name = name;
            this.match = match;
        }
    }

}
