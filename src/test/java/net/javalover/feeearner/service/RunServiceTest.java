package net.javalover.feeearner.service;

import net.javalover.feeearner.repository.RunRepository;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RunServiceTest {

    @Test
    void startRunReturnsRunIdFromRepository() {
        var repo = new RunRepository(null) {
            @Override public int createRun() { return 42; }
        };
        var service = new RunService(repo);
        assertEquals(42, service.startRun());
    }

    @Test
    void finishRunDelegatesToRepository() {
        var closedId = new AtomicInteger(-1);
        var repo = new RunRepository(null) {
            @Override public int createRun() { return 0; }
            @Override public void closeRun(int runId) { closedId.set(runId); }
        };
        var service = new RunService(repo);
        service.finishRun(7);
        assertEquals(7, closedId.get());
    }
}
