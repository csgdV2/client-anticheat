package combat_tracker.detection;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShieldBehaviorTest {
    private static final class Play {
        final ShieldBehavior check = new ShieldBehavior();
        long time = 1_000_000_000L;
        int target = 7;
        boolean blocking;
        int held = 1;

        Play() { check.frame(time, time / 1_000_000, target, blocking, held); }
        void step(int ms) {
            time += ms * 1_000_000L;
            check.frame(time, time / 1_000_000, target, blocking, held);
        }
        void waitFor(int ms) { for (int index = 0; index < ms / 10; index++) step(10); }
        void controls() {
            for (int index = 0; index < 24; index++) {
                waitFor(300);
                check.attack(time, time / 1_000_000, target, false, 1, true);
            }
        }
        void response(int delay, boolean swap) {
            blocking = false;
            held = 1;
            step(10);
            waitFor(300);
            blocking = true;
            step(10);
            if (swap) {
                waitFor(delay);
                held = 2;
                step(10);
            } else waitFor(850);
            blocking = false;
            held = 1;
            step(10);
            waitFor(800);
        }
        void episode(int index, boolean atShield, boolean miss, boolean lowered, boolean charged) {
            target = 100 + index;
            blocking = atShield;
            held = 1;
            step(10);
            held = 2;
            step(10);
            check.attack(time, time / 1_000_000, target, atShield, 2, charged);
            if (miss) check.miss();
            if (lowered) { blocking = false; step(20); }
            held = 1;
            step(10);
            waitFor(800);
        }
        boolean found(String id) { return check.findings().stream().anyMatch(finding -> finding.id().equals(id)); }
    }

    @Test public void responseNeedsIndependentShieldRaisesAndEnoughObservations() {
        Play play = new Play();
        for (int index = 0; index < 23; index++) play.response(40, true);
        assertFalse(play.found("shield-response"));
        play.response(40, true);
        assertTrue(play.found("shield-response"));
        assertTrue(play.check.findings().getFirst().detail().contains("24 of 24"));
    }

    @Test public void noSwapsAndVariableDelaysDoNotImplyAutomaticResponses() {
        Play noSwap = new Play();
        Play varied = new Play();
        for (int index = 0; index < 24; index++) {
            noSwap.response(0, false);
            varied.response(index % 2 == 0 ? 30 : 350, true);
        }
        assertFalse(noSwap.found("shield-response"));
        assertFalse(varied.found("shield-response"));
    }

    @Test public void seeingAnAlreadyRaisedShieldOrStalledFramesDoesNotCountAsAReaction() {
        Play play = new Play();
        for (int index = 0; index < 40; index++) {
            play.target = index;
            play.blocking = true;
            play.held = 2;
            play.step(200);
            play.held = 1;
            play.step(10);
        }
        assertFalse(play.found("shield-response"));
    }

    @Test public void simultaneousTransitionsAreReportedAsFrameAmbiguous() {
        Play play = new Play();
        for (int index = 0; index < 24; index++) {
            play.blocking = false; play.held = 1; play.step(10); play.waitFor(300);
            play.blocking = true; play.held = 2; play.step(10);
            play.blocking = false; play.held = 1; play.step(10); play.waitFor(800);
        }
        assertTrue(play.found("shield-response"));
        assertTrue(play.check.findings().getFirst().detail().contains("24 swaps were first seen in the same frame"));
    }

    @Test public void selectiveAxeUseNeedsBlockingAndNonblockingComparisonData() {
        Play play = new Play();
        play.controls();
        for (int index = 0; index < 24; index++) play.episode(index, true, false, true, true);
        assertTrue(play.found("shield-specific"));
        ExternalFinding finding = play.check.findings().stream().filter(f -> f.id().equals("shield-specific")).findFirst().orElseThrow();
        assertTrue(finding.detail().contains("not server confirmation"));
        Play noComparison = new Play();
        for (int index = 0; index < 24; index++) noComparison.episode(index, true, false, true, true);
        assertFalse(noComparison.found("shield-specific"));
    }

    @Test public void missesOtherAxeUsesAndUnchargedAttacksPreventNearPerfectPattern() {
        Play missed = new Play(); missed.controls();
        Play other = new Play(); other.controls();
        Play weak = new Play(); weak.controls();
        for (int index = 0; index < 24; index++) {
            missed.episode(index, true, index < 6, true, true);
            other.episode(index, index >= 6, false, true, true);
            weak.episode(index, true, false, true, false);
        }
        assertFalse(missed.found("shield-specific"));
        assertFalse(other.found("shield-specific"));
        assertFalse(weak.found("shield-specific"));
    }

    @Test public void equippedButUnraisedShieldsAndUnchangedBlockingDoNotCountAsBreaks() {
        Play equipped = new Play(); equipped.controls();
        Play noLowering = new Play(); noLowering.controls();
        for (int index = 0; index < 24; index++) {
            equipped.episode(index, false, false, false, true);
            noLowering.episode(index, true, false, false, true);
        }
        assertFalse(equipped.found("shield-specific"));
        assertFalse(noLowering.found("shield-specific"));
    }

    @Test public void alwaysHoldingAnAxeDoesNotCreateRepeatedSwapEpisodes() {
        Play play = new Play(); play.controls();
        play.check.pause();
        play.held = 2; play.blocking = true; play.step(10);
        for (int index = 0; index < 50; index++) {
            play.waitFor(300);
            play.check.attack(play.time, play.time / 1_000_000, play.target, true, 2, true);
        }
        assertFalse(play.found("shield-specific"));
    }

    @Test public void oneShieldLoweringCannotCountAsManySuccessfulAxeEpisodes() {
        Play play = new Play(); play.controls();
        play.blocking = true;
        play.step(10);
        for (int index = 0; index < 24; index++) {
            play.held = 2; play.step(10);
            play.check.attack(play.time, play.time / 1_000_000, play.target, true, 2, true);
            play.held = 1; play.step(10);
        }
        play.blocking = false; play.step(10);
        play.waitFor(850);
        assertFalse(play.found("shield-specific"));
    }

    @Test public void pauseDropsPendingSamplesAndResetClearsRecordedFindings() {
        Play play = new Play();
        for (int index = 0; index < 23; index++) play.response(40, true);
        play.check.pause();
        play.response(40, true);
        assertFalse(play.found("shield-response"));
        for (int index = 0; index < 23; index++) play.response(40, true);
        assertTrue(play.found("shield-response"));
        play.check.reset();
        assertTrue(play.check.findings().isEmpty());
    }
}

