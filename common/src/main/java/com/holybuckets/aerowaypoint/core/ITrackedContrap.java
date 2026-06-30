package com.holybuckets.aerowaypoint.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public interface ITrackedContrap {

    Entity getContraptionEntity();

    BlockPos getAnchorPos();

    UUID getContraptionUuid();

    boolean isAlive();
}
