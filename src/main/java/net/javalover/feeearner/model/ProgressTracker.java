package net.javalover.feeearner.model;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ProgressTracker {
    private final AtomicInteger total;
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final CopyOnWriteArrayList<FailedEntry> failures = new CopyOnWriteArrayList<>();

    public ProgressTracker(int total) {
        this.total = new AtomicInteger(total);
    }

    public AtomicInteger total() { return total; }
    public AtomicInteger completed() { return completed; }
    public AtomicInteger failed() { return failed; }
    public CopyOnWriteArrayList<FailedEntry> failures() { return failures; }
}
