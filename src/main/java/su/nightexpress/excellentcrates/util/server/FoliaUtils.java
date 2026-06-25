package su.nightexpress.excellentcrates.util.server;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public class FoliaUtils {

    private static final boolean FOLIA = hasClass("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaUtils() {

    }

    public static boolean isFolia() {
        return FOLIA;
    }

    @Nullable
    public static Object runAtFixedRate(@NotNull Player player, @NotNull Plugin plugin, @NotNull Runnable runnable, long delayTicks, long periodTicks) {
        if (!FOLIA) return null;

        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
            return method.invoke(scheduler, plugin, (Consumer<Object>) task -> runnable.run(), null, delayTicks, periodTicks);
        }
        catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not start Folia entity task for " + player.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    public static void cancelTask(@Nullable Object task) {
        if (task == null) return;

        try {
            task.getClass().getMethod("cancel").invoke(task);
        }
        catch (ReflectiveOperationException ignored) {

        }
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        }
        catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
