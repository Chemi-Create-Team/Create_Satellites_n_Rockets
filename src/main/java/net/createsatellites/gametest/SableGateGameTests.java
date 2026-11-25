package net.createsatellites.gametest;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.createsatellites.CreateSatellites;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateSatellites.MOD_ID)
@PrefixGameTestTemplate(false)
public class SableGateGameTests {
    @GameTest(template = "test_empty")
    public static void sableIsRunningInTheTestServer(GameTestHelper h) {
        var container = SubLevelContainer.getContainer(h.getLevel());
        h.assertTrue(container != null, "Sable gives every level a sub-level container");
        h.succeed();
    }
}
