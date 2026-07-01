package com.holybuckets.aerowaypoint.core;

import com.google.gson.JsonObject;
import com.holybuckets.aerowaypoint.Constants;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.core.MovingWaypoint;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.PlayerInteractEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.foundation.networking.SimpleStringMessage;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.holybuckets.foundation.HBUtil.PlayerUtil;

public class WaypointManager {

    private static final Logger LOG = LoggerFactory.getLogger("HBs Aero Waypoints");
    public static final long STATIC_WAYPOINT_LIFETIME_TICKS = 24000L;

    private final Level level;
    private final Map<String, Set<ITrackedContrap>> trackedContraptions;
    private final Map<String, Map<UUID, Integer>> waypointColorsByPlayer;
    private final Map<BlockPos, ITrackedContrap> staticContraptions;
    private int nextColorCounter;

    private static final Map<Level, WaypointManager> managers = new HashMap<>();


    public static Item WAYPOINT_TOGGLE_ITEM = null;

    public static final String MSG_ID_SYNC_CONTRAPTION = "sync_contraption";

    private static void sendTrackedToClient(ServerPlayer sp, UUID uuid, BlockPos pos, String action) {
        if (sp == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("action", action);
        if (uuid != null) json.addProperty("uuid", uuid.toString());
        SimpleStringMessage.createAndFire(sp, MSG_ID_SYNC_CONTRAPTION, json.toString());
    }

    private static void sendTrackedToClient(String playerId, UUID uuid, String action) {
        if (playerId == null) return;
        Player p = PlayerUtil.getPlayer(playerId, PlayerUtil.PlayerNameSpace.SERVER);
        if (p == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("action", action);
        if (uuid != null) json.addProperty("uuid", uuid.toString());
        SimpleStringMessage.createAndFire(p, MSG_ID_SYNC_CONTRAPTION, json.toString());
    }

    private WaypointManager(Level level) {
        this.level = level;
        this.trackedContraptions = new ConcurrentHashMap<>();
        this.waypointColorsByPlayer = new ConcurrentHashMap<>();
        this.staticContraptions = new ConcurrentHashMap<>();
        this.nextColorCounter = 0;
    }

    private String findOwnerPlayerId(ITrackedContrap contrap) {
        for (Map.Entry<String, Set<ITrackedContrap>> e : trackedContraptions.entrySet()) {
            if (e.getValue().contains(contrap)) return e.getKey();
        }
        return null;
    }

    //** GETTERS **//

    public Level getLevel() {
        return level;
    }

    public Set<ITrackedContrap> getITrackedContraps(ServerPlayer sp) {
        String id = PlayerUtil.getId(sp);
        if (id == null) return Set.of();
        Set<ITrackedContrap> set = trackedContraptions.get(id);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public Map<String, Set<ITrackedContrap>> getAllITrackedContraps() {
        return Collections.unmodifiableMap(trackedContraptions);
    }

    public boolean isTracked(ServerPlayer sp, UUID contraptionUuid) {
        if (sp == null || contraptionUuid == null) return false;
        String id = PlayerUtil.getId(sp);
        if (id == null) return false;
        Set<ITrackedContrap> set = trackedContraptions.get(id);
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
        Set<ITrackedContrap> set = trackedContraptions.get(id);
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
    public int calculateWaypointId(String playerId, int colorId) {
        int base = Constants.MOD_ID.hashCode() * playerId.hashCode();
        return (Math.abs(base) % Integer.MAX_VALUE) + colorId;
    }


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

        Set<ITrackedContrap> set = trackedContraptions.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        if (!set.add(contrap)) return contrap;

        Integer reusedColor = tryReactivateStatic(sp, id, contrap);
        int colorId = reusedColor != null ? reusedColor : getNextColor();
        int waypointId = calculateWaypointId(PlayerUtil.getId(sp), colorId);
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
            contrap.getContraptionUuid().toString()
        );

        sendTrackedToClient(sp, contrap.getContraptionUuid(), "add");
        return contrap;
    }

    private Integer tryReactivateStatic(ServerPlayer sp, String newOwnerId, ITrackedContrap newContrap) {
        BlockPos anchor = newContrap.getAnchorPos();
        if (anchor == null) return null;
        ITrackedContrap oldStatic = staticContraptions.get(anchor);
        if (oldStatic == null) return null;

        String oldOwnerId = findOwnerPlayerId(oldStatic);
        if (oldOwnerId == null || !oldOwnerId.equals(newOwnerId)) return null;

        staticContraptions.remove(anchor);

        Set<ITrackedContrap> ownerSet = trackedContraptions.get(oldOwnerId);
        if (ownerSet != null) ownerSet.remove(oldStatic);

        Map<UUID, Integer> ownerColors = waypointColorsByPlayer.get(oldOwnerId);
        Integer oldColorId = ownerColors != null ? ownerColors.remove(oldStatic.getContraptionUuid()) : null;

        if (oldColorId != null) {
            MovingWaypoint.removeWaypoint(sp, oldColorId);
            LOG.info("Aero: reactivated static waypoint at {} for player {} → new contraption uuid {} (color {})",
                anchor, newOwnerId, newContrap.getContraptionUuid(), oldColorId);
        }
        return oldColorId;
    }

    public boolean untrack(ServerPlayer sp, ITrackedContrap tc) {
        if (sp == null || tc == null) return false;
        String id = PlayerUtil.getId(sp);
        if (id == null) return false;
        Set<ITrackedContrap> set = trackedContraptions.get(id);
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
            if (set.isEmpty()) trackedContraptions.remove(id);
            sendTrackedToClient(sp, tc.getContraptionUuid(), "remove");
        }
        return removed;
    }

    public boolean untrack(ServerPlayer sp, UUID contraptionUuid) {
        ITrackedContrap tc = getByUuid(sp, contraptionUuid);
        if (tc == null) return false;
        return untrack(sp, tc);
    }

    public void clear() {
        trackedContraptions.clear();
        waypointColorsByPlayer.clear();
        staticContraptions.clear();
        nextColorCounter = 0;
    }

    private void tickPrune()
    {
        if(HBUtil.PlayerUtil.getAllPlayers().isEmpty()) return;

        long now = level != null ? level.getGameTime() : 0L;
        for (Map.Entry<String, Set<ITrackedContrap>> entry : trackedContraptions.entrySet())
        {
            String playerId = entry.getKey();
            Iterator<ITrackedContrap> it = entry.getValue().iterator();
            while (it.hasNext())
            {
                ITrackedContrap tc = it.next();
                Entity ent = tc.getContraptionEntity();
                boolean entityGone = (ent == null || ent.isRemoved());

                if (tc.getStaticPosition() != null) {
                    long age = now - tc.getStaticPositionStartTick();
                    if (age > STATIC_WAYPOINT_LIFETIME_TICKS) {
                        expireStatic(playerId, tc, it);
                    }
                    continue;
                }

                if (entityGone) {
                    transitionToStatic(playerId, tc, now);
                }
            }
        }
    }

    private void transitionToStatic(String playerId, ITrackedContrap tc, long now) {
        BlockPos anchor = tc.getAnchorPos();
        if (anchor == null) return;

        tc.setStaticPosition(anchor);
        tc.setStaticPositionStartTick(now);
        staticContraptions.put(anchor, tc);

        Map<UUID, Integer> colors = waypointColorsByPlayer.get(playerId);
        Integer colorId = colors != null ? colors.get(tc.getContraptionUuid()) : null;

        if (colorId != null) {
            int waypointId = calculateWaypointId(playerId, colorId);
            MovingWaypoint.removeWaypoint(playerId, colorId);
            Player p = PlayerUtil.getPlayer(playerId, PlayerUtil.PlayerNameSpace.SERVER);
            if (p instanceof ServerPlayer sp) {
                MovingWaypoint.setWaypoint(sp, anchor, colorId, waypointId, true, null, "contraption (static)");
            }
        }

        LOG.info("Aero: contraption transitioned to static waypoint at {} for player {} (color {})",
            anchor, playerId, colorId);
    }

    private void expireStatic(String playerId, ITrackedContrap tc, Iterator<ITrackedContrap> it) {
        BlockPos anchor = tc.getStaticPosition();
        it.remove();
        if (anchor != null) staticContraptions.remove(anchor);

        Map<UUID, Integer> colors = waypointColorsByPlayer.get(playerId);
        Integer colorId = colors != null ? colors.remove(tc.getContraptionUuid()) : null;
        if (colorId != null) {
            MovingWaypoint.removeWaypoint(playerId, colorId);
        }
        if (colors != null && colors.isEmpty()) waypointColorsByPlayer.remove(playerId);
        sendTrackedToClient(playerId, tc.getContraptionUuid(), "remove");

        LOG.info("Aero: static contraption waypoint at {} expired for player {} after 1 Minecraft day",
            anchor, playerId);
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
            if (!manager.trackedContraptions.isEmpty()) {
                manager.tickPrune();
            }
        }
    }
}
