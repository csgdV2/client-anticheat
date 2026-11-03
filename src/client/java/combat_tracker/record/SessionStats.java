package combat_tracker.record;

final class SessionStats {
    private SessionStats() {
    }

    static void summarise(SessionData d) {
        summariseSwings(d);
        summariseShieldBreaks(d);
    }

    private static void summariseSwings(SessionData d) {
        int hits = 0;
        double rSum = 0, rSumSq = 0, rMax = 0;
        double aSum = 0, aSumSq = 0;
        for (SessionData.SEvent e : d.swingEvents) {
            if (e.hit) {
                hits++;
            }
            rSum += e.reach;
            rSumSq += e.reach * e.reach;
            rMax = Math.max(rMax, e.reach);
            aSum += e.aimDeg;
            aSumSq += e.aimDeg * e.aimDeg;
        }
        int n = d.swingEvents.size();
        d.swings = n;
        d.swingHits = hits;
        d.swingMisses = n - hits;
        d.reachAvgBlocks = n == 0 ? 0 : rSum / n;
        d.reachMaxBlocks = rMax;
        d.reachSdBlocks = stdDev(rSum, rSumSq, n);
        d.aimAvgDeg = n == 0 ? 0 : aSum / n;
        d.aimSdDeg = stdDev(aSum, aSumSq, n);
    }

    private static void summariseShieldBreaks(SessionData d) {
        int attempts = d.shieldBreaks + d.shieldMisses;
        d.shieldBreakRate = attempts == 0 ? 0.0 : (100.0 * d.shieldBreaks / attempts);
    }

    private static double stdDev(double sum, double sumSq, int n) {
        if (n == 0) {
            return 0.0;
        }
        double mean = sum / n;
        return Math.sqrt(Math.max(0.0, (sumSq / n) - (mean * mean)));
    }
}
