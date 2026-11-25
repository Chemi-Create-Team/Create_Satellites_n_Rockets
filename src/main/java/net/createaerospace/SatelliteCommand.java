package net.createaerospace;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.createaerospace.orbit.OrbitMath;
import net.createaerospace.orbit.Satellite;
import net.createaerospace.orbit.SatelliteData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /satellite list|add|remove for operators (add makes a geostationary one unless given a lower tier): list every satellite with its current position, or place and delete them directly. */
public final class SatelliteCommand {
    private SatelliteCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("satellite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "name"), Satellite.GEOSTATIONARY_TIER))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 3))
                                .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "name"), IntegerArgumentType.getInteger(c, "tier"))))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "name"))))));
    }

    static String position(Satellite s, long tick) {
        double[] p = OrbitMath.subpoint(s, tick);
        return String.format("%s  %s %s  %d km  %d min/orbit", s.name(), OrbitMath.fmtLat(p[0]), OrbitMath.fmtLon(p[1]),
                s.altKm(), s.periodTicks() / 1200);
    }

    private static int list(CommandSourceStack src) {
        var all = SatelliteData.get(src.getServer()).list();
        long now = src.getServer().overworld().getGameTime();
        if (all.isEmpty()) src.sendSuccess(() -> Component.translatable("command.create_aerospace_engineering.none"), false);
        all.forEach(s -> src.sendSuccess(() -> Component.literal(position(s, now)), false));
        return all.size();
    }

    private static int add(CommandSourceStack src, String name, int tier) {
        var data = SatelliteData.get(src.getServer());
        long now = src.getServer().overworld().getGameTime();
        var id = src.getEntity() != null ? src.getEntity().getUUID() : java.util.UUID.nameUUIDFromBytes("server".getBytes());
        Satellite s = Satellite.deployed(name, id, src.getTextName(), OrbitMath.lonOf(src.getPosition().x), now).retuned(tier, tier == Satellite.GEOSTATIONARY_TIER ? 0 : 2, now);
        data.put(src.getServer(), s);
        src.sendSuccess(() -> Component.literal(position(s, now)), true);
        return 1;
    }

    private static int remove(CommandSourceStack src, String name) {
        var data = SatelliteData.get(src.getServer());
        int n = 0;
        for (Satellite s : data.list()) if (s.name().equals(name)) {
            data.remove(src.getServer(), s.id());
            n++;
        }
        int removed = n;
        src.sendSuccess(() -> Component.translatable("command.create_aerospace_engineering.removed", removed), true);
        return n;
    }
}
