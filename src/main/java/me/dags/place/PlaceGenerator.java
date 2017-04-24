package me.dags.place;

import com.flowpowered.math.vector.Vector3i;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.biome.BiomeTypes;
import org.spongepowered.api.world.extent.ImmutableBiomeVolume;
import org.spongepowered.api.world.extent.MutableBiomeVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.gen.BiomeGenerator;
import org.spongepowered.api.world.gen.GenerationPopulator;
import org.spongepowered.api.world.gen.WorldGenerator;
import org.spongepowered.api.world.gen.WorldGeneratorModifier;
import org.spongepowered.api.world.storage.WorldProperties;

/**
 * @author dags <dags@dags.me>
 */
public class PlaceGenerator implements GenerationPopulator, BiomeGenerator, WorldGeneratorModifier {

    private static final Vector3i min = new Vector3i(-512, 2, -512);
    private static final Vector3i max = new Vector3i(512, 2, 512);

    private final BlockState base = BlockTypes.BEDROCK.getDefaultState();
    private final BlockState canvas = BlockTypes.SNOW.getDefaultState();
    private final Cause cause;

    PlaceGenerator(Cause cause) {
        this.cause = cause;
    }

    @Override
    public String getId() {
        return "place:place";
    }

    @Override
    public String getName() {
        return "place";
    }

    @Override
    public void modifyWorldGenerator(WorldProperties world, DataContainer settings, WorldGenerator worldGenerator) {
        Sponge.getRegistry().getAllOf(BiomeType.class)
                .forEach(biome -> worldGenerator.getBiomeSettings(biome).getPopulators().clear());

        worldGenerator.getPopulators().clear();
        worldGenerator.setBiomeGenerator(this);
        worldGenerator.setBaseGenerationPopulator(this);
    }

    @Override
    public void populate(World world, MutableBlockVolume buffer, ImmutableBiomeVolume biomes) {
        setLayer(buffer, 0, base);
        setLayer(buffer, 1, canvas);
    }

    @Override
    public void generateBiomes(MutableBiomeVolume buffer) {
        for (int x = buffer.getBiomeMin().getX(); x <= buffer.getBiomeMax().getX(); x++) {
            for (int z = buffer.getBiomeMin().getZ(); z <= buffer.getBiomeMax().getZ(); z++) {
                buffer.setBiome(x, 0, z, BiomeTypes.BEACH);
            }
        }
    }

    private void setLayer(MutableBlockVolume buffer, int height, BlockState state) {
        for (int x = buffer.getBlockMin().getX(); x <= buffer.getBlockMax().getX(); x++) {
            for (int z = buffer.getBlockMin().getZ(); z <= buffer.getBlockMax().getZ(); z++) {
                if (x >= min.getX() && x <= max.getX() && z >= min.getZ() && z <= max.getZ()) {
                    buffer.setBlock(x, height, z, state, cause);
                }
            }
        }
    }

    static Vector3i getMin() {
        return min;
    }

    static Vector3i getMax() {
        return max;
    }

    static boolean contains(Vector3i pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean contains(int x, int y, int z) {
        return x >= min.getX() && x <= max.getX() && y >= min.getY() && y <= max.getY() && z >= min.getZ() && z <= max.getZ();
    }
}
