package me.drex.betteritemviewer.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.item.config.*;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.PositionCacheSystems;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import it.unimi.dsi.fastutil.Pair;
import me.drex.betteritemviewer.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

public class ItemManager {
    private static final ItemManager INSTANCE = new ItemManager();

    private final Map<String, ItemDetails> items = new HashMap<>();

    private ItemManager() {

    }

    public static ItemManager get() {
        return INSTANCE;
    }

    public void registerItem(String id, Item item) {
        ItemDetails details = getOrCreateDetails(id);

        details.damageInteractions.putAll(parseDamageInteractions(id, item));

        items.put(id, details);
    }

    public void registerRecipe(String id, CraftingRecipe craftingRecipe) {
        for (MaterialQuantity output : craftingRecipe.getOutputs()) {
            ItemDetails itemDetails = getOrCreateDetails(output.getItemId());
            itemDetails.craftingRecipes.put(id, craftingRecipe);
        }
        for (MaterialQuantity input : craftingRecipe.getInput()) {
            String itemId = input.getItemId();
            String resourceTypeId = input.getResourceTypeId();
            if (itemId != null) {
                ItemDetails itemDetails = getOrCreateDetails(itemId);
                itemDetails.usageRecipes.put(id, craftingRecipe);
            } else if (resourceTypeId != null) {
                for (Item item : Item.getAssetMap().getAssetMap().values()) {
                    ItemResourceType[] resourceTypes = item.getResourceTypes();
                    if (resourceTypes == null) continue;
                    for (ItemResourceType type : resourceTypes) {
                        if (resourceTypeId.equals(type.id)) {
                            ItemDetails itemDetails = getOrCreateDetails(item.getId());
                            itemDetails.usageRecipes.put(id, craftingRecipe);
                        }
                    }
                }
            }
        }
    }

    public ItemDetails getOrCreateDetails(String id) {
        return items.computeIfAbsent(id, _ -> new ItemDetails());
    }

    public Map<String, ItemDetails> getItems() {
        return items;
    }

    private Map<String, Range> parseDamageInteractions(String id, Item item) {
        Map<String, Range> damageInteractions = new LinkedHashMap<>();
        try {
            Path path = Item.getAssetMap().getPath(id);
            if (path == null) return damageInteractions;
            AssetPack assetPack = AssetModule.get().findAssetPackForPath(path);
            if (assetPack == null) return damageInteractions;

            Path root = assetPack.getRoot();
            Path resolve = root.resolve(path);

            if (Files.exists(resolve)) {
                try {
                    String jsonContent = Files.readString(resolve);
                    JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
                    if (json.has("InteractionVars")) {
                        for (Map.Entry<String, JsonElement> interactionVars : json.get("InteractionVars").getAsJsonObject().entrySet()) {
                            String key = interactionVars.getKey();
                            JsonElement value = interactionVars.getValue();
                            if (!value.isJsonObject()) continue;
                            JsonObject interactionVar = value.getAsJsonObject();
                            if (interactionVar.has("Interactions")) {
                                for (JsonElement interaction_ : interactionVar.getAsJsonArray("Interactions")) {
                                    if (!interaction_.isJsonObject()) continue;
                                    JsonObject interaction = interaction_.getAsJsonObject();
                                    if (interaction.has("DamageCalculator")) {
                                        JsonObject damageCalculator = interaction.get("DamageCalculator").getAsJsonObject();
                                        if (damageCalculator.has("BaseDamage")) {
                                            JsonObject baseDamage = damageCalculator.get("BaseDamage").getAsJsonObject();

                                            float minAmount = 0;
                                            float maxAmount = 0;
                                            for (Map.Entry<String, JsonElement> stringJsonElementEntry : baseDamage.entrySet()) {
                                                JsonElement damageValue = stringJsonElementEntry.getValue();
                                                minAmount = damageValue.getAsFloat();
                                                maxAmount = damageValue.getAsFloat();
                                            }
                                            if (damageCalculator.has("RandomPercentageModifier")) {
                                                float randomPercentageModifier = damageCalculator.get("RandomPercentageModifier").getAsFloat();
                                                minAmount *= (1f - randomPercentageModifier);
                                                maxAmount *= (1f + randomPercentageModifier);
                                            }

                                            damageInteractions.put(key, new Range((int) minAmount, (int) maxAmount));

                                        }
                                    }
                                }

                            }
                        }
                    }
                } catch (IOException _) {

                }
            }
        } catch (Exception e) {
            Main.get().getLogger().at(Level.SEVERE).withCause(e).log("Failed to parse item damage interactions for item " + id + ":");
        }

        return damageInteractions;
    }

    public void findMobDrops() {
        World defaultWorld = Universe.get().getDefaultWorld();
        if (defaultWorld == null) return;

        NPCPlugin npcPlugin = NPCPlugin.get();
        List<String> roles = npcPlugin.getRoleTemplateNames(true);
        TransformComponent transformComponent = new TransformComponent();

        Vector3d pos = new Vector3d(transformComponent.getPosition());
        String name = roles.getFirst();
        int roleIndex = NPCPlugin.get().getIndex(name);
        if (roleIndex < 0) {
            throw new IllegalStateException("No such valid role: " + name);
        } else {
            Pair<Ref<EntityStore>, NPCEntity> npcPair = npcPlugin.spawnEntity(defaultWorld.getEntityStore().getStore(), roleIndex, pos, null, null, null);
            NPCEntity npcComponent = npcPair.second();

            for (String roleName : roles) {
                try {
                    BuilderInfo builderInfo = NPCPlugin.get().prepareRoleBuilderInfo(NPCPlugin.get().getIndex(roleName));
                    Builder<Role> roleBuilder = (Builder<Role>) builderInfo.getBuilder();
                    Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                    holder.addComponent(NPCEntity.getComponentType(), npcComponent);
                    BuilderSupport builderSupport = new BuilderSupport(
                        NPCPlugin.get().getBuilderManager(), npcComponent, holder, new ExecutionContext(), roleBuilder, null
                    );
                    Role role = NPCPlugin.buildRole(roleBuilder, builderInfo, builderSupport, roleIndex);
                    PositionCacheSystems.initialisePositionCache(role, builderSupport.getStateEvaluator(), 0.0);
                    String dropListId = role.getDropListId();
                    if (dropListId == null) continue;
                    ItemDropList itemDropList = ItemDropList.getAssetMap().getAsset(dropListId);
                    if (itemDropList == null) continue;
                    var drops = new LinkedList<ItemDrop>();
                    ItemDropContainer container = itemDropList.getContainer();
                    if (container == null) continue;
                    container.getAllDrops(drops);

                    for (ItemDrop drop : drops) {
                        String itemId = drop.getItemId();
                        ItemDetails itemDetails = getOrCreateDetails(itemId);
                        itemDetails.mobLoot.put(role.getNameTranslationKey(), new Range(drop.getQuantityMin(), drop.getQuantityMax()));
                    }
                } catch (Throwable t) {
                    npcPlugin.getLogger().at(Level.WARNING).log("Error spawning role " + roleName + ": " + t.getMessage());
                }
            }

            npcComponent.remove();
        }
    }
}
