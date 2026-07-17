package com.holybuckets.aerowaypoint.core;

import com.holybuckets.foundation.HBUtil;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.level.SubLevel;
import dev.ryanhcode.sable.level.SubLevelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class TrackedContrapForge implements ITrackedContrap {

    private UUID id;
    private Contraption contraption;
    private BlockPos staticPosition;
    private long staticPositionStartTick;

    private UUID savedUuid;
    private BlockPos savedAnchor;
    private UUID savedSubLevelUuid;

    @Override
    public void init(MinecraftServer server) {
        var registry = server.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        for (String id : CREATE_CONTRAPTION_IDS) {
            EntityType<?> type = registry.get(HBUtil.LOC(id));
            if (type != null) ITrackedContrap.CONTRAPTION_TYPES.add(type);
        }
    }

    //noArgs
    public TrackedContrapForge() {
        super();
        id = UUID.randomUUID();
        this.staticPositionStartTick = -1;
    }

    //Create
    public TrackedContrapForge(Contraption contraption) {
        this();
        this.contraption = contraption;
    }

    //Aero
    @Override
    public UUID getId() {
        return this.id;
    }


    @Override
    public Entity getContraptionEntity() {
        return this.contraption != null ? this.contraption.entity : null;
    }

    @Override
    public BlockPos getAnchorPos() {
        if (this.contraption != null) return this.contraption.anchor;
        if(this.savedAnchor!=null) return this.savedAnchor;
        return this.staticPosition;
    }

    @Override
    public Vec3 getPos() {
        if (this.contraption != null && this.contraption.entity != null) return this.contraption.entity.position();
        return this.savedAnchor != null ? Vec3.atCenterOf(this.savedAnchor) : null;
    }

    @Override
    public UUID getContraptionUuid() {
        if (this.contraption != null && this.contraption.entity != null) return this.contraption.entity.getUUID();
        return this.savedUuid;
    }

    /**
     * If this contraption is a Create: Aeronautics ship, return the UUID of the
     * Sable sub-level backing it so the waypoint can follow the ship via
     * Foundation's Sable {@link com.holybuckets.aerowaypoint.compat.aeronautics.SableEntityResolver}
     * even once the contraption entity unloads.
     *
     * <p><b>VERIFY AGAINST THE SABLE / CREATE: AERONAUTICS JARS</b> — this is the
     * one place that maps a Create contraption to its Sable sub-level. The lookup
     * below assumes the sub-level containing the contraption entity's position is
     * the ship's own sub-level. If Create: Aeronautics exposes the sub-level
     * directly on the ship contraption/entity, prefer that. Returns {@code null}
     * for ordinary (non-Sable) contraptions, which is the correct fallthrough.</p>
     */
    @Override
    public UUID getSubLevelUuid() {
        Entity e = getContraptionEntity();
        if (e == null || e.level() == null) return this.savedSubLevelUuid;
        try {
            SubLevel sl = SubLevelManager.get(e.level()).getContaining(e.blockPosition());
            if (sl != null) {
                this.savedSubLevelUuid = sl.getUUID();
                return this.savedSubLevelUuid;
            }
        } catch (Throwable ignored) {
            // Sable not present / API mismatch — fall through to non-ship behavior.
        }
        return this.savedSubLevelUuid;
    }

    //string createTag()
    @Override
    public String createTag() {
        if(this.contraption!=null) {
            String type = this.contraption.getType().toString().substring(0,3);
            String uu = "STATIC";
            if(this.contraption.entity!=null) {
                uu = this.contraption.entity.getUUID().toString().substring(0, 4);
            }
            return "C:" + type + "@" + uu;
        } else if(this.staticPosition!=null) {
           return HBUtil.BlockUtil.positionToString(this.staticPosition);
        }
        return "Contraption";
    }

    @Override
    public void setStaticPosition(BlockPos pos) {
        this.staticPosition = pos;
    }

    @Override
    public void setSavedUuid(UUID uuid) {
        this.savedUuid = uuid;
    }

    @Override
    public long getStaticPositionStartTick() {
        return this.staticPositionStartTick;
    }

    @Override
    public boolean isStatic() {
        return this.staticPositionStartTick > -1;
    }

    @Override
    public void setStaticPositionStartTick(long tick) {
        this.staticPositionStartTick = tick;
    }


    @Override
    public ITrackedContrap generateContraption(Entity target) {
        if(target instanceof AbstractContraptionEntity abc) {
            return new TrackedContrapForge(abc.getContraption());
        } else {
            //aero stuff
        }
        return new TrackedContrapForge();
    }

    @Override
    public ITrackedContrap generateContraption(UUID id, UUID entityId, BlockPos lastPos) {
        TrackedContrapForge tc = new TrackedContrapForge();
        tc.id = id;
        tc.savedUuid = entityId;
        tc.savedAnchor = lastPos;
        tc.staticPosition = lastPos;
        tc.staticPositionStartTick = -1;
        return tc;
    }



        @Override
    public void restore(ITrackedContrap newTc) {
        this.contraption = ((TrackedContrapForge)newTc).contraption;
        this.savedUuid = newTc.getContraptionUuid();
        this.savedAnchor = newTc.getAnchorPos();
        this.staticPositionStartTick = -1;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ITrackedContrap that)) return false;
        UUID myId = this.id;
        UUID otherId = that.getId();
        return myId != null && myId.equals(otherId);
    }

    @Override
    public int hashCode() {
        return this.id != null ? this.id.hashCode() : 0;
    }

}
