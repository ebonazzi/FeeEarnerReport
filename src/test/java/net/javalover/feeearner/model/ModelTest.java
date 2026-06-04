package net.javalover.feeearner.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void feeEarnerRecord() {
        var fe = new FeeEarner(1, "Alice Smith", "alice@example.com", true, "Enquiry");
        assertEquals(1, fe.usrID());
        assertEquals("Alice Smith", fe.feeEarner());
        assertTrue(fe.usrActive());
    }

    @Test
    void runInfoRecord() {
        var now = LocalDateTime.now();
        var ri = new RunInfo(42, LocalDate.now(), now, now.plusMinutes(10));
        assertEquals(42, ri.runId());
        assertNotNull(ri.startedAt());
    }

    @Test
    void feeEarnerRunRecord() {
        var fer = new FeeEarnerRun(1, LocalDate.now(), 100, "Bob", "bob@example.com",
                "fe_100_20260604_1.xlsx", new byte[]{1, 2, 3}, LocalDateTime.now());
        assertEquals(100, fer.usrID());
        assertEquals(3, fer.excelSpreadsheet().length);
    }

    @Test
    void baseRowSealedHierarchy() {
        var now = LocalDateTime.now();
        BaseRow row = new FullTaskRow("Matter", LocalDate.now(), "M001", "Desc",
                "Dept", "PC001", "Sydney", "VIC", "Alice", "Bob", "Carol",
                "Task desc", "Type", "Notes", "Owner", now, now);
        assertInstanceOf(FullTaskRow.class, row);
    }

    @Test
    void progressTracker() {
        var tracker = new ProgressTracker(10);
        assertEquals(10, tracker.total().get());
        assertEquals(0, tracker.completed().get());
        tracker.completed().incrementAndGet();
        assertEquals(1, tracker.completed().get());
    }
}
