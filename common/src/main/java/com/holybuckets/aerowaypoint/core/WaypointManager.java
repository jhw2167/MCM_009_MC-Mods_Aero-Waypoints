package com.holybuckets.aerowaypoint.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.PlayerInteractEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WaypointManager {

    private final Level level;
    private final Set<TrackedContraption> trackedContraptions;

    private static final Map<Level, WaypointManager> managers = new HashMap<>();

    private WaypointManager(Level level) {
        this.level = level;
        this.trackedContraptions = new HashSet<>();
    }

    public Level getLevel() {
        return level;
    }

    public Set<TrackedContraption> getTrackedContraptions() {
        return Collections.unmodifiableSet(trackedContraptions);
    }

    public boolean isTracked(UUID contraptionUuid) {
        if (contraptionUuid == null) return false;
        for (TrackedContraption tc : trackedContraptions) {
            if (contraptionUuid.equals(tc.getContraptionUuid())) return true;
        }
        return false;
    }

    public TrackedContraption getByUuid(UUID contraptionUuid) {
        if (contraptionUuid == null) return null;
        for (TrackedContraption tc : trackedContraptions) {
            if (contraptionUuid.equals(tc.getContraptionUuid())) return tc;
        }
        return null;
    }

    public TrackedContraption track(ITrackedContrap contrap) {
        if (contrap == null) return null;
        TrackedContraption existing = getByUuid(contrap.getContraptionUuid());
        if (existing != null) return existing;
        TrackedContraption tc = new TrackedContraption(level, contrap);
        trackedContraptions.add(tc);
        return tc;
    }

    public boolean untrack(TrackedContraption tc) {
        if (tc == null) return false;
        return trackedContraptions.remove(tc);
    }

    public boolean untrack(UUID contraptionUuid) {
        TrackedContraption tc = getByUuid(contraptionUuid);
        if (tc == null) return false;
        return trackedContraptions.remove(tc);
    }

    public void clear() {
        trackedContraptions.clear();
    }

    private void tickPrune() {
        Iterator<TrackedContraption> it = trackedContraptions.iterator();
        while (it.hasNext()) {
            TrackedContraption tc = it.next();
            if (!tc.isAlive()) it.remove();
        }
    }


    private static WaypointManager init(Level level) {
        if (!managers.containsKey(level)) {
            managers.put(level, new WaypointManager(level));
        }
        return managers.get(level);
    }

    public static WaypointManager get(Level level) {
        if (level == null) return null;
        if (GeneralConfig.getInstance().isIntegrated()) {
            level = HBUtil.LevelUtil.toLevel(HBUtil.LevelUtil.LevelNameSpace.SERVER, level.dimension());
        }
        if (!managers.containsKey(level)) init(level);
        return managers.get(level);
    }

    public static Map<Level, WaypointManager> all() {
        return Collections.unmodifiableMap(managers);
    }

    public static void init(EventRegistrar reg) {
        reg.registerOnBeforeServerStarted(WaypointManager::onServerStart);
        reg.registerOnServerStopped(WaypointManager::onServerStopped);
        reg.registerOnLevelLoad(WaypointManager::onLevelLoad, EventPriority.High);
        reg.registerOnServerTick(TickType.ON_20_TICKS, WaypointManager::on20Ticks);
        reg.registerOnPlayerInteract(PlayerInteractEvent.EntityInteract.class, WaypointManager::onPlayerEntityInteract);

        PlayerTrackedContraptions.init();
    }

    private static void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        //PlayerTrackedContraptions.onPlayerEntityInteract(event);
        var target = event.getTarget();
        int i = 0;
    }


    private static void onServerStart(ServerStartingEvent event) {
        managers.clear();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        for (WaypointManager manager : managers.values()) {
            manager.clear();
        }
        managers.clear();
    }

    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        WaypointManager.init((Level) event.getLevel());
    }

    private static void on20Ticks(ServerTickEvent event) {
        for (WaypointManager manager : managers.values()) {
            if (!manager.trackedContraptions.isEmpty()) {
                manager.tickPrune();
            }
        }
    }
}
