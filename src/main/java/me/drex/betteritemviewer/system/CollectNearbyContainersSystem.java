package me.drex.betteritemviewer.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.drex.betteritemviewer.component.NearbyContainersComponent;
import me.drex.betteritemviewer.ui.hud.HudUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class CollectNearbyContainersSystem extends DelayedEntitySystem<EntityStore> {
    public CollectNearbyContainersSystem() {
        super(1.0f);
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        assert player != null;
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        assert transform != null;
        NearbyContainersComponent nearbyContainers = commandBuffer.ensureAndGetComponent(ref, NearbyContainersComponent.getComponentType());
        World world = store.getExternalData().getWorld();
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

        //noinspection removal
        SpatialResource<Ref<ChunkStore>, ChunkStore> blockStateSpatialStructure = chunkStore.getResource(BlockStateModule.get().getItemContainerSpatialResourceType());
        ObjectList<Ref<ChunkStore>> results = SpatialResource.getThreadLocalReferenceList();
        double horizontalRadius = world.getGameplayConfig().getCraftingConfig().getBenchMaterialHorizontalChestSearchRadius();
        double verticalRadius = world.getGameplayConfig().getCraftingConfig().getBenchMaterialVerticalChestSearchRadius();
        double extraSearchRadius = 3;
        blockStateSpatialStructure.getSpatialStructure()
            .ordered3DAxis(transform.getPosition(), horizontalRadius + extraSearchRadius, verticalRadius + extraSearchRadius, horizontalRadius + extraSearchRadius, results);

        List<ItemContainer> containers = new ObjectArrayList<>();

        for (Ref<ChunkStore> blockRef : results) {
            //noinspection removal
            if (BlockState.getBlockState(blockRef, blockRef.getStore()) instanceof ItemContainerState chest) {
                containers.add(chest.getItemContainer());
            }
        }
        nearbyContainers.itemContainer = new CombinedItemContainer(containers.toArray(ItemContainer[]::new));
        world.execute(() -> HudUtils.updateHud(ref));
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
