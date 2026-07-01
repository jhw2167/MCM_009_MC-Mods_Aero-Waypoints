package com.holybuckets.aerowaypoint.client.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holybuckets.aerowaypoint.client.CommonClassClient;
import com.holybuckets.aerowaypoint.client.config.AeroWaypointClientConfig.WaypointVisibility;
import com.holybuckets.aerowaypoint.core.WaypointManager;
import com.holybuckets.foundation.client.ClientEventRegistrar;
import com.holybuckets.foundation.event.custom.ClientLevelTickEvent;
import com.holybuckets.foundation.event.custom.DetermineActiveWaypointEvent;
import com.holybuckets.foundation.event.custom.SimpleMessageEvent;
import com.holybuckets.foundation.event.custom.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WaypointManagerClient {

    private static WaypointManagerClient INSTANCE;

    private final Set<UUID> trackedContraptions = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> staticContraptions = ConcurrentHashMap.newKeySet();

    private boolean wasCrouching = false;
    private boolean crouchToggleVisible = true;

    private static Item gogglesItem = null;

    public static WaypointManagerClient getInstance() {
        if (INSTANCE == null) INSTANCE = new WaypointManagerClient();
        return INSTANCE;
    }

    private WaypointManagerClient() {}

    public static void init(ClientEventRegistrar reg) {
        WaypointManagerClient self = getInstance();
        reg.registerOnSimpleMessage(WaypointManager.MSG_ID_SYNC_CONTRAPTION, self::onMessage);
        reg.registerOnDetermineActiveWaypoint(self::onDetermineActiveWaypoint);
        reg.registerOnClientLevelTick(TickType.ON_SINGLE_TICK, self::onClientTick);
    }


    public Set<UUID> getTrackedContraptions() {
        return Collections.unmodifiableSet(trackedContraptions);
    }

    public boolean isTrackedContraption(UUID uuid) {
        return uuid != null && trackedContraptions.contains(uuid);
    }


    private void onMessage(SimpleMessageEvent event)
    {
        JsonElement parsed = JsonParser.parseString(event.getContent());
        if (parsed == null || !parsed.isJsonObject()) return;
        JsonObject obj = parsed.getAsJsonObject();
        String action = obj.has("action") ? obj.get("action").getAsString() : null;
        if (action == null) return;
        switch (action) {
            case "add" -> {
                if (obj.has("uuid")) {
                    try { trackedContraptions.add(UUID.fromString(obj.get("uuid").getAsString())); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
            case "remove" -> {
                if (obj.has("uuid")) {
                    try { trackedContraptions.remove(UUID.fromString(obj.get("uuid").getAsString())); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
            case "clear" -> trackedContraptions.clear();
        }
    }

    private void onClientTick(ClientLevelTickEvent event) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        boolean nowCrouching = p.isCrouching();
        if (nowCrouching && !wasCrouching) {
            crouchToggleVisible = !crouchToggleVisible;
        }
        wasCrouching = nowCrouching;
    }

    private void onDetermineActiveWaypoint(DetermineActiveWaypointEvent event) {
        UUID linked = event.getWaypoint().linkedEntityUuid;
        if (linked == null) return;
        if (!trackedContraptions.contains(linked)) return;

        if (CommonClassClient.CONFIG == null) return;
        WaypointVisibility vis = CommonClassClient.CONFIG.getWaypointVisibility();
        Player p = Minecraft.getInstance().player;
        if (p == null) return;

        event.getWaypoint().isActive = isVisible(vis, p);
    }

    private boolean isVisible(WaypointVisibility vis, Player p) {
        switch (vis) {
            case ALWAYS:             return true;
            case GOGGLES:            return hasGoggles(p);
            case CROUCH:             return p.isCrouching();
            case CROUCH_TOGGLE:      return crouchToggleVisible;
            case GOGGLES_AND_CROUCH: return hasGoggles(p) && p.isCrouching();
            default:                 return true;
        }
    }

    private static boolean hasGoggles(Player p) {
        Item goggles = getGogglesItem();
        if (goggles == null) return false;
        for (ItemStack stack : p.getInventory().armor) {
            if (!stack.isEmpty() && stack.getItem() == goggles) return true;
        }
        for (ItemStack stack : p.getInventory().offhand) {
            if (!stack.isEmpty() && stack.getItem() == goggles) return true;
        }
        if(p.getMainHandItem().equals(new ItemStack(goggles))) return true;

        return false;
    }

    private static Item getGogglesItem() {
        if (gogglesItem == null) {
            Item resolved = BuiltInRegistries.ITEM.get(new ResourceLocation("create", "goggles"));
            if (resolved != null && resolved != BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "air"))) {
                gogglesItem = resolved;
            }
        }
        return gogglesItem;
    }
}
