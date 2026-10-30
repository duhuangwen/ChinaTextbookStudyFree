package org.chinatextbook.study;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Last page steps reported over the JS bridge, kept natively so they survive a hung renderer. */
final class DiagnosticTrail {
    private static final int MAX_STEP_LENGTH = 120;
    private final int capacity;
    private final ArrayDeque<String> steps = new ArrayDeque<>();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    DiagnosticTrail(int capacity) {
        this.capacity = capacity;
    }

    synchronized void mark(long timeMillis, String step) {
        if (step == null) return;
        String trimmed = step.length() > MAX_STEP_LENGTH ? step.substring(0, MAX_STEP_LENGTH) : step;
        if (steps.size() == capacity) steps.removeFirst();
        steps.addLast(clock.format(new Date(timeMillis)) + " " + trimmed);
    }

    synchronized List<String> snapshot() {
        return new ArrayList<>(steps);
    }
}
