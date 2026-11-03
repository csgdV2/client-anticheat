package combat_tracker.detection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShieldBehavior {
    private static final long FRAME_GAP = 100_000_000L;
    private static final long RESPONSE_WINDOW = 800_000_000L;
    private static final long FOLLOW_UP_WINDOW = 750_000_000L;
    private final Map<String, ExternalFinding> findings = new LinkedHashMap<>();
    private final List<Double> responses = new ArrayList<>();
    private final List<Episode> completed = new ArrayList<>();
    private final List<Episode> settling = new ArrayList<>();
    private int opportunities;
    private int sameFrameResponses;
    private int swordControls;
    private long lastControl;
    private long lastFrame;
    private int target = -1;
    private boolean blocking;
    private int held;
    private long unblockedSince;
    private long raisedAt;
    private long lastLowering;
    private int lastLoweringTarget = -1;
    private Episode current;

    private static final class Episode {
        int target;
        boolean blockingAtSwap;
        boolean blockingAttack;
        boolean failedOrOther;
        boolean stoppedBlocking;
        boolean previousBlocking;
        long started;
        long lastAttack;
        long ended;
        long lastSeen;
    }

    public void reset() {
        findings.clear();
        pause();
    }

    public void pause() {
        responses.clear(); completed.clear(); settling.clear();
        opportunities = sameFrameResponses = swordControls = 0;
        lastControl = lastFrame = unblockedSince = raisedAt = 0;
        lastLowering = 0;
        lastLoweringTarget = -1;
        target = -1;
        blocking = false;
        held = 0;
        current = null;
    }

    public void frame(long nano, long epoch, int opponent, boolean shieldBlocking, int heldType) {
        boolean continuous = lastFrame != 0 && nano > lastFrame && nano - lastFrame <= FRAME_GAP;
        if (!continuous) {
            raisedAt = unblockedSince = 0;
            current = null;
            settling.clear();
        }
        boolean sameTarget = continuous && opponent >= 0 && opponent == target;
        boolean axeSwap = continuous && held == 1 && heldType == 2 && opponent >= 0;
        if (raisedAt != 0 && (!sameTarget || !shieldBlocking || nano - raisedAt > RESPONSE_WINDOW)) {
            if (sameTarget) finishResponse(Double.NaN, epoch);
            else raisedAt = 0;
        }
        if (sameTarget && !blocking && shieldBlocking && held == 1
                && unblockedSince != 0 && nano - unblockedSince >= 250_000_000L) {
            raisedAt = nano;
        }
        if (axeSwap && raisedAt != 0 && sameTarget && shieldBlocking) {
            double delay = (nano - raisedAt) / 1_000_000.0;
            if (delay == 0) sameFrameResponses++;
            finishResponse(delay, epoch);
        }
        if (current != null) {
            observe(current, nano, opponent, shieldBlocking);
            if (heldType != 2 || nano - current.started > 2_000_000_000L) {
                current.ended = nano;
                settling.add(current);
                current = null;
            }
        }
        if (axeSwap) {
            current = new Episode();
            current.target = opponent;
            current.blockingAtSwap = shieldBlocking;
            current.previousBlocking = shieldBlocking;
            current.started = current.lastSeen = nano;
        }
        for (int index = settling.size() - 1; index >= 0; index--) {
            Episode episode = settling.get(index);
            observe(episode, nano, opponent, shieldBlocking);
            long reference = episode.lastAttack == 0 ? episode.ended : episode.lastAttack;
            if (nano - reference > FOLLOW_UP_WINDOW) {
                completed.add(episode);
                settling.remove(index);
            }
        }
        while (settling.size() > 32) settling.removeFirst();
        evaluateSelectivity(epoch);
        if (!sameTarget || shieldBlocking) unblockedSince = 0;
        if (opponent >= 0 && !shieldBlocking && unblockedSince == 0) unblockedSince = nano;
        target = opponent;
        blocking = shieldBlocking;
        held = heldType;
        lastFrame = nano;
    }

    public void attack(long nano, long epoch, int opponent, boolean shieldBlocking, int heldType, boolean charged) {
        if (heldType == 1 && opponent >= 0 && !shieldBlocking && charged
                && (lastControl == 0 || nano - lastControl >= 250_000_000L)) {
            swordControls = Math.min(10_000, swordControls + 1);
            lastControl = nano;
        }
        if (heldType == 2 && current != null) {
            current.lastAttack = nano;
            if (opponent == current.target && shieldBlocking && charged) current.blockingAttack = true;
            else current.failedOrOther = true;
        }
        evaluateSelectivity(epoch);
    }

    public void miss() {
        if (current != null) current.failedOrOther = true;
    }

    private void observe(Episode episode, long nano, int opponent, boolean shieldBlocking) {
        if (opponent != episode.target) return;
        if (episode.blockingAttack && episode.previousBlocking && !shieldBlocking
                && nano > episode.lastAttack && nano - episode.lastAttack <= FOLLOW_UP_WINDOW
                && nano - episode.lastSeen <= FRAME_GAP
                && !(lastLowering == nano && lastLoweringTarget == opponent)) {
            episode.stoppedBlocking = true;
            lastLowering = nano;
            lastLoweringTarget = opponent;
        }
        episode.previousBlocking = shieldBlocking;
        episode.lastSeen = nano;
    }

    private void finishResponse(double delay, long epoch) {
        opportunities++;
        if (Double.isFinite(delay)) responses.add(delay);
        raisedAt = 0;
        if (opportunities >= 24) {
            double mean = mean(responses);
            double sd = sd(responses, mean);
            if (responses.size() >= 22 && mean <= 150 && sd <= 30) {
                record("shield-response", "Axe swaps aligned with shield raises", opportunities, 25,
                        String.format(Locale.ROOT, "%d of %d independent blocking starts were followed by an axe swap. Observed delay mean %.1f ms, SD %.1f ms; %d swaps were first seen in the same frame. Only actively blocking opponents count. Frame and network timing limit these observations; they do not prove an automatic response.", responses.size(), opportunities, mean, sd, sameFrameResponses), epoch);
            }
            opportunities = sameFrameResponses = 0;
            responses.clear();
        }
    }

    private void evaluateSelectivity(long epoch) {
        if (completed.size() < 24) return;
        while (completed.size() > 24) completed.removeFirst();
        if (swordControls < 24) return;
        int blockingSwaps = 0, cleanAttempts = 0, stopped = 0;
        for (Episode episode : completed) {
            if (episode.blockingAtSwap) blockingSwaps++;
            if (episode.blockingAttack && !episode.failedOrOther) cleanAttempts++;
            if (episode.blockingAttack && !episode.failedOrOther && episode.stoppedBlocking) stopped++;
        }
        if (blockingSwaps >= 22 && cleanAttempts >= 22 && stopped >= 22) {
            record("shield-specific", "Repeated shield specific axe attacks", completed.size(), 25,
                    String.format(Locale.ROOT, "%d of %d axe equips occurred against actively blocking opponents; %d episodes had a charged shield attack without a recorded miss or other attack. Blocking stopped within 750 ms after %d of those episodes. %d charged sword attacks against nonblocking opponents provided comparison data. A shield lowering is not server confirmation of a shield break and does not establish that this player caused it.", blockingSwaps, completed.size(), cleanAttempts, stopped, swordControls), epoch);
        }
        completed.clear();
        swordControls = 0;
    }

    private void record(String id, String label, int samples, int score, String detail, long epoch) {
        ExternalFinding previous = findings.get(id);
        findings.put(id, new ExternalFinding(id, label, "BEHAVIORAL", previous == null ? 1 : previous.count() + 1,
                samples, score, detail, previous == null ? epoch : previous.firstEpochMs(), epoch));
    }

    public List<ExternalFinding> findings() {
        return List.copyOf(findings.values());
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.POSITIVE_INFINITY);
    }

    private static double sd(List<Double> values, double mean) {
        double sum = 0;
        for (double value : values) sum += (value - mean) * (value - mean);
        return Math.sqrt(sum / Math.max(1, values.size()));
    }
}
