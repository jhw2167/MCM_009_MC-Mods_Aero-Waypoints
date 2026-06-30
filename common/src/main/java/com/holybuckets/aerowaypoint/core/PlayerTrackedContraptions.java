package com.holybuckets.aerowaypoint.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.modelInterface.IManagedPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.holybuckets.foundation.player.ManagedPlayer.registerManagedPlayerData;

public class PlayerTrackedContraptions implements IManagedPlayer {

    Player p;
    private String id;
    private final Map<String, Set<UUID>> trackedByLevel = new HashMap<>();

    public PlayerTrackedContraptions(Player player) {
        setPlayer(player);
    }

    public static void init() {
        registerManagedPlayerData(PlayerTrackedContraptions.class, () -> new PlayerTrackedContraptions(null));
    }

    public void add(Level level, UUID contraptionUuid) {
        if (level == null || contraptionUuid == null) return;
        trackedByLevel.computeIfAbsent(levelKey(level), k -> new HashSet<>()).add(contraptionUuid);
    }

    public boolean remove(Level level, UUID contraptionUuid) {
        if (level == null || contraptionUuid == null) return false;
        Set<UUID> set = trackedByLevel.get(levelKey(level));
        if (set == null) return false;
        boolean removed = set.remove(contraptionUuid);
        if (set.isEmpty()) trackedByLevel.remove(levelKey(level));
        return removed;
    }

    public boolean contains(Level level, UUID contraptionUuid) {
        Set<UUID> set = trackedByLevel.get(levelKey(level));
        return set != null && set.contains(contraptionUuid);
    }

    public Set<UUID> getTracked(Level level) {
        Set<UUID> set = trackedByLevel.get(levelKey(level));
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public Map<String, Set<UUID>> getAllTracked() {
        return Map.copyOf(trackedByLevel);
    }

    public void clearLevel(Level level) {
        if (level == null) return;
        trackedByLevel.remove(levelKey(level));
    }

    public void clearAll() {
        trackedByLevel.clear();
    }

    private static String levelKey(Level level) {
        return HBUtil.LevelUtil.toLevelId(level);
    }

    @Override public boolean isServerOnly() { return true; }
    @Override public boolean isInit(String subclass) { return true; }

    @Override
    public IManagedPlayer getStaticInstance(Player player, String id) {
        return null;
    }

    @Override
    public void handlePlayerJoin(Player player) {
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag levels = new ListTag();
        for (Map.Entry<String, Set<UUID>> entry : trackedByLevel.entrySet()) {
            CompoundTag levelTag = new CompoundTag();
            levelTag.putString("levelId", entry.getKey());
            ListTag uuids = new ListTag();
            for (UUID uuid : entry.getValue()) {
                CompoundTag u = new CompoundTag();
                u.putUUID("uuid", uuid);
                uuids.add(u);
            }
            levelTag.put("uuids", uuids);
            levels.add(levelTag);
        }
        tag.put("trackedByLevel", levels);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        trackedByLevel.clear();
        if (!nbt.contains("trackedByLevel", Tag.TAG_LIST)) return;
        ListTag levels = nbt.getList("trackedByLevel", Tag.TAG_COMPOUND);
        for (int i = 0; i < levels.size(); i++) {
            CompoundTag levelTag = levels.getCompound(i);
            String levelId = levelTag.getString("levelId");
            if (levelId == null || levelId.isEmpty()) continue;
            Set<UUID> set = new HashSet<>();
            ListTag uuids = levelTag.getList("uuids", Tag.TAG_COMPOUND);
            for (int j = 0; j < uuids.size(); j++) {
                CompoundTag u = uuids.getCompound(j);
                if (u.hasUUID("uuid")) set.add(u.getUUID("uuid"));
            }
            if (!set.isEmpty()) trackedByLevel.put(levelId, set);
        }
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public void setPlayer(Player player) {
        if (player != null) this.p = player;
    }

    public ServerPlayer getServerPlayer() {
        return p instanceof ServerPlayer sp ? sp : null;
    }
}
