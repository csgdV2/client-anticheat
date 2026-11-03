package combat_tracker.detection;

import org.junit.Test;
import static org.junit.Assert.*;

public class BehaviorEngineTest {
    private void acquisitions(BehaviorEngine engine, boolean stalled, boolean clickBeforeAcquisition, boolean charged) {
        long time = 1_000_000_000L;
        engine.frame(time, 1000, 7, -1, Double.NaN, 0);
        for (int attempt = 0; attempt < 40; attempt++) {
            int spacing = stalled ? 100 : 10;
            for (int index = 0; index < 40; index++) {
                time += spacing * 1_000_000L;
                engine.frame(time, time / 1_000_000, -1, -1, Double.NaN, 0);
            }
            time += spacing * 1_000_000L;
            engine.frame(time, time / 1_000_000, 7, -1, Double.NaN, 0);
            long click = clickBeforeAcquisition ? time - 1 : time + 20_000_000L;
            engine.attack(time + 20_000_000L, time / 1_000_000, 7, click, charged);
            time += 20_000_000L;
        }
    }

    @Test public void targetTimingNeedsIndependentAcquisitionsAndFreshClick() {
        BehaviorEngine valid = new BehaviorEngine();
        acquisitions(valid, false, false, true);
        assertEquals("target-timing", valid.findings().getFirst().id());
        BehaviorEngine held = new BehaviorEngine();
        acquisitions(held, false, true, true);
        assertTrue(held.findings().isEmpty());
        BehaviorEngine cooldown = new BehaviorEngine();
        acquisitions(cooldown, false, false, false);
        assertTrue(cooldown.findings().isEmpty());
    }

    @Test public void frameStallsInvalidateReactionEvidence() {
        BehaviorEngine engine = new BehaviorEngine();
        acquisitions(engine, true, false, true);
        assertTrue(engine.findings().isEmpty());
    }

    @Test public void shieldSequencesRequireLargeSampleAndConsistency() {
        BehaviorEngine engine = new BehaviorEngine();
        for (int index = 0; index < 23; index++) engine.shieldSwap(100, 1000 + index);
        assertTrue(engine.findings().isEmpty());
        engine.shieldSwap(100, 1024);
        assertEquals("shield-sequence", engine.findings().getFirst().id());
        BehaviorEngine varied = new BehaviorEngine();
        for (int index = 0; index < 100; index++) varied.shieldSwap(index % 2 == 0 ? 80 : 300, index);
        assertTrue(varied.findings().isEmpty());
    }

    @Test public void aimEvidenceNeedsRepeatedCorrectionsToSameCandidate() {
        BehaviorEngine engine = new BehaviorEngine();
        for (int index = 0; index <= 600; index++) {
            int phase = index % 20;
            engine.frame(1_000_000_000L + index * 10_000_000L, 1000 + index * 10,
                    -1, 7, phase == 0 ? 20 : 0.01, phase == 1 ? 20 : 0);
        }
        assertEquals("aim", engine.findings().getFirst().id());
        BehaviorEngine switched = new BehaviorEngine();
        for (int index = 0; index <= 600; index++) {
            switched.frame(1_000_000_000L + index * 10_000_000L, index, -1, index,
                    index % 2 == 0 ? 20 : 0.01, 20);
        }
        assertTrue(switched.findings().isEmpty());
    }

    @Test public void scoreDecaysButHistoryRemainsAndNewSessionResetsIt() {
        BehaviorEngine engine = new BehaviorEngine();
        for (int index = 0; index < 24; index++) engine.shieldSwap(100, 1000 + index);
        long last = engine.findings().getFirst().lastEpochMs();
        assertEquals(20, engine.currentScore(last));
        assertTrue(engine.currentScore(last + 60_000) <= 10);
        assertEquals(1, engine.findings().size());
        engine.reset();
        assertTrue(engine.findings().isEmpty());
        assertEquals(0, engine.currentScore(last));
    }

    @Test public void targetChangesCannotAccumulateAimEvidenceAcrossDifferentPlayers() {
        BehaviorEngine engine = new BehaviorEngine();
        for (int index = 0; index < 1200; index++) {
            int phase = index % 20;
            engine.frame(1_000_000_000L + index * 10_000_000L, 1000 + index * 10,
                    -1, index / 300, phase == 0 ? 20 : 0.01, phase == 1 ? 20 : 0);
        }
        assertTrue(engine.findings().isEmpty());
    }

    @Test public void windowsMouseClassificationExcludesReleaseAndUnknownEvents() {
        assertEquals(WindowsInputMonitor.MOUSE, WindowsInputMonitor.mouseKind(0x201));
        assertEquals(WindowsInputMonitor.MOVEMENT, WindowsInputMonitor.mouseKind(0x200));
        assertEquals(WindowsInputMonitor.WHEEL, WindowsInputMonitor.mouseKind(0x20a));
        assertEquals(-1, WindowsInputMonitor.mouseKind(0x202));
        assertEquals(-1, WindowsInputMonitor.mouseKind(123));
    }
}
