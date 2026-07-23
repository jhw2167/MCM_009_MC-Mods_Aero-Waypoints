package com.holybuckets.aerowaypoint.core;

import com.holybuckets.aerowaypoint.compat.SableSubLevelEntityLike;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.model.EntityLike;
import com.holybuckets.foundation.model.VanillaEntityLike;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.UUID;

public class TrackedContrapForge implements ITrackedContrap {

    private UUID id;
    private Contraption contraption;
    private SubLevel subLevel;
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
    public TrackedContrapForge(SubLevel subLevel) {
        this();
        this.subLevel = subLevel;
        if (subLevel != null) this.savedUuid = subLevel.getUniqueId();
    }

    // Sub-level ships are tracked by persistent UUID, not the live reference.
    @Override
    public boolean isSubLevelBacked() {
        return this.subLevel != null;
    }


    @Override
    public UUID getId() {
        return this.id;
    }


    @Override
    public Entity getContraptionEntity() {
        return this.contraption != null ? this.contraption.entity : null;
    }

    @Override   // we want to catch the last position of the entity if it suddenly becomes unviable
    public BlockPos getPos() {
        Vec3 innerPos = innerPos();
        if(innerPos != null)
            staticPosition = HBUtil.BlockUtil.toBlockPos(innerPos);
        else
            savedAnchor = staticPosition;

        return staticPosition;
    }

    @Override
    public BlockPos getSavedAnchorPos() {
        return savedAnchor != null ? savedAnchor : staticPosition;
    }


    private Vec3 innerPos() {
        if (contraption != null && contraption.entity != null && !contraption.entity.isRemoved())
             return contraption.entity.position();
        if (subLevel != null && !subLevel.isRemoved()) {
            Vec3 pos = subLevelPos(subLevel);
            if(!pos.equals(Vec3.ZERO)) return pos;
        }
        return null;
    }

    // World-space center of a sub-level's global bounding box.
    private static Vec3 subLevelPos(SubLevel subLevel) {
        Vector3dc center = subLevel.lastPose().position();
        return new Vec3(center.x(), center.y(), center.z());
    }

    // EntityLike: dimension of the backing contraption entity or sub-level.
    @Override
    public ResourceLocation dimension() {
        if (this.contraption != null && this.contraption.entity != null)
            return this.contraption.entity.level().dimension().location();
        if (this.subLevel != null)
            return this.subLevel.getLevel().dimension().location();
        return null;
    }

    // EntityLike: valid while the sub-level or contraption entity still exists.
    @Override
    public boolean isValid() {
        if (this.subLevel != null) return !this.subLevel.isRemoved();
        Entity e = getContraptionEntity();
        return e != null && !e.isRemoved();
    }

    @Override
    public UUID getContraptionUuid() {
        if (this.contraption != null && this.contraption.entity != null)
            return this.contraption.entity.getUUID();
        if(this.subLevel != null)
            return this.subLevel.getUniqueId();

        return this.savedUuid;
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
    public void convertToStatic(long tick) {
        this.staticPositionStartTick = tick;
        this.contraption = null;
        this.subLevel = null;
    }


    @Override
    public ITrackedContrap generateContraption(EntityLike target)
    {
        if(target instanceof VanillaEntityLike ent) {
            if(ent.entity() instanceof AbstractContraptionEntity abc)
                return new TrackedContrapForge(abc.getContraption());
        } else if(target instanceof SableSubLevelEntityLike sub) {
            return new TrackedContrapForge(sub.getSubLevel());
        }
        return new TrackedContrapForge();
    }

    @Override
    public ITrackedContrap generateContraption(UUID id, UUID entityId, BlockPos lastPos) {
        TrackedContrapForge tc = new TrackedContrapForge();
        tc.id = id;
        tc.savedUuid = entityId;
        tc.staticPosition = lastPos;
        tc.staticPositionStartTick = -1;
        return tc;
    }



        @Override
    public void restore(ITrackedContrap newTc) {
        this.contraption = ((TrackedContrapForge)newTc).contraption;
        this.subLevel = ((TrackedContrapForge)newTc).subLevel;
        this.savedUuid = newTc.getContraptionUuid();
        this.staticPositionStartTick = -1;
    }

    @Override
    public void restore(EntityLike newTc) {
        if(newTc instanceof VanillaEntityLike ent) {
            if(ent.entity() instanceof AbstractContraptionEntity abc)
                this.contraption = abc.getContraption();
        } else if(newTc instanceof SableSubLevelEntityLike sub) {
            this.subLevel = sub.getSubLevel();
            if (this.subLevel != null) this.savedUuid = this.subLevel.getUniqueId();
        }
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
