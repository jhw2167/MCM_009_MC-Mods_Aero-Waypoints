package com.holybuckets.aerowaypoint.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public class TrackedContraption {

    private final Level level;
    private final ITrackedContrap contrap;
    private Entity trackingEntity;

    public TrackedContraption(Level level, ITrackedContrap contrap) {
        this.level = level;
        this.contrap = contrap;
    }

    public Level getLevel() {
        return level;
    }

    public ITrackedContrap getContrap() {
        return contrap;
    }

    public BlockPos getBlockPos() {
        return contrap.getAnchorPos();
    }

    public UUID getContraptionUuid() {
        return contrap.getContraptionUuid();
    }

    public Entity getContraptionEntity() {
        return contrap.getContraptionEntity();
    }

    public Entity getTrackingEntity() {
        return trackingEntity;
    }

    public void setTrackingEntity(Entity e) {
        this.trackingEntity = e;
    }

    public boolean isAlive() {
        return contrap != null && contrap.isAlive();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackedContraption tc)) return false;
        return Objects.equals(getContraptionUuid(), tc.getContraptionUuid());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getContraptionUuid());
    }
}
