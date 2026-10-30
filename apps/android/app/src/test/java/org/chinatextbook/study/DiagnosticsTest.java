package org.chinatextbook.study;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticsTest {
    @Test public void trailKeepsOnlyTheLatestStepsInOrder() {
        DiagnosticTrail trail = new DiagnosticTrail(3);
        for (int i = 1; i <= 5; i++) trail.mark(i * 1000L, "step" + i);
        List<String> steps = trail.snapshot();
        assertEquals(3, steps.size());
        assertTrue(steps.get(0).endsWith("step3"));
        assertTrue(steps.get(2).endsWith("step5"));
    }

    @Test public void trailTruncatesLongStepsAndIgnoresNull() {
        DiagnosticTrail trail = new DiagnosticTrail(2);
        trail.mark(0, null);
        StringBuilder longStep = new StringBuilder();
        for (int i = 0; i < 300; i++) longStep.append('x');
        trail.mark(0, longStep.toString());
        List<String> steps = trail.snapshot();
        assertEquals(1, steps.size());
        assertTrue(steps.get(0).length() < 140);
    }

    @Test public void watchdogFlagsOnlyAnUnansweredPingPastTheTimeout() {
        HangWatchdog watchdog = new HangWatchdog(8000);
        assertTrue(watchdog.shouldPing());
        assertFalse(watchdog.isHung(0));
        watchdog.pinged(1000);
        assertFalse(watchdog.shouldPing());
        assertFalse(watchdog.isHung(8999));
        assertTrue(watchdog.isHung(9000));
        watchdog.ponged();
        assertFalse(watchdog.isHung(20000));
        assertTrue(watchdog.shouldPing());
    }

    @Test public void watchdogResetForgetsAnOutstandingPing() {
        HangWatchdog watchdog = new HangWatchdog(8000);
        watchdog.pinged(0);
        watchdog.reset();
        assertFalse(watchdog.isHung(100000));
        assertTrue(watchdog.shouldPing());
    }
}
