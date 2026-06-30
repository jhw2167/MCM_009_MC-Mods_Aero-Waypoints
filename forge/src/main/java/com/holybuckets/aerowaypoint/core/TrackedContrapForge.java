package com.holybuckets.aerowaypoint.core;

import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public class TrackedContrapForge implements ITrackedContrap {

    private final TrackedContraption trackedContraption;
    private Contraption contraption;

    public TrackedContrapForge(TrackedContraption trackedContraption) {
        this.trackedContraption = trackedContraption;
    }


    @Override
    public Entity getContraptionEntity() {
        return null;
    }

    @Override
    public BlockPos getAnchorPos() {
        return null;
    }

    @Override
    public java.util.UUID getContraptionUuid() {
        return trackedContraption.getContraptionUuid();
    }

    @Override
    public boolean isAlive() {
        return false;
    }
}
