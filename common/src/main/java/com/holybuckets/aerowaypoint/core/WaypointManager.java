package com.holybuckets.aerowaypoint.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.core.MovingWaypoint;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.PlayerInteractEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.holybuckets.foundation.HBUtil.PlayerUtil;

public class WaypointManager {

    private final Level level;
    private final Map<String, Set<ITrackedContrap>> ITrackedContraps;
    private final Map<String, Map<UUID, Integer>> waypointColorsByPlayer;
    private int nextColorCounter;

    private static final Map<Level, WaypointManager> managers = new HashMap<>();


    public static Item WAYPOINT_TOGGLE_ITEM = null;
    public static ResourceLocation WAYPOINT_TOGGLE_LOC = HBUtil.LOC("create", "goggles");

    private WaypointManager(Level level) {
        this.level = level;
        this.ITrackedContraps = new ConcurrentHashMap<>();
        this.waypointColorsByPlayer = new ConcurrentHashMap<>();
        this.nextColorCounter = 0;
    }

    //** GETTERS **//

    public Level getLevel() {
        return level;
    }

    public Set<ITrackedContrap> getITrackedContraps(ServerPlayer sp) {
        String id = PlayerUtil.getId(sp);
        if (id == null) return Set.of();
        Set<ITrackedContrap> set = ITrackedContraps.get(id);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public Map<String, Set<ITrackedContrap>> getAllITrackedContraps() {
        return Collections.unmodifiableMap(ITrackedContraps);
    }

    public boolean isTracked(ServerPlayer sp, UUID contraptionUuid) {
        if (sp == null || contraptionUuid == null) return false;
        String id = PlayerUtil.getId(sp);
        if (id == null) return false;
        Set<ITrackedContrap> set = ITrackedContraps.get(id);
        if (set == null) return false;
        for (ITrackedContrap tc : set) {
            if (contraptionUuid.equals(tc.getContraptionUuid())) return true;
        }
        return false;
    }

    public ITrackedContrap getByUuid(ServerPlayer sp, UUID contraptionUuid) {
        if (sp == null || contraptionUuid == null) return null;
        String id = PlayerUtil.getId(sp);
        if (id == null) return null;
        Set<ITrackedContrap> set = ITrackedContraps.get(id);
        if (set == null) return null;
        for (ITrackedContrap tc : set) {
            if (contraptionUuid.equals(tc.getContraptionUuid())) return tc;
        }
        return null;
    }

    private synchronized int getNextColor() {
        int c = nextColorCounter % MovingWaypoint.MAX_COLORS;
        nextColorCounter++;
        return c;
    }



    //** CORE **//


    public void interact(PlayerInteractEvent.EntityInteract event)
    {
        Item handItem = event.getItemStack().getItem();
        if (WAYPOINT_TOGGLE_ITEM == null || !handItem.equals(WAYPOINT_TOGGLE_ITEM)) return;

        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        Entity targetEntity = event.getTarget();
        if (targetEntity == null) return;

        ITrackedContrap existing = this.getByUuid(sp, targetEntity.getUUID());
        if (existing != null) {
            this.untrack(sp, existing);
        } else {
            ITrackedContrap contraption = ITrackedContrap.getContraption(targetEntity);
            if (contraption != null) this.track(sp, contraption);
        }
    }


    public ITrackedContrap track(ServerPlayer sp, ITrackedContrap contrap) {
        if (sp == null || contrap == null) return null;
        String id = PlayerUtil.getId(sp);
        if (id == null) return null;

        Set<ITrackedContrap> set = ITrackedContraps.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        if (!set.add(contrap)) return contrap;

        int colorId = getNextColor();
        int waypointId = colorId * 2 + 1;
        waypointColorsByPlayer
            .computeIfAbsent(id, k -> new ConcurrentHashMap<>())
            .put(contrap.getContraptionUuid(), colorId);

        MovingWaypoint.setWaypoint(
            sp,
            contrap.getAnchorPos(),
            colorId,
            waypointId,
            true,
            contrap.getContraptionEntity(),
            "contraption"
        );

        return contrap;
    }

    public boolean untrack(ServerPlayer sp, ITrackedContrap tc) {
        if (sp == null || tc == null) return false;
        String id = PlayerUtil.getId(sp);
        if (id == null) return false;
        Set<ITrackedContrap> set = ITrackedContraps.get(id);
        if (set == null) return false;
        boolean removed = set.remove(tc);
        if (removed) {
            Map<UUID, Integer> colors = waypointColorsByPlayer.get(id);
            if (colors != null) {
                Integer colorId = colors.remove(tc.getContraptionUuid());
                if (colorId != null) {
                    MovingWaypoint.removeWaypoint(sp, colorId);
                }
                if (colors.isEmpty()) waypointColorsByPlayer.remove(id);
            }
            if (set.isEmpty()) ITrackedContraps.remove(id);
        }
        return removed;
    }

    public boolean untrack(ServerPlayer sp, UUID contraptionUuid) {
        ITrackedContrap tc = getByUuid(sp, contraptionUuid);
        if (tc == null) return false;
        return untrack(sp, tc);
    }

    public void clear() {
        ITrackedContraps.clear();
        waypointColorsByPlayer.clear();
        nextColorCounter = 0;
    }

    private void tickPrune() {
        for (Map.Entry<String, Set<ITrackedContrap>> entry : ITrackedContraps.entrySet()) {
            String playerId = entry.getKey();
            Iterator<ITrackedContrap> it = entry.getValue().iterator();
            while (it.hasNext()) {
                ITrackedContrap tc = it.next();
                Entity ent = tc.getContraptionEntity();
                if (ent == null || ent.isRemoved()) {
                    it.remove();
                    Map<UUID, Integer> colors = waypointColorsByPlayer.get(playerId);
                    if (colors != null) {
                        Integer colorId = colors.remove(tc.getContraptionUuid());
                        if (colorId != null) {
                            MovingWaypoint.removeWaypoint(playerId, colorId);
                        }
                    }
                }
            }
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
        
    }

    public static void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ITrackedContrap.isValidContraption(event.getTarget())) return;
        WaypointManager m = get(event.getLevel());
        if (m != null) m.interact(event);
    }


    private static void onServerStart(ServerStartingEvent event) {
        managers.clear();

        WAYPOINT_TOGGLE_ITEM = HBUtil.ItemUtil.itemNameToItem("create:goggles");
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
            if (!manager.ITrackedContraps.isEmpty()) {
                manager.tickPrune();
            }
        }
    }
}
