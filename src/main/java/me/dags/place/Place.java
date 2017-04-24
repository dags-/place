package me.dags.place;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import me.dags.commandbus.CommandBus;
import me.dags.commandbus.annotation.Caller;
import me.dags.commandbus.annotation.Command;
import me.dags.commandbus.annotation.Join;
import me.dags.commandbus.annotation.Permission;
import me.dags.commandbus.format.FMT;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStoppingEvent;
import org.spongepowered.api.event.world.LoadWorldEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.GeneratorTypes;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.WorldArchetype;
import org.spongepowered.api.world.difficulty.Difficulties;
import org.spongepowered.api.world.gen.WorldGeneratorModifier;
import org.spongepowered.api.world.storage.WorldProperties;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author dags <dags@dags.me>
 */
@Plugin(id = "place", name = "place", version = "1.0", description = "r/place")
public class Place implements CacheLoader<String, PlaceStats> {

    private final ConfigurationOptions options = ConfigurationOptions.defaults().setShouldCopyDefaults(true);
    private final ConfigurationLoader<CommentedConfigurationNode> loader;
    private final LoadingCache<String, PlaceStats> cache;
    private final Cause cause;
    private int wait = 30;

    private final Set<UUID> worlds = new HashSet<>();
    private PlaceGenerator placeWorld;
    private ConfigurationNode root;

    @Inject
    public Place(@DefaultConfig(sharedRoot = false) ConfigurationLoader<CommentedConfigurationNode> loader, PluginContainer container) {
        this.loader = loader;
        this.cause = Cause.source(container).build();
        this.cache = Caffeine.<String, PlaceStats>newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .build(this);
    }

    private void save(ConfigurationNode node) {
        if (node != null) {
            try {
                loader.save(node);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public PlaceStats load(@Nonnull String key) throws Exception {
        return new PlaceStats(root.getNode(key));
    }

    @Listener
    public void reload(GameReloadEvent event) {
        cache.invalidateAll();

        try {
            root = loader.load(options);
        } catch (IOException e) {
            root = loader.createEmptyNode();
        }

        if (root != null) {
            wait = root.getNode("_settings", "wait").getInt(wait);
            save(root);
        }
    }

    @Listener
    public void init(GameInitializationEvent event) {
        placeWorld = new PlaceGenerator(cause);
        CommandBus.create().register(this).submit(this);
        Sponge.getRegistry().register(WorldGeneratorModifier.class, placeWorld);
        Task.builder().interval(5, TimeUnit.MINUTES).execute(() -> {
            save(root);
            cache.cleanUp();
        }).submit(this);
        reload(null);
    }

    @Listener
    public void stop(GameStoppingEvent event) {
        save(root);
    }

    @Listener (order = Order.POST)
    public void world(LoadWorldEvent event) {
        World world = event.getTargetWorld();
        if (world.getWorldGenerator().getBaseGenerationPopulator() instanceof PlaceGenerator) {
            worlds.add(world.getUniqueId());
            world.getProperties().setGameRule("doFireTick", "false");
            world.getProperties().setGameRule("doMobSpawning", "false");
            world.getProperties().setGameRule("doDaylightCycle", "false");
            world.getProperties().setGameRule("randomTickSpeed", "0");
            world.getProperties().setDifficulty(Difficulties.PEACEFUL);
            world.getProperties().setWorldTime(6000L);
        }
    }

    @Listener (order = Order.PRE)
    public void primary(InteractBlockEvent.Primary event) {
        if (worlds.contains(event.getTargetBlock().getWorldUniqueId())) {
            event.setCancelled(true);
        }
    }

    @Listener
    public void secondary(InteractBlockEvent.Secondary event, @First Player player) {
        if (worlds.contains(event.getTargetBlock().getWorldUniqueId())) {
            event.setCancelled(true);

            boolean replace = true;
            Vector3i target = event.getTargetBlock().getPosition();

            if (!PlaceGenerator.contains(target)) {
                replace = false;
                target = target.add(event.getTargetSide().asBlockOffset());
            }

            if (PlaceGenerator.contains(target)) {
                final Vector3i point = target;
                final boolean replaced = replace;

                player.getItemInHand(HandTypes.MAIN_HAND).flatMap(stack -> stack.get(Keys.ITEM_BLOCKSTATE)).ifPresent(state -> {
                    World world = player.getWorld();
                    PlaceStats stats = cache.get(player.getIdentifier());

                    long current = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
                    long stamp = TimeUnit.MILLISECONDS.toSeconds(stats.getTimestamp(world.getName()));
                    long elapsed = current - stamp;

                    if (elapsed < wait) {
                        FMT.error("You must wait ").stress(wait - elapsed).error(" seconds before you can place another block").tell(player);
                    } else {
                        Vector3d pos = point.toDouble();

                        if (world.getBlock(point).getType() != BlockTypes.AIR) {
                            world.spawnParticles(ParticleEffect.builder().type(ParticleTypes.BREAK_BLOCK).build(), pos);
                            world.playSound(SoundTypes.BLOCK_PISTON_EXTEND, pos, 0.8);
                        }

                        world.setBlock(point, state, cause);
                        world.playSound(SoundTypes.ENTITY_ARROW_HIT_PLAYER, pos, 0.8);

                        stats.setTimestamp(world.getName());

                        if (replaced) {
                            stats.incReplacements(world.getName());
                        } else {
                            stats.incPlacements(world.getName());
                        }
                    }
                });
            }
        }
    }

    @Listener (order = Order.PRE)
    public void place(ChangeBlockEvent.Place event, @First Player player) {
        if (worlds.contains(player.getWorld().getUniqueId())) {
            event.setCancelled(true);
            FMT.warn("You cannot use that here").tell(player);
        }
    }

    @Permission("place.command.create")
    @Command(alias = "create", parent = "place")
    public void create(@Caller Player source, @Join("name") String name) {
        name = name.toLowerCase();

        if (Sponge.getServer().getWorld(name).isPresent()) {
            FMT.error("A world by the name %s already exists", name).tell(source);
        } else {
            FMT.info("Creating world %s...", name).tell(source);

            WorldArchetype worldArchetype = WorldArchetype.builder()
                    .dimension(DimensionTypes.OVERWORLD)
                    .difficulty(Difficulties.PEACEFUL)
                    .generator(GeneratorTypes.FLAT)
                    .generatorModifiers(placeWorld)
                    .gameMode(GameModes.CREATIVE)
                    .loadsOnStartup(true)
                    .pvp(false)
                    .build(name, name);

            try {
                WorldProperties properties = Sponge.getServer().createWorldProperties(name, worldArchetype);
                Sponge.getServer().loadWorld(properties).ifPresent(world -> {
                    FMT.info("Successfully created world %s", world.getName());
                    world.getProperties().setSpawnPosition(new Vector3i(0, 3, 0));
                    source.setLocation(world.getSpawnLocation());
                });
            } catch (IOException e) {
                e.printStackTrace();
                FMT.warn("An error occurred generating world %s, see the console", name).tell(source);
            }
        }
    }

    @Permission("place.command.stats")
    @Command(alias = "stats", parent = "place")
    public void stats(@Caller Player source) {
        stats(source, source, source.getWorld().getName());
    }

    @Permission("place.command.stats")
    @Command(alias = "stats", parent = "place")
    public void stats(@Caller CommandSource source, User other, String world) {
        if (Sponge.getServer().getWorld(world).isPresent()) {
            PlaceStats stats = cache.get(other.getIdentifier());

            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss - dd MMM yyyy");
            Date date = new Date(stats.getTimestamp(world));

            FMT.info("Placed: ").stress(stats.getPlacements(world))
                    .info(", Replaced: ").stress(stats.getReplacements(world))
                    .info(", Last Action: ").stress(format.format(date))
                    .tell(source);
        } else {
            FMT.error("World %s does not exist", world).tell(source);
        }
    }
}
