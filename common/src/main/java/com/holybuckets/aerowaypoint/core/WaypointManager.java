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

public class WaypointManager {

    private static final Logger LOG = LoggerFactory.getLogger("HBs Aero Waypoints");
    public static final long STATIC_WAYPOINT_LIFETIME_TICKS = 24000L;

    private final Level level;
    private final Map<String, Set<ITrackedContrap>> trackedContraptions;
    private final Map<String, Map<ITrackedContrap, Integer>> waypointColorsByPlayer;
    private final Map<BlockPos, ITrackedContrap> staticContraptions;
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
            if (contraption == null) return;
            if(staticContraptions.containsKey(contraption.getAnchorPos()))
                this.tryReactivateStatic(contraption);
            else
                this.track(sp, contraption);
        }
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
        if(!globalContraptions.add(contrap)) return null; //returns true if its a new contrap

        Set<ITrackedContrap> set = trackedContraptions.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        set.add(contrap);

        int colorId = (colorOverride>0) ? colorOverride : getNextColor();
        int waypointId = calculateWaypointId(PlayerUtil.getId(sp), colorId);
        waypointColorsByPlayer.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
            .put(contrap, colorId);

        //c + first 4 letters of UUID
        UUID subLevelUuid = contrap.getSubLevelUuid();
        if (subLevelUuid != null) {
            // Sable-backed ship (Create: Aeronautics): follow by the sub-level UUID.
            // HBs Foundation's Sable EntityLikeResolver resolves this UUID to a live
            // position every tick, so the waypoint stays pinned to the moving ship even
            // after its contraption entity unloads. Assumes Foundation's MovingWaypoint
            // exposes a UUID-following overload (the "linkedEntityUuid" architecture).
            MovingWaypoint.setWaypoint(
                sp,
                contrap.getAnchorPos(),
                colorId,
                waypointId,
                true,
                subLevelUuid,
                contrap.createTag()
            );
            sendTrackedToClient(sp, subLevelUuid, null, "add");
        } else {
            MovingWaypoint.setWaypoint(
                sp,
                contrap.getAnchorPos(),
                colorId,
                waypointId,
                true,
                contrap.getContraptionEntity(),
                contrap.createTag()
            );

            if (contrap.getContraptionEntity() != null)
                sendTrackedToClient(sp, contrap.getContraptionUuid(), null, "add");
            else
                sendTrackedToClient(sp, null, contrap.getAnchorPos(), "add");
        }

        return contrap;
    }

    private void tryReactivateStatic(ITrackedContrap newContrap)
    {
        ITrackedContrap contrap = staticContraptions.remove(newContrap.getAnchorPos());
        if (contrap == null) return;

        for (String playerId : trackedContraptions.keySet() )
        {
            Set<ITrackedContrap> playerContraptions = trackedContraptions.get(playerId);
            if(playerContraptions==null)
                trackedContraptions.put(playerId, ConcurrentHashMap.newKeySet());
            trackedContraptions.get(playerId).add(newContrap);
            contrap.restore(newContrap);

            Integer colorId = waypointColorsByPlayer.get(playerId).get(contrap);
            if (colorId == null) colorId = getNextColor();

            int waypointId = calculateWaypointId(playerId, colorId);
            ServerPlayer sp = (ServerPlayer) PlayerUtil.getPlayer(playerId,
                PlayerUtil.PlayerNameSpace.SERVER);

            MovingWaypoint.setWaypoint(
                sp, contrap.getAnchorPos(),
                colorId, waypointId,
                true,
                contrap.getContraptionEntity(),
                contrap.createTag()
            );
            //send remove message to tracked client
            sendTrackedToClient(sp, null, contrap.getAnchorPos(), "remove");
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
            UUID linkedUuid = tc.getSubLevelUuid() != null ? tc.getSubLevelUuid() : tc.getContraptionUuid();
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
    public boolean restoreEntry(ServerPlayer sp, ServerLevel serverLevel, int colorId, ITrackedContrap data)
    {
        String playerId = PlayerUtil.getId(sp);
        BlockPos pos = data.getAnchorPos();
        if (playerId == null) return false;


        ChunkAccess chunk = level.getChunk(pos);
        ChunkPos cp = new ChunkPos(pos);
        if(chunk == null) {
            HBUtil.ChunkUtil.forceLoadChunk( serverLevel, cp, TICKET_ID);
            return false;
        } else if(HBUtil.ChunkUtil.isChunkForceLoaded(serverLevel, new ChunkPos(pos))) {
            HBUtil.ChunkUtil.unforceLoadChunk(serverLevel, cp, TICKET_ID);
        }
        Entity contrapEntity = serverLevel.getEntity(data.getContraptionUuid());
        ITrackedContrap tc = ITrackedContrap.getContraption(contrapEntity);
        if(contrapEntity==null)
        {
            tc.setStaticPosition(pos);
            tc.setStaticPositionStartTick(CONFIG.getTotalTickCount());
        } else {
            data.restore(tc);
        }

        this.track(sp, tc, colorId);
        return true;
    }


    private void tickPrune()
    {
        if(HBUtil.PlayerUtil.getAllPlayers().isEmpty() || ManagedPlayer.PLAYERS.isEmpty()) return;
        List<ServerPlayer> players = HBUtil.PlayerUtil.getAllPlayers();

        long now = level != null ? level.getGameTime() : 0L;
        for (ServerPlayer player : players)
        {
            String playerId = PlayerUtil.getId(player);
            Iterator<ITrackedContrap> it = trackedContraptions.get(playerId).iterator();
            while (it.hasNext())
            {
                ITrackedContrap tc = it.next();
                Entity ent = tc.getContraptionEntity();

                boolean entityGone = (ent == null || ent.isRemoved());
                if (entityGone && !tc.isStatic() ) {
                    transitionToStatic(playerId, tc, now);
                    continue;
                }
                else if(entityGone && tc.isStatic()) {
                    //proceed to static checks
                }
                else {  //contraption entity alive and well
                    continue;
                }

                if (tc.getAnchorPos() == null) continue;

                //check if the block is loaded and if it is air
                if (level != null && level.isLoaded(tc.getAnchorPos()))
                {
                    List<Entity> entities = level.getEntities((Entity) null,  new AABB(tc.getAnchorPos()),
                        (e) -> !( (e instanceof LivingEntity) || (e instanceof ItemEntity)) );
                    if(entities.isEmpty()) {
                        //no entities, check if the block is air
                    } else {
                        Entity e = entities.get(0);
                        if(ITrackedContrap.isValidContraption(e)) {
                            ITrackedContrap newTc = ITrackedContrap.getContraption(e);
                            this.tryReactivateStatic(newTc);
                            continue;
                        }
                    }

                }


                if(level.getBlockState(tc.getAnchorPos()).isAir()) {
                    expireStatic(player, playerId, tc);
                    it.remove();
                    continue;
                }

                long age = now - tc.getStaticPositionStartTick();
                if (age > STATIC_WAYPOINT_LIFETIME_TICKS) {
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
            if(tc.getContraptionEntity()!=null)
                json.addProperty("entityId", tc.getContraptionEntity().getUUID().toString());
            json.addProperty("lastPos", HBUtil.BlockUtil.positionToString(tc.getAnchorPos()));
            contrapsJson.add(json);
        }
        event.getDataStore().getOrCreateWorldSaveData(Constants.MOD_ID)
            .addProperty(GLOBAL_CONTRAPS_KEY, contrapsJson);
    }


    private static void on20Ticks(ServerTickEvent event) {
        for (WaypointManager manager : managers.values()) {
            if (!manager.trackedContraptions.isEmpty()) {
                manager.tickPrune();
            }
        }

        //get all players and flush their pending waypoints
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
                try {
                    ITrackedContrap resolved = globalContraptions.stream().filter(tc -> p.id.equals(tc.getId())).findAny().get();
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
            if (p == null) return tag;
            String playerId = PlayerUtil.getId(p);
            if (playerId == null) return tag;

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
