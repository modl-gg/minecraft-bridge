package gg.modl.bridge.detection;

public class ViolationRecord {

    private final DetectionSource source;
    private final String checkName;
    private final String verbose;
    private final long timestamp;

    public ViolationRecord(DetectionSource source, String checkName, String verbose) {
        this.source = source;
        this.checkName = checkName;
        this.verbose = verbose;
        this.timestamp = System.currentTimeMillis();
    }

    public DetectionSource getSource() {
        return source;
    }

    public String getCheckName() {
        return checkName;
    }

    public String getVerbose() {
        return verbose;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + source + "] " + checkName + " - " + verbose;
    }
}
