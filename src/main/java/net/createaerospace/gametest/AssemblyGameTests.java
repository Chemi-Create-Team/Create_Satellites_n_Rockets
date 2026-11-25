package net.createaerospace.gametest;

import net.createaerospace.CreateAerospace;
import net.createaerospace.assembly.AssemblyRecipes;
import net.createaerospace.assembly.ToolType;
import net.createaerospace.block.AssemblyJigBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateAerospace.MOD_ID)
@PrefixGameTestTemplate(false)
public class AssemblyGameTests {
    private static final BlockPos JIG = new BlockPos(1, 2, 1);
    private static final String MEMORY = "magnetic_core_memory", BEAM = "welded_iron_beam";

    private static AssemblyJigBlockEntity jig(GameTestHelper h, String recipe) {
        h.setBlock(JIG, CreateAerospace.ASSEMBLY_JIG.get().defaultBlockState());
        var jig = (AssemblyJigBlockEntity) h.getBlockEntity(JIG);
        var r = AssemblyRecipes.get(CreateAerospace.id(recipe));
        h.assertTrue(r != null, "the sample recipe " + recipe + " is loaded");
        jig.setRecipe(r);
        return jig;
    }

    @GameTest(template = "test_empty")
    public static void sampleRecipesLoad(GameTestHelper h) {
        var memory = AssemblyRecipes.get(CreateAerospace.id(MEMORY));
        var beam = AssemblyRecipes.get(CreateAerospace.id(BEAM));
        h.assertTrue(memory != null && beam != null, "both sample recipes load");
        h.assertTrue(memory.stages() == 3 && memory.steps().size() == 6, "the memory has three stages and six steps");
        h.assertTrue(beam.steps().get(2).seamDirection() != null, "the weld knows which side its neighbour is on");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void laterStagesWaitAndSteps_goToOneArmNearestFirst(GameTestHelper h) {
        var jig = jig(h, MEMORY);
        BlockPos a = h.absolutePos(JIG.west(3)), b = h.absolutePos(JIG.east(3));
        h.assertTrue(jig.reserve(a, ToolType.WIRING_HEAD, Vec3.atCenterOf(a)) == -1, "wiring waits for the frame");
        h.assertTrue(jig.reserve(a, ToolType.WELDING_TORCH, Vec3.atCenterOf(a)) == -1, "welding waits for the wiring");
        int first = jig.reserve(a, ToolType.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(first >= 0 && jig.recipe.steps().get(first).rel().getX() == 0, "the west arm takes the west cell first");
        int second = jig.reserve(b, ToolType.GRIPPER, Vec3.atCenterOf(b));
        h.assertTrue(second >= 0 && second != first && jig.recipe.steps().get(second).rel().getX() == 2, "the east arm takes another one, the east cell");
        int third = jig.reserve(a, ToolType.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(third >= 0 && third != first && third != second, "no step is given twice");
        h.assertTrue(jig.reserve(a, ToolType.GRIPPER, Vec3.atCenterOf(a)) == -1, "nothing is left in the stage");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void aReservationWithoutArmReturns(GameTestHelper h) {
        var jig = jig(h, BEAM);
        BlockPos a = h.absolutePos(JIG.west(3));
        int s = jig.reserve(a, ToolType.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(s >= 0 && jig.status[s] == AssemblyJigBlockEntity.RESERVED, "reserved");
        h.runAfterDelay(45, () -> {
            h.assertTrue(jig.status[s] == AssemblyJigBlockEntity.PENDING, "the step is open again once no arm holds it");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void finishingRemovesTheBlocksAndDropsTheProduct(GameTestHelper h) {
        var jig = jig(h, BEAM);
        BlockPos a = h.absolutePos(JIG.west(3));
        for (int stage = 0; stage < 2; stage++) {
            ToolType tool = stage == 0 ? ToolType.GRIPPER : ToolType.WELDING_TORCH;
            int s;
            while ((s = jig.reserve(a, tool, Vec3.atCenterOf(a))) >= 0) {
                if (jig.recipe.steps().get(s).place()) h.assertTrue(jig.place(s), "the block is placed into the free cell");
                else jig.complete(s);
            }
        }
        h.assertTrue(jig.allDone(), "all steps are done");
        h.assertTrue(h.getBlockState(JIG.above()).is(Blocks.IRON_BLOCK), "the frame stands");
        h.runAfterDelay(50, () -> {
            h.assertTrue(h.getBlockState(JIG.above()).isAir() && h.getBlockState(JIG.above().east()).isAir(), "the temporary blocks are gone");
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(JIG)).inflate(3));
            h.assertTrue(items.stream().anyMatch(i -> i.getItem().is(CreateAerospace.WELDED_IRON_BEAM.get())), "the product appears");
            h.assertTrue(jig.status[0] == AssemblyJigBlockEntity.PENDING, "the jig is ready for the next one");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void placingNeverOverwritesABlock(GameTestHelper h) {
        var jig = jig(h, BEAM);
        h.setBlock(JIG.above(), Blocks.DIRT.defaultBlockState());
        BlockPos a = h.absolutePos(JIG.west(3));
        int s = jig.reserve(a, ToolType.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(!jig.place(s) || jig.recipe.steps().get(s).rel().getX() != 0, "an occupied cell is not overwritten");
        h.assertTrue(h.getBlockState(JIG.above()).is(Blocks.DIRT), "the dirt is still there");
        h.succeed();
    }
}
