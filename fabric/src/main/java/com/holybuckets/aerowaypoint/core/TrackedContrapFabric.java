package com.holybuckets.aerowaypoint.core;

import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.model.EntityLike;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class TrackedContrapFabric implements ITrackedContrap {

    private UUID id;
    private Contraption contraption;
    private BlockPos staticPosition;
    private long staticPositionStartTick;

    private UUID savedUuid;
    private BlockPos savedAnchor;

    @Override
    public void init(MinecraftServer server) {
        var registry = server.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        for (String id : CREATE_CONTRAPTION_IDS) {
            EntityType<?> type = registry.get(HBUtil.LOC(id));
            if (type != null) ITrackedContrap.CONTRAPTION_TYPES.add(type);
        }
    }

    //noArgs
    public TrackedContrapFabric() {
        super();
        id = UUID.randomUUID();
        this.staticPositionStartTick = -1;
    }

    //Create
    public TrackedContrapFabric(Contraption contraption) {
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

    // EntityLike: dimension of the backing contraption entity, if any.
    @Override
    public ResourceLocation dimension() {
        Entity e = getContraptionEntity();
        return e != null ? e.level().dimension().location() : null;
    }

    // EntityLike: valid while the contraption entity still exists.
    @Override
    public boolean isValid() {
        Entity e = getContraptionEntity();
        return e != null && !e.isRemoved();
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
    public ITrackedContrap generateContraption(EntityLike target) {
        if(target instanceof AbstractContraptionEntity abc) {
            return new TrackedContrapFabric(abc.getContraption());
        } else {
            //aero stuff
        }
        return new TrackedContrapFabric();
    }

    @Override
    public ITrackedContrap generateContraption(UUID id, UUID entityId, BlockPos lastPos) {
        TrackedContrapFabric tc = new TrackedContrapFabric();
        tc.id = id;
        tc.savedUuid = entityId;
        tc.savedAnchor = lastPos;
        tc.staticPosition = lastPos;
        tc.staticPositionStartTick = -1;
        return tc;
    }



    @Override
    public void restore(ITrackedContrap newTc) {
        this.contraption = ((TrackedContrapFabric)newTc).contraption;
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
