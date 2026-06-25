package su.nightexpress.excellentcrates.util.server;

public class FoliaUtils {

    private static final boolean FOLIA = hasClass("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaUtils() {

    }

    public static boolean isFolia() {
        return FOLIA;
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
