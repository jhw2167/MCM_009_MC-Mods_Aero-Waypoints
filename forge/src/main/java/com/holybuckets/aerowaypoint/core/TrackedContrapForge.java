package com.holybuckets.aerowaypoint.core;

import com.holybuckets.foundation.HBUtil;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class TrackedContrapForge implements ITrackedContrap {

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
    public TrackedContrapForge() {
    }

    //Create
    public TrackedContrapForge(Contraption contraption) {
        this.contraption = contraption;
    }

    //Aero


    @Override
    public Entity getContraptionEntity() {
        return this.contraption != null ? this.contraption.entity : null;
    }

    @Override
    public BlockPos getAnchorPos() {
        if (this.contraption != null) return this.contraption.anchor;
        return this.savedAnchor;
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
        }
        return "";
    }

    @Override
    public BlockPos getStaticPosition() {
        return this.staticPosition;
    }

    @Override
    public void setStaticPosition(BlockPos pos) {
        this.staticPosition = pos;
    }

    @Override
    public long getStaticPositionStartTick() {
        return this.staticPositionStartTick;
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
        return null;
    }

    @Override
    public ITrackedContrap restoreStatic(UUID uuid, BlockPos anchor, long startTick) {
        TrackedContrapForge tc = new TrackedContrapForge();
        tc.savedUuid = uuid;
        tc.savedAnchor = anchor;
        tc.staticPosition = anchor;
        tc.staticPositionStartTick = startTick;
        return tc;
    }

}
