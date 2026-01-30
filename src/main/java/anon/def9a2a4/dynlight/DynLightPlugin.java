package anon.def9a2a4.dynlight;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class DynLightPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        new Metrics(this, 29162);
        getLogger().info("DynLight plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DynLight plugin disabled!");
    }
}
