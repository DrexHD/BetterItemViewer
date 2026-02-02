package me.drex.betteritemviewer.ui.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.drex.betteritemviewer.component.BetterItemViewerComponent;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

public class PinnedRecipesHud extends CustomUIHud {
    public PinnedRecipesHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Huds/Drex_BetterItemViewer_Container.ui");
        Ref<EntityStore> ref = getPlayerRef().getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        BetterItemViewerComponent component = store.ensureAndGetComponent(ref, BetterItemViewerComponent.getComponentType());
        AtomicInteger index = new AtomicInteger(0);
        for (String pinnedRecipe : component.pinnedRecipes) {
            buildRecipe(player, pinnedRecipe, commandBuilder, index);
        }
    }

    public void buildRecipe(Player player, String pinnedRecipe, UICommandBuilder commandBuilder, AtomicInteger index) {
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(pinnedRecipe);
        if (recipe == null) return;

        commandBuilder.append("#Recipes", "Huds/Drex_BetterItemViewer_Recipe.ui");
        String recipeTag = "#Recipes[" + index.get() + "]";
        index.getAndIncrement();

        AtomicInteger i = new AtomicInteger(0);

        String tag = "#Inputs";
        commandBuilder.appendInline(recipeTag + " #InputItems", "Group " + tag + " {LayoutMode: Top;}");

        for (MaterialQuantity materialQuantity : recipe.getInput()) {
            addMaterialQuantity(player, materialQuantity, commandBuilder, recipeTag + " " + tag, i);
        }

        for (MaterialQuantity output : recipe.getOutputs()) {
            String itemId = output.getItemId();
            if (itemId == null) continue;
            commandBuilder.set(recipeTag + " #OutputItemIcon.ItemId", itemId);
            Item item = Item.getAssetMap().getAsset(itemId);
            commandBuilder.set(recipeTag + " #OutputItemName.TextSpans", Message.translation(item.getTranslationKey()));
        }

    }

    private void addMaterialQuantity(Player player, MaterialQuantity materialQuantity, UICommandBuilder commandBuilder, String tag, AtomicInteger i) {
        Inventory inventory = player.getInventory();
        String itemId = materialQuantity.getItemId();
        String resourceTypeId = materialQuantity.getResourceTypeId();

        if (itemId != null) {
            Item inputItem = Item.getAssetMap().getAsset(itemId);
            if (inputItem == null) return;

            int count = inventory.getCombinedEverything().countItemStacks(itemStack -> itemStack.getItemId().equals(itemId));

            commandBuilder.append(tag, "Huds/Drex_BetterItemViewer_RecipeEntry.ui");
            commandBuilder.set(tag + "[" + i + "] #ItemIcon.ItemId", itemId);
            commandBuilder.set(tag + "[" + i + "] #ItemIcon.Visible", true);
            String value = count + "/" + materialQuantity.getQuantity() + " ";
            Message name = Message.empty();
            if (count < materialQuantity.getQuantity()) {
                name.insert(Message.raw(value).color("#dd1111"));
            } else {
                name.insert(Message.raw(value).color("#11dd11"));
            }
            name.insert(" ").insert(Message.translation(inputItem.getTranslationKey()));
            commandBuilder.set(tag + "[" + i + "] #ItemName.TextSpans", name);
            i.getAndIncrement();
        } else if (resourceTypeId != null) {
            ResourceType resourceType = ResourceType.getAssetMap().getAsset(resourceTypeId);
            if (resourceType == null) return;

            int count = inventory.getCombinedEverything().countItemStacks(itemStack -> {
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

            Message name = Message.empty();
            commandBuilder.append(tag, "Huds/Drex_BetterItemViewer_RecipeEntry.ui");
            commandBuilder.set(tag + "[" + i + "] #ResourceIcon.AssetPath", icon);
            commandBuilder.set(tag + "[" + i + "] #ResourceIcon.Visible", true);
            String value = count + "/" + materialQuantity.getQuantity() + " ";
            if (count < materialQuantity.getQuantity()) {
                name.insert(Message.raw(value).color("#ff2222"));
            } else {
                name.insert(Message.raw(value).color("#22ff22"));
            }
            name.insert(" ").insert(Message.translation("server.resourceType." + resourceTypeId + ".name"));
            commandBuilder.set(tag + "[" + i + "] #ItemName.TextSpans", name);
            i.getAndIncrement();
        }
    }
}
