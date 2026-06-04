package net.javalover.feeearner.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressEventTest {

    @Test
    void holdsCompletedTotalFailed() {
        var event = new ProgressEvent(3, 10, 1);
        assertEquals(3, event.completed());
        assertEquals(10, event.total());
        assertEquals(1, event.failed());
    }
}
