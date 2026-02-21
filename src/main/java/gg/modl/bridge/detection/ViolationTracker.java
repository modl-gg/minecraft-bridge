package gg.modl.bridge.detection;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationTracker {

    private static final int MAX_RECORDS_PER_PLAYER = 200;
    private static final long RECORD_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private final ConcurrentHashMap<UUID, List<ViolationRecord>> records = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public void startCleanupTask(JavaPlugin plugin) {
        // Run cleanup every 60 seconds (1200 ticks)
        cleanupTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 1200L, 1200L);
    }

    public void stopCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    public void addViolation(UUID uuid, DetectionSource source, String checkName, String verbose) {
        List<ViolationRecord> playerRecords = records.computeIfAbsent(uuid, k -> new ArrayList<>());
        synchronized (playerRecords) {
            playerRecords.add(new ViolationRecord(source, checkName, verbose));
            if (playerRecords.size() > MAX_RECORDS_PER_PLAYER) {
                playerRecords.remove(0);
            }
        }
    }

    public List<ViolationRecord> getRecords(UUID uuid) {
        List<ViolationRecord> playerRecords = records.get(uuid);
        if (playerRecords == null) return List.of();
        synchronized (playerRecords) {
            return new ArrayList<>(playerRecords);
        }
    }

    public int getViolationCount(UUID uuid, DetectionSource source, String checkName) {
        List<ViolationRecord> playerRecords = records.get(uuid);
        if (playerRecords == null) return 0;
        synchronized (playerRecords) {
            return (int) playerRecords.stream()
                    .filter(r -> r.getSource() == source && r.getCheckName().equalsIgnoreCase(checkName))
                    .count();
        }
    }

    public void resetPlayer(UUID uuid) {
        records.remove(uuid);
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - RECORD_TTL_MS;
        for (var entry : records.entrySet()) {
            List<ViolationRecord> list = entry.getValue();
            synchronized (list) {
                list.removeIf(r -> r.getTimestamp() < cutoff);
            }
            // Don't remove empty entries here — resetPlayer handles that on quit.
            // Removing here races with addViolation/getViolationCount since
            // computeIfAbsent can return a list that's about to be removed.
        }
    }
}
