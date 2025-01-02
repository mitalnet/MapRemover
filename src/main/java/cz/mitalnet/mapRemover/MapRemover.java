package cz.mitalnet.mapRemover;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ResidenceManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.Extent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Entity;
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

    /**
     * Zaznamenává obydlené chunky.
     * <p>
     * Tato metoda asynchronně prochází všechny chunky ve světě a zaznamenává ty,
     * které jsou obydlené (obsahují rezidence). Výsledky jsou uloženy do konfigurace.
     */
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
                    if (!residenceManager.getByChunk(world.getChunkAt(x, z)).isEmpty()) {
                        if (!configuration.getStringList("ObydleneResky").contains(x + "@" + z)) {
                            configuration.getStringList("ObydleneResky").add(x + "@" + z);
                            saveConfig();
                            getLogger().info("Zaznamenáno (" + x + "@" + z + ")");
                        } else {
                            getLogger().info("Nezaznamenáno (" + x + "@" + z + ") první test");
                        }
                        break;
                    }
                    boolean residenceSearched = false;
                    for (int x1 = x - 5; x1 < x + 5; x1++) {
                        for (int z1 = z - 5; z1 < z - 5; z1++) {
                            Chunk chunk = world.getChunkAt(x1, z1);
                            // Kontrola Residence a přidání do seznamu
                            if (!residenceManager.getByChunk(chunk).isEmpty()) {
                                if (!configuration.getStringList("ObydleneResky").contains(x + "@" + z)) {
                                    configuration.getStringList("ObydleneResky").add(x + "@" + z);
                                    getLogger().info("Zaznamenáno (" + x + "@" + z + ")");
                                    residenceSearched = true;
                                } else {
                                    getLogger().info("Nezaznamenáno (" + x + "@" + z + ")");
                                }
                            }
                            if (residenceSearched) {
                                break;
                            }
                        }
                        if (residenceSearched) {
                            break;
                        }
                    }
                    configuration.set("SkipSteps", x + radius);
                    saveConfig();
                }
            }
            configuration.set("completed", true);
            saveConfig();
        });
    }

    /**
     * Odstraňuje neobydlené chunky na základě konfigurace.
     */
    public void removeChunks() {
        if (world == null) {
            getLogger().warning("Svět není načten, nelze spustit odstraňování chunků.");
            return;
        }

        Configuration configuration = getConfig();
        List<String> obydeneResky = configuration.getStringList("ObydleneResky");
        int radius = (int) (world.getWorldBorder().getSize() / 16);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int totalChunks = (radius * 2) * (radius * 2);
            int processedChunks = 0;

            for (int x = -radius; x < radius; x++) {
                for (int z = -radius; z < radius; z++) {
                    if (!obydeneResky.contains(x + "@" + z)) {
                        try {
                            World we = (World) BukkitAdapter.adapt(world);
                            Extent extent = BukkitAdapter.adapt(we);
                            extent.regenerateChunk(x, z, null, world.getSeed());
                        } catch (Exception e) {
                            getLogger().warning("Chyba při odstraňování chunku (" + x + "@" + z + ")");
                        }
                    }

                    processedChunks++;

                    // Vypiš průběh každých 10 % nebo poslední chunk
                    if (processedChunks % (totalChunks / 10) == 0 || processedChunks == totalChunks) {
                        int progressPercent = (int) ((processedChunks / (double) totalChunks) * 100);
                        getLogger().info("Průběh odstranění chunků: " + progressPercent + "% (" + processedChunks + "/" + totalChunks + ")");
                    }
                }
            }

            getLogger().info("Odstraňování chunků dokončeno!");
        });
    }




    /**
     * Zpracování příkazů.
     */
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

            case "mitalchunkremover":
                player.sendMessage(ChatColor.YELLOW + "Odstraňování chunků právě probíhá...");
                removeChunks();
                player.sendMessage(ChatColor.GREEN + "Chunky byly úspěšně odstraněny.");
                return true;
            default:
                return false;
        }
    }
}
