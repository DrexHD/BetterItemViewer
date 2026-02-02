package me.drex.betteritemviewer.item;

import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.npc.role.Role;

import java.util.*;

public class ItemDetails {
    public final Map<String, MobDropDetails> mobLoot = new LinkedHashMap<>();
    public final Map<String, CraftingRecipe> craftingRecipes = new LinkedHashMap<>();
    public final Map<String, CraftingRecipe> usageRecipes = new LinkedHashMap<>();
    public final Map<String, Range> damageInteractions = new LinkedHashMap<>();
}
