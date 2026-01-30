package anon.def9a2a4.dynlight;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class DynLightPlugin extends JavaPlugin {

    private static DynLightPlugin instance;

    private DynLightConfig config;
    private LightSourceManager sourceManager;
    private LightRenderer renderer;
    private BuiltInDetector detector;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        saveDefaultConfig();
        this.config = new DynLightConfig(getConfig());

        // Initialize components
        this.sourceManager = new LightSourceManager();
        this.renderer = new LightRenderer(config);
        this.detector = new BuiltInDetector(sourceManager, config);

        // Register event listeners
        getServer().getPluginManager().registerEvents(renderer, this);

        // Start update loop
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                detector.detectBuiltInSources();
                renderer.updateAllPlayers(sourceManager.getAllLightSources());
            }
        }.runTaskTimer(this, 0L, config.updateInterval);

        new Metrics(this, 29162);
        getLogger().info("DynLight plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (renderer != null) {
            renderer.clearAllPlayers();
        }
        getLogger().info("DynLight plugin disabled!");
    }

    /**
     * Get the DynLight API for other plugins to register light sources.
     *
     * @return The DynLightAPI instance
     */
    public static DynLightAPI getAPI() {
        return instance.sourceManager;
    }
}
