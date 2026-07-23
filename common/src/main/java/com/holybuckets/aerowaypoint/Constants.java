package com.holybuckets.aerowaypoint;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Constants {

	public static final String MOD_ID = "hbs_aerowaypoint";
	public static final String MOD_NAME = "HBs Aero Waypoints";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

	public static final List<BlockPos> LOCAL_POINTS = new ArrayList<>();
	static {
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					LOCAL_POINTS.add(new BlockPos(x, y, z));
				}
			}
		}
	}
}