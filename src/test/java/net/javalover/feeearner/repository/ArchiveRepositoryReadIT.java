package net.javalover.feeearner.repository;

import net.javalover.feeearner.TestDataSourceFactory;
import net.javalover.feeearner.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ArchiveRepositoryReadIT {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 11);
    private static final int RUN = 999_999;       // a test run_id unlikely to collide
    private static final int USR = 999_991;

    private FullTaskRow fullTask() {
        return new FullTaskRow("Matter", DAY, "M-1", "Test matter", "Litigation",
            "PC", "Melbourne", "VIC", "Joseph Tran", "LA", "Sup",
            "Do the thing", "Type", "Notes", "Owner",
            LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 17, 0));
    }

    @Test
    void writeThenReadRoundTripsFullTaskRows() throws Exception {
        var ds = TestDataSourceFactory.create();
        var repo = new ArchiveRepository(ds);
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // replaceExisting=true clears any prior test rows first
                repo.writeForFeeEarner(conn, RUN, DAY, USR, true,
                    List.of(fullTask()), List.of(), List.of(), List.of(), List.of());
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }

        var rows = repo.readFullTask(RUN, USR);
        assertEquals(1, rows.size());
        assertEquals("Joseph Tran", rows.get(0).feeEarner());
        assertEquals("Matter", rows.get(0).type());

        var ids = repo.getFeeEarnerIdsForRun(RUN);
        assertTrue(ids.contains(USR));

        // cleanup
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            repo.writeForFeeEarner(conn, RUN, DAY, USR, true,
                List.of(), List.of(), List.of(), List.of(), List.of());
            conn.commit();
        }
    }
}
