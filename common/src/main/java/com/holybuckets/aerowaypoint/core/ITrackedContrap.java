package com.holybuckets.aerowaypoint.core;

import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.model.EntityLike;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.*;

// A tracked contraption is also an EntityLike so waypoints can follow it directly.
public interface ITrackedContrap extends EntityLike {

    List<ITrackedContrap> GENERATOR = new ArrayList<>(1);
    Set<EntityType<?>> CONTRAPTION_TYPES = new HashSet<>();

    String[] CREATE_CONTRAPTION_IDS = {
        "create:contraption",
        "create:stationary_contraption",
        "create:gantry_contraption",
        "create:carriage_contraption"
    };


    static void init(GeneralConfig config) {
        GENERATOR.clear();
        GENERATOR.add( ( ITrackedContrap) Balm.platformProxy()
            .withNeoForge("com.holybuckets.aerowaypoint.core.TrackedContrapForge")
            .withFabric("com.holybuckets.aerowaypoint.core.TrackedContrapFabric")
            .build());
        GENERATOR.get(0).init(config.getServer());
    }


    static boolean isValidContraption(Entity target) {
        if (target == null) return false;
        return CONTRAPTION_TYPES.contains(target.getType());
    }


    void init(MinecraftServer server);

    //Aero
    UUID getId();

    Entity getContraptionEntity();

    BlockPos getAnchorPos();

    Vec3 getPos();

    UUID getContraptionUuid();

    // EntityLike: the followed UUID is the contraption/sub-level UUID.
    @Override default UUID getUUID() { return getContraptionUuid(); }

    // EntityLike: current position is the contraption/sub-level position.
    @Override default Vec3 position() { return getPos(); }

    // EntityLike: waypoints do not use orientation, so report none.
    @Override default float getYRot() { return 0f; }

    @Override default float getXRot() { return 0f; }

    // True when this contraption is backed by a Sable sub-level (an Aeronautics ship).
    default boolean isSubLevelBacked() { return false; }

    void setStaticPosition(BlockPos pos);

    void setSavedUuid(UUID uuid);

    long getStaticPositionStartTick();

    boolean isStatic();

    void setStaticPositionStartTick(long tick);

    static ITrackedContrap getContraption(EntityLike target) {
        return GENERATOR.get(0).generateContraption(target);
    }

    static ITrackedContrap createContraption(UUID id, UUID entityId, BlockPos lastPos) {
        return GENERATOR.get(0).generateContraption(id, entityId, lastPos);
    }


    ITrackedContrap generateContraption(EntityLike target);

    ITrackedContrap generateContraption(UUID id, UUID entityId, BlockPos lastPos);

    /**
     * Restores a static contraption back to an active entity tracking ITrackedContrap
     * @param newTc the new ITrackedContrap to restore
     * @return
     */
    void restore(ITrackedContrap newTc);

    void restore(EntityLike e);

    String createTag();
}
