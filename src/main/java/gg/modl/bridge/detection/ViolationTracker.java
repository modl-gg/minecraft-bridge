package gg.modl.bridge.detection;

import gg.modl.bridge.config.BridgeConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationTracker {

    private static final int MAX_RECORDS_PER_CHECK = 50;

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Double>> violationLevels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, List<ViolationRecord>>> violationRecords = new ConcurrentHashMap<>();

    private final BridgeConfig config;
    private BukkitTask decayTask;

    public ViolationTracker(BridgeConfig config) {
        this.config = config;
    }

    public void startDecayTask(JavaPlugin plugin) {
        long intervalTicks = config.getDecayInterval() * 20L;
        decayTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::decayAll, intervalTicks, intervalTicks);
    }

    public void stopDecayTask() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    public double addViolation(UUID uuid, DetectionSource source, String checkName, String verbose) {
        String key = compositeKey(source, checkName);

        ConcurrentHashMap<String, Double> playerLevels = violationLevels.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        double newVL = playerLevels.merge(key, 1.0, Double::sum);

        ConcurrentHashMap<String, List<ViolationRecord>> playerRecords = violationRecords.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        List<ViolationRecord> records = playerRecords.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (records) {
            records.add(new ViolationRecord(source, checkName, verbose));
            if (records.size() > MAX_RECORDS_PER_CHECK) {
                records.remove(0);
            }
        }

        return newVL;
    }

    public double getViolationLevel(UUID uuid, DetectionSource source, String checkName) {
        String key = compositeKey(source, checkName);
        ConcurrentHashMap<String, Double> playerLevels = violationLevels.get(uuid);
        if (playerLevels == null) return 0.0;
        return playerLevels.getOrDefault(key, 0.0);
    }

    public List<ViolationRecord> getRecords(UUID uuid, DetectionSource source, String checkName) {
        String key = compositeKey(source, checkName);
        ConcurrentHashMap<String, List<ViolationRecord>> playerRecords = violationRecords.get(uuid);
        if (playerRecords == null) return List.of();
        List<ViolationRecord> records = playerRecords.get(key);
        if (records == null) return List.of();
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    public void resetPlayer(UUID uuid) {
        violationLevels.remove(uuid);
        violationRecords.remove(uuid);
    }

    public void resetCheck(UUID uuid, DetectionSource source, String checkName) {
        String key = compositeKey(source, checkName);

        ConcurrentHashMap<String, Double> playerLevels = violationLevels.get(uuid);
        if (playerLevels != null) {
            playerLevels.remove(key);
        }

        ConcurrentHashMap<String, List<ViolationRecord>> playerRecords = violationRecords.get(uuid);
        if (playerRecords != null) {
            playerRecords.remove(key);
        }
    }

    private void decayAll() {
        double decayAmount = config.getDecayAmount();

        for (Map.Entry<UUID, ConcurrentHashMap<String, Double>> playerEntry : violationLevels.entrySet()) {
            ConcurrentHashMap<String, Double> levels = playerEntry.getValue();
            Iterator<Map.Entry<String, Double>> it = levels.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Double> entry = it.next();
                double newVal = entry.getValue() - decayAmount;
                if (newVal <= 0) {
                    it.remove();
                    // Also remove records for this check
                    ConcurrentHashMap<String, List<ViolationRecord>> playerRecords = violationRecords.get(playerEntry.getKey());
                    if (playerRecords != null) {
                        playerRecords.remove(entry.getKey());
                    }
                } else {
                    entry.setValue(newVal);
                }
            }
            if (levels.isEmpty()) {
                violationLevels.remove(playerEntry.getKey());
                violationRecords.remove(playerEntry.getKey());
            }
        }
    }

    public static String compositeKey(DetectionSource source, String checkName) {
        return source.name().toLowerCase() + ":" + checkName.toLowerCase();
    }
}
