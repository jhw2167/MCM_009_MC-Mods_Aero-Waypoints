package com.holybuckets.aerowaypoint.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holybuckets.aerowaypoint.AeroWaypointsMain;
import com.holybuckets.aerowaypoint.Constants;
import com.holybuckets.aerowaypoint.LoggerProject;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.core.MovingWaypoint;
import com.holybuckets.foundation.datastore.DataStore;
import com.holybuckets.foundation.datastructure.ConcurrentLinkedSet;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.DatastoreSaveEvent;
import com.holybuckets.foundation.event.custom.PlayerInteractEvent;
import com.holybuckets.foundation.event.custom.ServerTickEvent;
import com.holybuckets.foundation.event.custom.TickType;
import com.holybuckets.foundation.model.EntityLike;
import com.holybuckets.foundation.model.EntityLikeResolver;
import com.holybuckets.foundation.model.VanillaEntityLike;
import com.holybuckets.foundation.modelInterface.IManagedPlayer;
import com.holybuckets.foundation.networking.SimpleStringMessage;
import com.holybuckets.foundation.player.ManagedPlayer;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.LevelLoadingEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.blay09.mods.balm.api.event.server.ServerStoppedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.holybuckets.foundation.HBUtil.PlayerUtil;
import static com.holybuckets.foundation.HBUtil.BlockUtil.toBlockPos;

public class WaypointManager {

    private static final Logger LOG = LoggerFactory.getLogger("HBs Aero Waypoints");
    public static final long STATIC_WAYPOINT_LIFETIME_TICKS = 24000L;
    // Grace before a loaded-but-unresolvable ship is treated as disassembled and removed.
    private static final long SUBLEVEL_LOST_GRACE_TICKS = 100L;

    private final Level level;
    private final Map<String, Set<ITrackedContrap>> trackedContraptions;
    private final Map<String, Map<ITrackedContrap, Integer>> waypointColorsByPlayer;
    private final Map<BlockPos, ITrackedContrap> staticContraptions;
    // Per sub-level UUID, the tick it first became loaded-but-unresolvable (disassembly grace timer).
    private final Map<UUID, Long> shipLostTicks = new ConcurrentHashMap<>();
    private int nextColorCounter;

    private static final Map<Level, WaypointManager> managers = new HashMap<>();
    private static GeneralConfig CONFIG;

     public static final Set<ITrackedContrap> globalContraptions = new ConcurrentLinkedSet<>();
    private static boolean globalContraptionsLoaded = false;
    private static final String GLOBAL_CONTRAPS_KEY = "globalContraptions";


    public static Item WAYPOINT_TOGGLE_ITEM = null;

    public static final String MSG_ID_SYNC_CONTRAPTION = "sync_contraption";

    private static void sendTrackedToClient(ServerPlayer sp, UUID uuid, BlockPos pos, String action) {
        if (sp == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("action", action);
        if (uuid != null) json.addProperty("uuid", uuid.toString());
        if(pos != null) {
            json.addProperty("pos", HBUtil.BlockUtil.positionToString(pos));

        }
        SimpleStringMessage.createAndFire(sp, MSG_ID_SYNC_CONTRAPTION, json.toString());
    }


    private WaypointManager(Level level) {
        this.level = level;
        this.trackedContraptions = new ConcurrentHashMap<>();
        this.waypointColorsByPlayer = new ConcurrentHashMap<>();
        this.staticContraptions = new ConcurrentHashMap<>();
        this.nextColorCounter = 0;
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


    // Entity interact path: check the toggle item, then run the common logic on the target as an EntityLike.
    public void interact(PlayerInteractEvent.EntityInteract event)
    {
        Item handItem = event.getItemStack().getItem();
        if (WAYPOINT_TOGGLE_ITEM == null || !handItem.equals(WAYPOINT_TOGGLE_ITEM)) return;

        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        Entity targetEntity = event.getTarget();
        if (targetEntity == null) return;

        this.interact(sp, EntityLike.of(targetEntity));
    }

    // Common toggle logic for any EntityLike target, whether a vanilla entity or a Sable sub-level.
    public void interact(ServerPlayer sp, EntityLike target)
    {
        if (sp == null || target == null) return;

        ITrackedContrap existing = this.getByUuid(sp, target.getUUID());
        if (existing != null) {
            this.untrack(sp, existing);
            return;
        }

        ITrackedContrap contraption = ITrackedContrap.getContraption(target);
        if (contraption == null) return;
        if (staticContraptions.containsKey(contraption.getAnchorPos()))
            this.tryReactivateStatic(contraption);
        else
            this.track(sp, contraption);
    }


    public ITrackedContrap track(ServerPlayer sp, ITrackedContrap contrap)
    {
        return this.track(sp, contrap, -1);
    }

    public ITrackedContrap track(ServerPlayer sp, ITrackedContrap contrap, int colorOverride)
    {
        if (sp == null || contrap == null) return null;
        String id = PlayerUtil.getId(sp);
        if (id == null) return null;

        Set<ITrackedContrap> set = trackedContraptions.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        if(!set.add(contrap)) return null; //already tracked
        globalContraptions.add(contrap);

        int colorId = (colorOverride>0) ? colorOverride : getNextColor();
        int waypointId = calculateWaypointId(PlayerUtil.getId(sp), colorId);
        waypointColorsByPlayer.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
            .put(contrap, colorId);

        //c + first 4 letters of UUID
        MovingWaypoint.setWaypoint(
            sp,
            contrap.getAnchorPos(),
            colorId,
            waypointId,
            true,
            contrap,
            contrap.createTag()
        );


        // Ships (sub-level backed) and entities are followed by UUID; only truly static targets use a position.
        if (contrap.getContraptionUuid() != null)
            sendTrackedToClient(sp, contrap.getContraptionUuid(), null, "add");
        else
            sendTrackedToClient(sp, null, contrap.getAnchorPos(), "add");

        return contrap;
    }

    private void tryReactivateStatic(EntityLike entityLike) {
        tryReactivateStatic(entityLike, null);
    }

    private void tryReactivateStatic(EntityLike entity, BlockPos anchorPos)
    {
        BlockPos pos = (anchorPos!=null) ? anchorPos : entityLike.getPos();
        ITrackedContrap contrap = staticContraptions.remove(pos);
        if (contrap == null) return;

        for (String playerId : trackedContraptions.keySet() )
        {
            Set<ITrackedContrap> playerContraptions = trackedContraptions.get(playerId);
            if(playerContraptions==null)
                trackedContraptions.put(playerId, ConcurrentHashMap.newKeySet());
            trackedContraptions.get(playerId).add(contrap);
            contrap.restore(entity);

            Integer colorId = waypointColorsByPlayer.get(playerId).get(contrap);
            if (colorId == null) colorId = getNextColor();

            int waypointId = calculateWaypointId(playerId, colorId);
            ServerPlayer sp = (ServerPlayer) PlayerUtil.getPlayer(playerId,
                PlayerUtil.PlayerNameSpace.SERVER);

            MovingWaypoint.setWaypoint(
                sp,
                pos,
                colorId,
                 waypointId,
                true,
                contrap,
                contrap.createTag()
            );
            //send remove message to tracked client
            sendTrackedToClient(sp, null, pos, "remove");
            sendTrackedToClient(sp, contrap.getContraptionUuid(), null, "add");
        }

    }

    public boolean untrack(ServerPlayer sp, ITrackedContrap tc) {
        if (sp == null || tc == null) return false;
        String id = PlayerUtil.getId(sp);
        Set<ITrackedContrap> playerContraps = trackedContraptions.get(id);
        if (playerContraps == null) return false;
        boolean removed = globalContraptions.remove(tc) && playerContraps.remove(tc);
        if (removed) {
            Map<ITrackedContrap, Integer> colors = waypointColorsByPlayer.get(id);
            if (colors != null) {
                Integer colorId = colors.remove(tc);
                if (colorId != null) {
                    int waypointId = calculateWaypointId(id, colorId);
                    MovingWaypoint.removeWaypoint(sp, waypointId);
                }
            }
            // Remove client-side using whatever UUID we added it with (sub-level for ships).
            UUID linkedUuid = tc.getContraptionUuid() != null ? tc.getContraptionUuid() : tc.getContraptionUuid();
            sendTrackedToClient(sp, linkedUuid, null, "remove");
        }
        return removed;
    }


    public void clear() {
        trackedContraptions.clear();
        waypointColorsByPlayer.clear();
        staticContraptions.clear();
        nextColorCounter = 0;
    }

    //** PERSISTENCE HELPERS **//

    public void wipePlayerWaypoints(ServerPlayer sp)
    {
        String pid = PlayerUtil.getId(sp);
        if (pid == null) return;

        Map<ITrackedContrap, Integer> colors = waypointColorsByPlayer.get(pid);
        for (Integer colorId : colors.values()) {
            MovingWaypoint.removeWaypoint(sp, calculateWaypointId(pid, colorId));
        }
    }

    /**
     * Restores a tracked contraption and sets waypoint for player when they reload into the world.
     * If a BlockPosition default was not set, the waypoint is skipped.
     *
     * We try and load the chunk where the object lives and re-link the waypoint to the entity or position
     * if we can't we set it at the block pos and hope for the best.
     *
     * @return true to remove pending entry, false to wait.
     */
    private static final String TICKET_ID = "aero_waypoint_force_load";
    public boolean restoreEntry(ServerPlayer sp, ServerLevel serverLevel, int colorId, ITrackedContrap tc)
    {
        String playerId = PlayerUtil.getId(sp);
        BlockPos pos = tc.getAnchorPos();
        if (playerId == null) return false;


        ChunkAccess chunk = level.getChunk(pos);
        ChunkPos cp = new ChunkPos(pos);
        if(chunk == null) {
            HBUtil.ChunkUtil.forceLoadChunk( serverLevel, cp, TICKET_ID);
            return false;
        } else if(HBUtil.ChunkUtil.isChunkForceLoaded(serverLevel, new ChunkPos(pos))) {
            HBUtil.ChunkUtil.unforceLoadChunk(serverLevel, cp, TICKET_ID);
        }

        Optional<EntityLike> ship = EntityLikeResolver.resolveEntity(tc.getContraptionUuid(), level);

        if(ship.isEmpty()) {    //static waypoint OR entity not resolved
            tc.setStaticPositionStartTick(CONFIG.getTotalTickCount());
        } else {
            tc.restore(ship.get());
        }


        this.track(sp, tc, colorId);
        return true;
    }

    private static final long LOST_CONTRAP_BUFFER_TICKS = 60 * 20L;

    // UUID of the contraption/ship -> tick count when it was first observed as "lost"
    private final Map<UUID, Long> subLevelLostSince = new HashMap<>();

    private void tickPrune()
    {
        if (HBUtil.PlayerUtil.getAllPlayers().isEmpty() || ManagedPlayer.PLAYERS.isEmpty()) return;
        List<ServerPlayer> players = HBUtil.PlayerUtil.getAllPlayers();

        long now = level != null ? CONFIG.getTotalTickCount() : 0L;

        for (ServerPlayer player : players)
        {
            String playerId = PlayerUtil.getId(player);
            Iterator<ITrackedContrap> it = trackedContraptions.get(playerId).iterator();
            while (it.hasNext())
            {
                ITrackedContrap tc = it.next();
                UUID shipId = tc.getUUID();

                if (tc.isStatic()) {
                    //proceed to static checks
                }
                else if (tc.isValid()) {
                    shipLostTicks.remove(shipId);
                    continue;   //everything is fine
                }
                else if (tc.getPos() == null) {
                    transitionToStatic(playerId, tc, now);
                    continue;
                }
                else if (level.isLoaded(toBlockPos(tc.getPos()))) //loaded and not valid
                {
                    Long lostTicks = shipLostTicks.getOrDefault(shipId, now);
                    if(now - lostTicks >= LOST_CONTRAP_BUFFER_TICKS) {
                        shipLostTicks.remove(shipId);
                        transitionToStatic(playerId, tc, now);
                    } else {
                        shipLostTicks.put(shipId, now);
                    }
                    continue;
                }
                else {  //not loaded, not valid, leave it, will sit statically on its own.
                    continue;
                }

                if (tc.getAnchorPos() == null) continue;

                //check if the block is loaded
                if (level != null && level.isLoaded(tc.getAnchorPos()))
                {
                    //Try to resolve by UUID:
                    var recoveredEntity = EntityLikeResolver.resolveEntity(tc.getContraptionUuid(), level);
                    if (recoveredEntity.isPresent()) {
                        this.tryReactivateStatic(recoveredEntity.get(), tc.getAnchorPos());
                        continue;
                    }

                    List<Entity> entities = level.getEntities((Entity) null, new AABB(tc.getAnchorPos()),
                        (e) -> !((e instanceof LivingEntity) || (e instanceof ItemEntity)));
                    if (entities.isEmpty()) {
                        //no entities, check if the block is air
                    } else {
                        Entity e = entities.get(0);
                        if (ITrackedContrap.isValidContraption(e)) {
                            this.tryReactivateStatic(new VanillaEntityLike(e));
                            continue;
                        }
                    }
                }

                long age = now - tc.getStaticPositionStartTick();
                if (age < STATIC_WAYPOINT_LIFETIME_TICKS) continue;

                //if (level.getBlockState(tc.getAnchorPos()).isAir())
                {
                    expireStatic(player, playerId, tc);
                    it.remove();
                    continue;
                }
            }
        }
    }


    private void transitionToStatic(String playerId, ITrackedContrap tc, long now)
    {
        BlockPos anchor = tc.getAnchorPos();
        if(staticContraptions.containsKey(anchor)) return;
        if (anchor == null) return;

        tc.setStaticPosition(anchor);
        tc.setStaticPositionStartTick(now);
        staticContraptions.put(anchor, tc);

        Map<ITrackedContrap, Integer> colors = waypointColorsByPlayer.get(playerId);
        Integer colorId = (colors != null) ? colors.get(tc) : null;

        if (colorId != null)
        {
            int waypointId = calculateWaypointId(playerId, colorId);
            MovingWaypoint.removeWaypoint(playerId, colorId);
            Player p = PlayerUtil.getPlayer(playerId, PlayerUtil.PlayerNameSpace.SERVER);
            if (p instanceof ServerPlayer sp) {
                MovingWaypoint.setWaypoint(sp, anchor, colorId, waypointId, true, null, "contraption (static)");
                sendTrackedToClient(sp, tc.getContraptionUuid(), null, "remove");
                sendTrackedToClient(sp, null, anchor, "add");
            }
        }

        LoggerProject.logInfo("005002", String.format("Contraption waypoint: %s, %s transitioned to static for player %s (color %s)",
            tc.createTag(), anchor, playerId, colorId));
    }

    private void expireStatic(ServerPlayer player, String playerId, ITrackedContrap tc)
    {
        BlockPos anchor = tc.getAnchorPos();
        if (anchor != null) staticContraptions.remove(anchor);
        globalContraptions.remove(tc);

        Map<ITrackedContrap, Integer> colors = waypointColorsByPlayer.get(playerId);
        Integer colorId = colors != null ? colors.remove(tc) : null;
        if (colorId != null) {
            int id = calculateWaypointId(playerId, colorId);
            MovingWaypoint.removeWaypoint(playerId, id);
        }
        if (colors != null && colors.isEmpty()) waypointColorsByPlayer.remove(playerId);
        sendTrackedToClient(player, null, anchor, "remove");

        String message1 = String.format("Contraption waypoint: %s, %s expired", tc.createTag(), anchor);
        String message2 =  String.format(" player %s (color %s) has expired", playerId, colorId);
        AeroWaypointsMain.MESSAGER.sendBottomActionHint(player, message1);
        LoggerProject.logInfo("005001", message1 + message2);

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
        reg.registerOnDataSave(WaypointManager::onDataSave);

        PlayerContrapWaypointData.init();
    }

    public static void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ITrackedContrap.isValidContraption(event.getTarget())) return;
        WaypointManager m = get(event.getLevel());
        if (m != null) m.interact(event);
    }

    // Server-side entry for an already-resolved EntityLike target, used by the Sable sub-level path.
    public static void onPlayerInteract(ServerPlayer sp, EntityLike target) {
        if (sp == null || target == null) return;
        WaypointManager m = get(sp.level());
        if (m != null) m.interact(sp, target);
    }


    private static void onServerStart(ServerStartingEvent event)
    {
        CONFIG = GeneralConfig.getInstance();
        managers.values().forEach(WaypointManager::clear);
        managers.clear();

        globalContraptions.clear();
        globalContraptionsLoaded = false;

        WAYPOINT_TOGGLE_ITEM = HBUtil.ItemUtil.itemNameToItem("create:goggles");
    }

    private static void onServerStopped(ServerStoppedEvent event) {

    }

    private static void onLevelLoad(LevelLoadingEvent.Load event) {
        if(event.getLevel().isClientSide()) return;
        WaypointManager.init((Level) event.getLevel());
        loadSavedContraptions();
    }

    private static void loadSavedContraptions()
    {
        if (globalContraptionsLoaded) return;
        DataStore ds = CONFIG.getDataStore();
        if (ds == null) return;
        globalContraptionsLoaded = true;

        JsonElement raw = ds.getOrCreateWorldSaveData(Constants.MOD_ID).get(GLOBAL_CONTRAPS_KEY);
        if (raw == null || !raw.isJsonArray()) return;
        List<JsonElement> arr = raw.getAsJsonArray().asList();
        for (JsonElement e : arr) {
            JsonObject obj = e.getAsJsonObject();
            UUID id = UUID.fromString(obj.get("id").getAsString());
            UUID entityId = obj.has("entityId") ? UUID.fromString(obj.get("entityId").getAsString()) : null;
            BlockPos lastPos = HBUtil.BlockUtil.stringToBlockPos(obj.get("lastPos").getAsString());
            ITrackedContrap tc = ITrackedContrap.createContraption(id, entityId, lastPos);
            globalContraptions.add(tc);
        }


    }
    //DataSaveEvent, datastoreSave, saveData, onSaveEvent
    private static void onDataSave(DatastoreSaveEvent event)
    {
        JsonArray contrapsJson = new JsonArray();
        for (ITrackedContrap tc : globalContraptions)
        {
            JsonObject json = new JsonObject();
            //add id, entityId (nullable) and lastPos
            json.addProperty("id", tc.getId().toString());
            if(tc.getContraptionUuid()!=null)
                json.addProperty("entityId", tc.getContraptionUuid().toString());
            json.addProperty("lastPos", HBUtil.BlockUtil.positionToString(tc.getAnchorPos()));
            contrapsJson.add(json);
        }
        event.getDataStore().getOrCreateWorldSaveData(Constants.MOD_ID)
            .addProperty(GLOBAL_CONTRAPS_KEY, contrapsJson);
    }

    private static final int BUFFER_RESOLVE_ENTITIES = 15;
    private static int count = 0;
    private static void on20Ticks(ServerTickEvent event) {
        for (WaypointManager manager : managers.values()) {
            if (!manager.trackedContraptions.isEmpty()) {
                manager.tickPrune();
            }
        }

        //get all players and flush their pending waypoints
        if(count < BUFFER_RESOLVE_ENTITIES) {count++; return;}
        for (ServerPlayer sp : HBUtil.PlayerUtil.getAllPlayers()) {
            IManagedPlayer data =  ManagedPlayer.getManagedPlayer(sp).getSubclass(PlayerContrapWaypointData.class);
            if(data instanceof PlayerContrapWaypointData subData) {
                subData.flushPendingWaypoints(sp);
            }
        }
    }


    //** MANAGED PLAYER PERSISTENCE **//

    public static class PlayerContrapWaypointData implements IManagedPlayer {

        private Player p;
        private String id;
        private final List<PendingEntry> pending = new ArrayList<>();

        static class PendingEntry {
            UUID id;
            String levelId;
            int colorId;
            UUID contrapId;
            BlockPos blockPos;
            long startTick;
            boolean isStatic() { return blockPos != null; }
        }

        public static void init() {
            ManagedPlayer.registerManagedPlayerData(PlayerContrapWaypointData.class,
                () -> new PlayerContrapWaypointData(null));
        }

        public PlayerContrapWaypointData(Player player) {
            setPlayer(player);
        }

        @Override public boolean isServerOnly() { return true; }
        @Override public boolean isInit(String subclass) { return true; }
        @Override public IManagedPlayer getStaticInstance(Player player, String id) { return null; }

        @Override
        public void handlePlayerJoin(Player player)
        {
            if (!(player instanceof ServerPlayer sp)) return;
        }

        public void flushPendingWaypoints(ServerPlayer sp)
        {
            if (pending.isEmpty()) return;

            //pending iterator
            Iterator<PendingEntry> it = pending.iterator();
            while(it.hasNext())
            {
                PendingEntry p = it.next();
                Level lvl = HBUtil.LevelUtil.toServerLevel(p.levelId);
                WaypointManager mgr = WaypointManager.get(lvl);
                if (mgr == null) continue;

                ITrackedContrap resolved = globalContraptions.stream().filter(tc -> tc.getId().equals(p.id))
                    .findFirst().orElse(ITrackedContrap.createContraption(p.id, null, p.blockPos));
                try {
                    boolean rm = mgr.restoreEntry(sp, (ServerLevel) lvl, p.colorId, resolved);
                    if (rm) it.remove();
                } catch (Exception e) {}

            }
        }

        @Override
        public void handlePlayerLeave(Player player) {
            if (!(player instanceof ServerPlayer sp)) return;
            for (WaypointManager mgr : managers.values()) {
                mgr.wipePlayerWaypoints(sp);
            }
        }

        @Override
        public CompoundTag serializeNBT()
        {
            CompoundTag tag = new CompoundTag();
            if (p == null || PlayerUtil.getId(p)==null) return tag;
            String playerId = PlayerUtil.getId(p);

            ListTag list = new ListTag();
            for (Map.Entry<Level, WaypointManager> me : managers.entrySet())
            {
                WaypointManager mgr = me.getValue();
                Set<ITrackedContrap> set = mgr.trackedContraptions.get(playerId);
                if (set == null || set.isEmpty()) continue;
                Map<ITrackedContrap, Integer> colors = mgr.waypointColorsByPlayer.get(playerId);
                if (colors == null) continue;
                String levelId = HBUtil.LevelUtil.toLevelId(me.getKey());

                for (ITrackedContrap tc : set)
                {
                    Integer colorId = colors.get(tc);
                    if(colorId == null) continue;

                    CompoundTag c = new CompoundTag();
                    c.putUUID("id", tc.getId());
                    c.putString("levelId", levelId);
                    c.putInt("colorId", colorId);
                    c.putString("pos", HBUtil.BlockUtil.positionToString(tc.getAnchorPos()));
                    if (tc.getContraptionUuid() != null) {
                        c.putUUID("uuid", tc.getContraptionUuid());
                    }
                    list.add(c);
                }
            }
            if (!list.isEmpty()) tag.put("entries", list);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            pending.clear();
            if (nbt == null || !nbt.contains("entries", Tag.TAG_LIST)) return;
            ListTag list = nbt.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++)
            {
                CompoundTag c = list.getCompound(i);
                PendingEntry e = new PendingEntry();
                e.id = c.getUUID("id");
                e.levelId = c.getString("levelId");
                e.colorId = c.getInt("colorId");
                e.blockPos = HBUtil.BlockUtil.stringToBlockPos(c.getString("pos"));
                if (c.hasUUID("uuid")) {
                    e.contrapId = c.getUUID("uuid");
                }
                pending.add(e);
            }
        }

        @Override public void setId(String id) { this.id = id; }
        @Override public void setPlayer(Player player) { if (player != null) this.p = player; }
    }
}
