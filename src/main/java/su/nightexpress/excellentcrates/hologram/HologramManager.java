package su.nightexpress.excellentcrates.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Config;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.hologram.entity.FakeDisplay;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntity;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntityGroup;
import su.nightexpress.excellentcrates.hologram.handler.HologramPacketsHandler;
import su.nightexpress.excellentcrates.hologram.handler.HologramProtocolHandler;
import su.nightexpress.excellentcrates.hologram.listener.HologramListener;
import su.nightexpress.excellentcrates.hooks.HookId;
import su.nightexpress.excellentcrates.util.CrateUtils;
import su.nightexpress.excellentcrates.util.pos.WorldPos;
import su.nightexpress.excellentcrates.util.server.FoliaUtils;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.Plugins;
import su.nightexpress.nightcore.util.placeholder.Replacer;

import java.util.*;

public class HologramManager extends AbstractManager<CratesPlugin> {

    private final Map<String, FakeDisplay> displayMap;

    private HologramHandler handler;

    public HologramManager(@NotNull CratesPlugin plugin) {
        super(plugin);
        this.displayMap = new HashMap<>();
    }

    @Override
    protected void onLoad() {
        if (this.detectHandler()) {
            this.addListener(new HologramListener(this.plugin, this));

            if (!FoliaUtils.isFolia()) {
                this.addTask(this::tickHolograms, Config.CRATE_HOLOGRAM_UPDATE_INTERVAL.get());
            }
            else {
                this.plugin.warn("Crate holograms on Folia are updated by player movement instead of a global world scan.");
            }
        }
    }

    @Override
    protected void onShutdown() {
        this.displayMap.values().forEach(this::discard);
        this.displayMap.clear();

        this.handler = null;
    }

    private boolean detectHandler() {
        if (Plugins.isInstalled(HookId.PACKET_EVENTS)) {
            this.handler = new HologramPacketsHandler();
        }
        else if (Plugins.isInstalled(HookId.PROTOCOL_LIB)) {
            this.handler = new HologramProtocolHandler();
        }
        else {
            this.plugin.warn("*".repeat(25));
            this.plugin.warn("You have no packet library plugins installed for the Holograms feature to work.");
            this.plugin.warn("Please install one of the following plugins to enable crate holograms: " + HookId.PACKET_EVENTS + " or " + HookId.PROTOCOL_LIB);
            this.plugin.warn("*".repeat(25));
        }

        return this.hasHandler();
    }

    private void tickHolograms() {
        this.plugin.getCrateManager().getCrates().forEach(crate -> {
            if (!crate.isHologramEnabled()) return;

            this.render(crate);
        });
    }

    public boolean hasHandler() {
        return this.handler != null;
    }

    @Nullable
    private FakeDisplay getDisplay(@NotNull Crate crate) {
        return this.displayMap.get(crate.getId());
    }

    public void disableBlockHologram(@NotNull Crate crate, @NotNull WorldPos blockPos) {
        this.toggleBlockHologram(crate, blockPos, false);
    }

    public void enableBlockHologram(@NotNull Crate crate, @NotNull WorldPos blockPos) {
        this.toggleBlockHologram(crate, blockPos, true);
    }

    private void toggleBlockHologram(@NotNull Crate crate, @NotNull WorldPos blockPos, boolean enabled) {
        FakeDisplay display = this.getDisplay(crate);
        if (display == null) return;

        FakeEntityGroup group = display.getGroup(blockPos);
        if (group == null) return;

        group.setDisabled(!enabled);

        if (group.isDisabled()) {
            this.discard(group);
        }
        else {
            this.render(crate);
        }
    }



    public void removeForViewer(@NotNull Player player) {
        this.displayMap.values().forEach(display -> this.removeForViewer(player, display));
    }

    public void removeForViewer(@NotNull Player player, @NotNull FakeDisplay display) {
        display.getGroups().forEach(group -> this.removeForViewer(player, group));
    }

    public void removeForViewer(@NotNull Player player, @NotNull FakeEntityGroup group) {
        group.removeViewer(player);
        this.handler.sendDestroyEntityPacket(player, group.getEntityIDs());
    }



    public void discard(@NotNull Crate crate) {
        FakeDisplay display = this.displayMap.remove(crate.getId());
        if (display == null) return;

        this.discard(display);
    }

    public void discard(@NotNull FakeDisplay display) {
        display.getGroups().forEach(this::discard);
    }

    public void discard(@NotNull FakeEntityGroup group) {
        group.clearViewers();
        this.handler.sendDestroyEntityPacket(group.getEntityIDs());
    }

    public void renderForViewer(@NotNull Player player) {
        this.plugin.getCrateManager().getCrates().forEach(crate -> {
            if (!crate.isHologramEnabled()) return;

            this.render(crate, player);
        });
    }

    public void render(@NotNull Crate crate, @NotNull Player player) {
        this.createIfAbsent(crate, player);

        FakeDisplay display = this.getDisplay(crate);
        if (display == null) return;

        List<String> text = Replacer.create().replace(crate.replacePlaceholders()).apply(crate.getHologramText().reversed());
        if (text.isEmpty()) return;

        String worldName = player.getWorld().getName();
        Location playerLocation = player.getLocation();

        for (FakeEntityGroup group : display.getGroups()) {
            if (group.isDisabled()) continue;

            WorldPos blockPosition = group.getBlockPosition();
            if (!blockPosition.getWorldName().equals(worldName)) {
                this.removeForViewer(player, group);
                continue;
            }

            double distanceSquared = playerLocation.distanceSquared(this.toCenterLocation(player.getWorld(), blockPosition, crate.getHologramYOffset() + 0.2D));
            double range = Config.CRATE_EFFECTS_VISIBILITY_DISTANCE.get();
            if (distanceSquared > range * range) {
                this.removeForViewer(player, group);
                continue;
            }

            boolean needSpawn = !group.isViewer(player);

            List<String> hologramText = Replacer.create().replacePlaceholderAPI(player).apply(text);
            List<FakeEntity> holograms = group.getEntities();
            for (int index = 0; index < holograms.size(); index++) {
                String line = index >= hologramText.size() ? "" : hologramText.get(index);
                FakeEntity entity = holograms.get(index);
                this.handler.sendHologramPackets(player, entity, needSpawn, line);
            }

            group.addViewer(player);
        }
    }



    public void render(@NotNull Crate crate) {
        this.createIfAbsent(crate);

        FakeDisplay display = this.getDisplay(crate);
        if (display == null) return;

        List<String> text = Replacer.create().replace(crate.replacePlaceholders()).apply(crate.getHologramText().reversed());
        if (text.isEmpty()) return;

        for (FakeEntityGroup group : display.getGroups()) {
            if (group.isDisabled()) continue;

            WorldPos blockPosition = group.getBlockPosition();
            World world = blockPosition.getWorld();
            Location location = blockPosition.toLocation();

            if (!blockPosition.isChunkLoaded() || world == null || location == null) {
                this.discard(group); // Remove all viewers and send entity destroy packet.
                continue;
            }

            List<Player> players = new ArrayList<>(world.getPlayers());
            players.removeIf(player -> {
                if (CrateUtils.isInEffectRange(player, location)) return false;

                this.removeForViewer(player, group);
                return true;
            });

            if (players.isEmpty()) {
                this.discard(group); // Remove all viewers and send entity destroy packet.
                continue;
            }

            players.forEach(player -> {
                boolean needSpawn = !group.isViewer(player);

                List<String> hologramText = Replacer.create().replacePlaceholderAPI(player).apply(text);
                List<FakeEntity> holograms = group.getEntities();
                for (int index = 0; index < holograms.size(); index++) {
                    // Fix for fake entity's text not being updated/replaced when text size is less than holograms amount, so force it to empty string.
                    String line = index >= hologramText.size() ? "" : hologramText.get(index);
                    FakeEntity entity = holograms.get(index);
                    this.handler.sendHologramPackets(player, entity, needSpawn, line);
                }

                group.addViewer(player);
            });
        }
    }

    private void createIfAbsent(@NotNull Crate crate) {
        this.createIfAbsent(crate, null);
    }

    private void createIfAbsent(@NotNull Crate crate, @Nullable Player viewer) {
        if (!this.hasHandler()) return;

        List<String> originText = crate.getHologramText();
        if (originText.isEmpty()) return;

        FakeDisplay display = this.displayMap.computeIfAbsent(crate.getId(), id -> new FakeDisplay());

        double yOffset = crate.getHologramYOffset() + 0.2;
        double lineGap = Config.CRATE_HOLOGRAM_LINE_GAP.get();

        crate.getBlockPositions().forEach(blockPos -> {
            if (display.getGroup(blockPos) != null) return;

            Location baseLocation = viewer == null ? this.toCenterLocation(blockPos, yOffset) : this.toCenterLocation(viewer, blockPos, yOffset);
            if (baseLocation == null) return;

            // Allocate ID values for our fake entities, so there is no clash with new server entities.

            FakeEntityGroup group = display.getGroupOrCreate(blockPos);

            for (int index = 0; index < originText.size(); index++) {
                double gap = lineGap * index;

                Location location = baseLocation.clone().add(0, gap, 0);
                group.addEntity(FakeEntity.create(location));
            }
        });
    }

    @Nullable
    private Location toCenterLocation(@NotNull Player viewer, @NotNull WorldPos blockPos, double yOffset) {
        World world = viewer.getWorld();
        if (!blockPos.getWorldName().equals(world.getName())) return null;

        return this.toCenterLocation(world, blockPos, yOffset);
    }

    @Nullable
    private Location toCenterLocation(@NotNull WorldPos blockPos, double yOffset) {
        World world = blockPos.getWorld();
        if (world == null) return null;

        return this.toCenterLocation(world, blockPos, yOffset);
    }

    @NotNull
    private Location toCenterLocation(@NotNull World world, @NotNull WorldPos blockPos, double yOffset) {
        return new Location(world, blockPos.getX() + 0.5D, blockPos.getY() + 1.0D + yOffset, blockPos.getZ() + 0.5D);
    }

}
