package cz.mitalnet.mapRemover;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ResidenceManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.Extent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public final class MapRemover extends JavaPlugin {
    private World world;
    private Configuration configuration;
    private ResidenceManager residenceManager;

    @Override
    public void onEnable() {
        // Inicializace světa
        world = Bukkit.getWorld("world");
        if (world == null) {
            getLogger().severe("Svět 'world' nebyl nalezen. Plugin nebude fungovat správně.");
            return;
        }
        // Načtení konfigurace
        saveDefaultConfig();
        getLogger().info("MapRemover byl úspěšně aktivován!");
        configuration = getConfig();

        if(configuration.getStringList("ObydleneResky").isEmpty()){
            configuration.set("ObydleneResky", List.of());
            saveConfig();
        }
        residenceManager = Residence.getInstance().getResidenceManager();
    }

    @Override
    public void onDisable() {
        getLogger().info("MapRemover byl deaktivován.");
    }
    public void startLogPopulatedChunks() {
        // Kontrola, zda je svět načten
        if (world == null) {
            getLogger().warning("Svět není načten, nelze spustit logování chunků.");
            return;
        }
        int skipSteps = configuration.getInt("SkipSteps", 0);
        // Výpočet poloměru v chunkách na základě velikosti světové hranice
        int radius = (int) (world.getWorldBorder().getSize() / 16);
        // Rozdělení práce na dávky
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            for (int x = -radius + skipSteps; x < radius; x++) { // Iterace po větších krocích
                getLogger().info("Pracuju neruš (" + (x + radius) + "/" + (radius * 2) + ")");
                for (int z = -radius; z < radius; z++) {
                    boolean residenceFound = false;
                    for (int x1 = x - 5; x1 < x + 5; x1++) {
                        for (int z1 = z - 5; z1 < z - 5; z1++) {
                            Chunk chunk = world.getChunkAt(x1, z1);
                            // Kontrola Residence a přidání do seznamu
                            if (!residenceManager.getByChunk(chunk).isEmpty()) {
                                residenceFound = true;
                            }
                            if (residenceFound) {
                                break;
                            }
                        }
                        if (residenceFound) {
                            break;
                        }
                    }
                    configuration.set("SkipSteps", x + radius);
                    if(residenceFound){
                        regenChunk(x,z,world);
                    }
                    saveConfig();
                }
            }
            configuration.set("completed", true);
            saveConfig();
        });
    }
    public void regenChunk(int x,int z,World world){
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            try {
                Extent extent = BukkitAdapter.adapt(world);
                extent.regenerateChunk(x, z, null, world.getSeed());
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Tento příkaz může použít pouze hráč.");
            return true;
        }
        if (!sender.getName().equals("mitalnet_")) {
            return false;
        }

        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "mitalchunklogger":
                startLogPopulatedChunks();
                player.sendMessage(ChatColor.GREEN + "Obydlené se začaly skenovat.");
                return true;
            default:
                return false;
        }
    }
}
