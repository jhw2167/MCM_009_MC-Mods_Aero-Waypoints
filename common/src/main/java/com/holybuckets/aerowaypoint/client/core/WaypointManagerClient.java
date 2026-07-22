package com.holybuckets.aerowaypoint.client.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holybuckets.aerowaypoint.client.CommonClassClient;
import com.holybuckets.aerowaypoint.client.config.AeroWaypointClientConfig.WaypointVisibility;
import com.holybuckets.aerowaypoint.core.WaypointManager;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.client.ClientEventRegistrar;
import com.holybuckets.foundation.client.core.MovingWaypoint;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WaypointManagerClient {

    private static WaypointManagerClient INSTANCE;

    private final Set<UUID> trackedContraptions = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> staticContraptions = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> entityCreatedAt = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> staticCreatedAt = new ConcurrentHashMap<>();

    private static final long VISIBILITY_GRACE_MILLIS = 3000L;

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
        reg.registerOnDisconnectedFromServer(event -> {
            self.trackedContraptions.clear();
            self.staticContraptions.clear();
            self.entityCreatedAt.clear();
            self.staticCreatedAt.clear();
        });
    }


    public Set<UUID> getTrackedContraptions() {
        return Collections.unmodifiableSet(trackedContraptions);
    }

    public Set<BlockPos> getStaticContraptions() {
        return Collections.unmodifiableSet(staticContraptions);
    }

    public boolean isTrackedContraption(UUID uuid) {
        return uuid != null && trackedContraptions.contains(uuid);
    }

    public boolean isStaticContraption(BlockPos pos) {
        return pos != null && staticContraptions.contains(pos);
    }


    private void onMessage(SimpleMessageEvent event)
    {
        JsonElement parsed = JsonParser.parseString(event.getContent());
        if (parsed == null || !parsed.isJsonObject()) return;
        JsonObject obj = parsed.getAsJsonObject();
        String action = obj.has("action") ? obj.get("action").getAsString() : null;
        if (action == null) return;
        long now = System.currentTimeMillis();
        switch (action) {
            case "add" -> {
                if (obj.has("uuid")) {
                    try {
                        UUID id = UUID.fromString(obj.get("uuid").getAsString());
                        trackedContraptions.add(id);
                        entityCreatedAt.put(id, now);
                    } catch (IllegalArgumentException ignored) {}
                } else if (obj.has("pos")) {
                    BlockPos p = HBUtil.BlockUtil.stringToBlockPos(obj.get("pos").getAsString());
                    if (p != null) {
                        staticContraptions.add(p);
                        staticCreatedAt.put(p, now);
                    }
                }
            }
            case "remove" -> {
                if (obj.has("uuid")) {
                    try {
                        UUID id = UUID.fromString(obj.get("uuid").getAsString());
                        trackedContraptions.remove(id);
                        entityCreatedAt.remove(id);
                    } catch (IllegalArgumentException ignored) {}
                } else if (obj.has("pos")) {
                    BlockPos p = HBUtil.BlockUtil.stringToBlockPos(obj.get("pos").getAsString());
                    if (p != null) {
                        staticContraptions.remove(p);
                        staticCreatedAt.remove(p);
                    }
                }
            }
            case "clear" -> {
                trackedContraptions.clear();
                staticContraptions.clear();
                entityCreatedAt.clear();
                staticCreatedAt.clear();
            }
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
        MovingWaypoint.Waypoint wp = event.getWaypoint();
        if (!wp.isActive) return;

        UUID linked = wp.linkedEntityUuid;
        BlockPos target = wp.targetPos;

        boolean isTrackedEntity = (linked != null) && trackedContraptions.contains(linked);
        boolean isTrackedStatic = (linked == null) && target != null && staticContraptions.contains(target);
        if (!isTrackedEntity && !isTrackedStatic) return;

        long now = System.currentTimeMillis();
        Long createdAt = isTrackedEntity ? entityCreatedAt.get(linked) : staticCreatedAt.get(target);
        if (createdAt != null && now - createdAt < VISIBILITY_GRACE_MILLIS) return;

        if (CommonClassClient.CONFIG == null) return;
        WaypointVisibility vis = CommonClassClient.CONFIG.getWaypointVisibility();
        Player p = Minecraft.getInstance().player;
        if (p == null) return;

        wp.isActive = isVisible(vis, p);
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
        if(p.getMainHandItem().getItem() == goggles) return true;

        return false;
    }

    private static Item getGogglesItem() {
        if (gogglesItem == null) {
            Item resolved = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "goggles"));
            if (resolved != null && resolved != BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("minecraft", "air"))) {
                gogglesItem = resolved;
            }
        }
        return gogglesItem;
    }
}
