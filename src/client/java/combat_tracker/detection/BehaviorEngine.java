package combat_tracker.detection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BehaviorEngine {
    public static final String DISCLAIMER = "Behavioral Evidence. Not definitive proof of cheating.";
    private final Map<String, ExternalFinding> findings = new LinkedHashMap<>();
    private final ShieldBehavior shields = new ShieldBehavior();
    private final List<Double> reactions = new ArrayList<>();
    private final List<Double> swaps = new ArrayList<>();
    private final List<Double> snaps = new ArrayList<>();
    private long targetSince;
    private long lastFrame;
    private long lastEligibleAcquisition;
    private long lastReactionAcquisition;
    private long outsideSince;
    private int target = -1;
    private int frameSamples;
    private boolean targetTimingEligible;
    private double previousAimError = Double.NaN;
    private int previousAimTarget = -1;
    private long epochMs;

    public void reset() {
        findings.clear();
        shields.reset();
        clearTransient();
    }

    public void pause() {
        clearTransient();
    }

    private void clearTransient() {
        reactions.clear(); swaps.clear(); snaps.clear();
        shields.pause();
        targetSince = lastFrame = lastEligibleAcquisition = lastReactionAcquisition = outsideSince = 0;
        target = -1;
        frameSamples = 0;
        previousAimError = Double.NaN;
        previousAimTarget = -1;
        targetTimingEligible = false;
    }

    public void frame(long nano, long epoch, int newTarget, int aimTarget, double aimError, double rotationDegrees) {
        epochMs = epoch;
        long elapsed = lastFrame == 0 ? Long.MAX_VALUE : nano - lastFrame;
        if (elapsed <= 0 || elapsed > 50_000_000L) {
            targetTimingEligible = false;
            previousAimError = Double.NaN;
            frameSamples = 0;
            snaps.clear();
        }
        if (newTarget != target) {
            boolean independentlyAcquired = target == -1 && outsideSince != 0
                    && nano - outsideSince >= 200_000_000L;
            target = newTarget;
            targetSince = nano;
            targetTimingEligible = newTarget >= 0 && independentlyAcquired
                    && elapsed > 0 && elapsed <= 25_000_000L
                    && (lastEligibleAcquisition == 0 || nano - lastEligibleAcquisition >= 300_000_000L);
            if (targetTimingEligible) lastEligibleAcquisition = nano;
            if (newTarget < 0) outsideSince = nano;
        }
        if (aimTarget != previousAimTarget) {
            previousAimError = Double.NaN;
            frameSamples = 0;
            snaps.clear();
        }
        previousAimTarget = aimTarget;
        if (aimTarget >= 0 && elapsed > 0 && elapsed <= 25_000_000L && Double.isFinite(aimError)) {
            frameSamples++;
            if (Double.isFinite(previousAimError) && previousAimError >= 15.0
                    && rotationDegrees >= 15.0 && aimError <= 0.15) {
                snaps.add(aimError);
            }
            if (frameSamples >= 600) {
                if (snaps.size() >= 24 && stats(snaps).sd <= 0.04) {
                    evidence("aim", "Repeated precise aim corrections", snaps.size(), frameSamples, 20,
                            String.format(Locale.ROOT, "%d corrections from at least 15 degrees to within 0.15 degrees of target centre in %d sampled frames; experimental heuristic.", snaps.size(), frameSamples));
                }
                snaps.clear();
                frameSamples = 0;
            }
            previousAimError = aimError;
        } else {
            previousAimError = Double.NaN;
        }
        lastFrame = nano;
    }

    public void attack(long nano, long epoch, int attackedTarget, long clickNano, boolean charged) {
        epochMs = epoch;
        if (!charged || !targetTimingEligible || attackedTarget != target || targetSince == lastReactionAcquisition
                || lastFrame == 0 || nano - lastFrame > 50_000_000L || clickNano < targetSince
                || clickNano > nano || nano - clickNano > 100_000_000L) return;
        lastReactionAcquisition = targetSince;
        double latency = (clickNano - targetSince) / 1_000_000.0;
        if (latency > 500) return;
        reactions.add(latency);
        if (reactions.size() >= 40) {
            Stats s = stats(reactions);
            if (s.mean <= 50 && s.sd <= 5) {
                evidence("target-timing", "Repeated target acquisition timing", 1, reactions.size(), 25,
                        String.format(Locale.ROOT, "%d independent target acquisitions: observed click delay mean %.2f ms, SD %.2f ms. Measurements are frame limited and are not human-reaction-time proof.", reactions.size(), s.mean, s.sd));
            }
            reactions.clear();
        }
    }

    public void shieldSwap(double ms, long epoch) {
        epochMs = epoch;
        if (!Double.isFinite(ms) || ms < 1 || ms > 2000) return;
        swaps.add(ms);
        if (swaps.size() >= 24) {
            Stats s = stats(swaps);
            if (s.mean <= 350 && s.sd <= 10) {
                evidence("shield-sequence", "Repeated shield breaker sequence", 1, swaps.size(), 20,
                        String.format(Locale.ROOT, "%d sword → axe → blocking-target attack → sword sequences; duration mean %.2f ms, SD %.2f ms. This records attack attempts, not server-confirmed shield disables.", swaps.size(), s.mean, s.sd));
            }
            swaps.clear();
        }
    }

    private void evidence(String id, String label, int count, int samples, int score, String detail) {
        ExternalFinding old = findings.get(id);
        findings.put(id, new ExternalFinding(id, label, "BEHAVIORAL", old == null ? count : old.count() + count,
                samples, score, detail, old == null ? epochMs : old.firstEpochMs(), epochMs));
    }

    public ShieldBehavior shields() {
        return shields;
    }

    public List<ExternalFinding> findings() {
        List<ExternalFinding> out = new ArrayList<>(findings.values());
        out.addAll(shields.findings());
        return List.copyOf(out);
    }

    public int currentScore(long nowEpochMs) {
        double total = 0;
        for (ExternalFinding finding : findings()) {
            total += finding.score() * Math.pow(0.5, Math.max(0, nowEpochMs - finding.lastEpochMs()) / 60_000.0);
        }
        return (int) Math.min(100, Math.round(total));
    }

    private record Stats(double mean, double sd) {
    }

    private static Stats stats(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double square = 0;
        for (double value : values) square += (value - mean) * (value - mean);
        return new Stats(mean, Math.sqrt(square / Math.max(1, values.size())));
    }
}
