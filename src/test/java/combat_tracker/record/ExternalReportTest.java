package combat_tracker.record;

import combat_tracker.detection.ExternalFinding;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ExternalReportTest {
    @Test public void reportClearlySeparatesSuspicionFromObservedInputAndEscapesMetadata() {
        SessionData session = new SessionData();
        session.flagAttack = 1;
        session.mods.add(new SessionData.ModEntry("<Long mod name>", "1.0", List.of("Author <One>")));
        session.externalFindings.add(new ExternalFinding("test", "Possible <macro>", "BEHAVIORAL", 1,
                192, 25, "Experimental <measurement>", 1000, 2000));
        session.externalFindings.add(new ExternalFinding("windows-0", "Windows mouse", "OBSERVED_INPUT", 3,
                3, 0, "Origin unknown", 1000, 2000));
        String html = ReportBuilder.build(session);
        assertTrue(html.contains("Behavioral Evidence. Not definitive proof of cheating"));
        assertTrue(html.contains("Observed Windows software generated input. not proof of cheating"));
        assertTrue(html.contains("Automated Attack"));
        assertTrue(html.contains("Possible &lt;macro&gt;"));
        assertTrue(html.contains("Author &lt;One&gt;"));
        assertFalse(html.contains("<h2>Jump resets"));
        assertFalse(html.contains("<h2>Combo timing"));
        assertFalse(html.contains("Synthetic Input"));
    }
}
