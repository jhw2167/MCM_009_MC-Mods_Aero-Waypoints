package com.holybuckets.aerowaypoint.command;

//Project imports

import com.holybuckets.aerowaypoint.core.WaypointManager;
import com.holybuckets.foundation.event.CommandRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandList {

    public static final String CLASS_ID = "033";
    private static final String PREFIX = "hb";

    public static void register() {
        CommandRegistry.register(FindMyBlimp::delete);
        CommandRegistry.register(FindMyBlimp::deleteAll);
    }

    // Suggests the colorIds of the player's currently tracked blimps.
    private static final SuggestionProvider<CommandSourceStack> BLIMP_ID_SUGGESTIONS =
        (context, builder) -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer sp)) {
                return SharedSuggestionProvider.suggest(new String[0], builder);
            }
            WaypointManager mgr = WaypointManager.get(sp.level());
            if (mgr == null) return SharedSuggestionProvider.suggest(new String[0], builder);
            return SharedSuggestionProvider.suggest(
                mgr.getTrackedColorIds(sp).stream().map(i -> Integer.toString(i)), builder);
        };

    //1. /hb findMyBlimp delete <id> | deleteAll
    private static class FindMyBlimp {

        // /hb findMyBlimp delete <id>
        private static LiteralArgumentBuilder<CommandSourceStack> delete() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("findMyBlimp")
                    .then(Commands.literal("delete")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                            .suggests(BLIMP_ID_SUGGESTIONS)
                            .executes(context -> executeDelete(context.getSource(),
                                IntegerArgumentType.getInteger(context, "id")))
                        )
                    )
                );
        }

        // /hb findMyBlimp deleteAll
        private static LiteralArgumentBuilder<CommandSourceStack> deleteAll() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("findMyBlimp")
                    .then(Commands.literal("deleteAll")
                        .executes(context -> executeDeleteAll(context.getSource()))
                    )
                );
        }

        private static int executeDelete(CommandSourceStack source, int id) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("This command can only be used by players"));
                return 0;
            }
            WaypointManager mgr = WaypointManager.get(player.level());
            if (mgr == null) {
                source.sendFailure(Component.literal("No waypoint manager for this level"));
                return 0;
            }
            if (mgr.untrackByColorId(player, id)) {
                source.sendSuccess(() -> Component.literal("Removed blimp waypoint " + id), false);
                return 1;
            }
            source.sendFailure(Component.literal("No blimp waypoint with id " + id));
            return 0;
        }

        private static int executeDeleteAll(CommandSourceStack source) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("This command can only be used by players"));
                return 0;
            }
            WaypointManager mgr = WaypointManager.get(player.level());
            if (mgr == null) {
                source.sendFailure(Component.literal("No waypoint manager for this level"));
                return 0;
            }
            int count = mgr.untrackAll(player);
            source.sendSuccess(() -> Component.literal("Removed " + count + " blimp waypoint(s)"), false);
            return 1;
        }
    }
    //END COMMAND

}
//END CLASS COMMANDLIST
