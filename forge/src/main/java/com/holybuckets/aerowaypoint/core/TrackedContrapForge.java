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
        return this.contraption.entity;
    }

    @Override
    public BlockPos getAnchorPos() {
        return this.contraption.anchor;
    }

    @Override
    public Vec3 getPos() {
        return this.contraption.entity.position();
    }

    @Override
    public UUID getContraptionUuid() {
        return this.contraption.entity.getUUID();
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

}
