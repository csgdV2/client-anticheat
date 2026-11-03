package combat_tracker.detection;

public record ExternalFinding(String id, String label, String category, int count,
                              int samples, int score, String detail, long firstEpochMs,
                              long lastEpochMs) {
}
