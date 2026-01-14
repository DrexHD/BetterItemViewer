package me.drex.betteritemviewer;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;

public class Main extends JavaPlugin {

    public static Collection<Item> ITEMS = Collections.emptyList();
    private static Map<String, Map<String, Map.Entry<Integer, Integer>>> MOB_LOOT = null;

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();
        this.getCommandRegistry().registerCommand(new BetterItemViewerCommand());
        this.getEventRegistry().register(LoadedAssetsEvent.class, Item.class, Main::onItemAssetLoad);
    }

    public static Map<String, Map<String, Map.Entry<Integer, Integer>>> getOrCreateMobLootInfo(Store<EntityStore> store) {
        if (MOB_LOOT == null) {
            MOB_LOOT = new HashMap<>();
            NPCPlugin npcPlugin = NPCPlugin.get();
            List<String> roles = npcPlugin.getRoleTemplateNames(true);
            TransformComponent transformComponent = new TransformComponent();

            Vector3d pos = new Vector3d(transformComponent.getPosition());
            String name = roles.getFirst();
            int roleIndex = NPCPlugin.get().getIndex(name);
            if (roleIndex < 0) {
                throw new IllegalStateException("No such valid role: " + name);
            } else {
                Pair<Ref<EntityStore>, NPCEntity> npcPair = npcPlugin.spawnEntity(store, roleIndex, pos, null, null, null);
                NPCEntity npcComponent = npcPair.second();

                for (String roleName : roles) {
                    try {
                        BuilderInfo builderInfo = NPCPlugin.get().prepareRoleBuilderInfo(NPCPlugin.get().getIndex(roleName));
                        Builder<Role> roleBuilder = (Builder<Role>) builderInfo.getBuilder();
                        BuilderSupport builderSupport = new BuilderSupport(
                            NPCPlugin.get().getBuilderManager(), npcComponent, EntityStore.REGISTRY.newHolder(), new ExecutionContext(), roleBuilder, null
                        );
                        Role role = NPCPlugin.buildRole(roleBuilder, builderInfo, builderSupport, roleIndex);
                        PositionCacheSystems.initialisePositionCache(role, builderSupport.getStateEvaluator(), 0.0);
                        String dropListId = role.getDropListId();
                        if (dropListId != null) {
                            ItemDropList itemDropList = ItemDropList.getAssetMap().getAsset(dropListId);
                            var drops = new LinkedList<ItemDrop>();
                            itemDropList.getContainer().getAllDrops(drops);

                            for (ItemDrop drop : drops) {
                                String itemId = drop.getItemId();
                                Map<String, Map.Entry<Integer, Integer>> dropRates = MOB_LOOT.computeIfAbsent(itemId, _ -> new HashMap<>());
                                dropRates.put(role.getNameTranslationKey(), Map.entry(drop.getQuantityMin(), drop.getQuantityMax()));
                            }
                        }

                    } catch (Throwable t) {
                        npcPlugin.getLogger().at(Level.WARNING).log("Error spawning role " + roleName + ": " + t.getMessage());
                        continue;
                    }
                }

                npcComponent.remove();
            }
        }
        return MOB_LOOT;
    }


    private static void onItemAssetLoad(LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        ITEMS = event.getAssetMap().getAssetMap().values();
    }

}