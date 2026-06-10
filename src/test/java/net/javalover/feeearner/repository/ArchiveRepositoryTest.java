package net.javalover.feeearner.repository;

import net.javalover.feeearner.FakeJdbc;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ArchiveRepository#writeForFeeEarner} is a transaction <em>participant</em>: it
 * runs its statements on the supplied connection and must NOT commit, roll back, or close
 * it — that is the caller's (SpreadsheetService's) job. Verified with JDBC proxy fakes.
 */
class ArchiveRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 10);

    @Test
    void replaceExistingDeletesAllFiveTablesBeforeReturning() throws SQLException {
        var fake = new FakeJdbc();
        new ArchiveRepository().writeForFeeEarner(fake.connection(), 1, DAY, 42, true,
            List.of(), List.of(), List.of(), List.of(), List.of());

        // 5 DELETEs issued (one per archive table); inserts skipped for empty row lists
        assertEquals(5, fake.prepared.stream().filter(s -> s.startsWith("DELETE")).count());
        assertEquals(5, fake.updateCount, "one executeUpdate per table delete");
    }

    @Test
    void doesNotManageTheTransaction() throws SQLException {
        var fake = new FakeJdbc();
        new ArchiveRepository().writeForFeeEarner(fake.connection(), 1, DAY, 42, false,
            List.of(), List.of(), List.of(), List.of(), List.of());

        assertFalse(fake.committed, "participant must not commit");
        assertFalse(fake.rolledBack, "participant must not roll back");
        assertFalse(fake.closed, "participant must not close the connection");
    }

    @Test
    void propagatesSqlExceptionToCaller() {
        var fake = new FakeJdbc();
        fake.failOnUpdate = 2;   // 2nd DELETE fails
        var repo = new ArchiveRepository();
        assertThrows(SQLException.class, () ->
            repo.writeForFeeEarner(fake.connection(), 1, DAY, 42, true,
                List.of(), List.of(), List.of(), List.of(), List.of()));
        assertFalse(fake.rolledBack, "rollback is the caller's responsibility, not the participant's");
    }
}
