package net.javalover.feeearner.repository;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the transactional behaviour of {@link ArchiveRepository#writeForFeeEarner}
 * without a real database, using dynamic-proxy fakes for DataSource/Connection/Statement.
 */
class ArchiveRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 10);

    /** A mid-write SQL failure must roll back the whole transaction and never commit. */
    @Test
    void writeForFeeEarnerRollsBackOnFailure() {
        var s = new TxState();
        s.failOnUpdate = 2;   // fail on the 2nd DELETE statement
        var repo = new ArchiveRepository(fakeDataSource(s));

        assertThrows(RuntimeException.class, () ->
            repo.writeForFeeEarner(1, DAY, 42, true,
                List.of(), List.of(), List.of(), List.of(), List.of()));

        assertTrue(s.rolledBack, "transaction must be rolled back on failure");
        assertFalse(s.committed, "transaction must NOT be committed on failure");
        assertTrue(s.closed, "connection must be closed");
    }

    /** A clean write commits exactly once and never rolls back. */
    @Test
    void writeForFeeEarnerCommitsOnSuccess() {
        var s = new TxState();   // no simulated failure
        var repo = new ArchiveRepository(fakeDataSource(s));

        repo.writeForFeeEarner(1, DAY, 42, true,
            List.of(), List.of(), List.of(), List.of(), List.of());

        assertTrue(s.committed, "clean write must commit");
        assertFalse(s.rolledBack, "clean write must not roll back");
        assertTrue(s.closed, "connection must be closed");
    }

    // ── fake JDBC via dynamic proxies ───────────────────────────────────────────

    private static final class TxState {
        boolean committed, rolledBack, closed;
        int updateCount;        // executeUpdate calls so far
        int failOnUpdate = -1;  // throw on this executeUpdate (1-based); -1 = never
    }

    private DataSource fakeDataSource(TxState s) {
        return (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{DataSource.class},
            (p, m, args) -> m.getName().equals("getConnection")
                ? fakeConnection(s) : defaultValue(m.getReturnType()));
    }

    private Connection fakeConnection(TxState s) {
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{Connection.class},
            (p, m, args) -> switch (m.getName()) {
                case "commit"           -> { s.committed = true; yield null; }
                case "rollback"         -> { s.rolledBack = true; yield null; }
                case "close"            -> { s.closed = true; yield null; }
                case "prepareStatement" -> fakeStatement(s);
                default                 -> defaultValue(m.getReturnType());
            });
    }

    private PreparedStatement fakeStatement(TxState s) {
        return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
            (p, m, args) -> {
                if (m.getName().equals("executeUpdate")) {
                    s.updateCount++;
                    if (s.updateCount == s.failOnUpdate)
                        throw new SQLException("simulated failure on update " + s.updateCount);
                    return 0;
                }
                if (m.getName().equals("executeBatch")) return new int[0];
                return defaultValue(m.getReturnType());
            });
    }

    private static Object defaultValue(Class<?> t) {
        if (!t.isPrimitive() || t == void.class) return null;
        if (t == boolean.class) return false;
        if (t == long.class)    return 0L;
        if (t == double.class)  return 0d;
        if (t == float.class)   return 0f;
        if (t == char.class)    return '\0';
        return 0;   // int, short, byte
    }
}
