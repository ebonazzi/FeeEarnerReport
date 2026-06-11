package net.javalover.feeearner.repository;

import net.javalover.feeearner.FakeJdbc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunRepositoryTest {

    @Test
    void updateMostRecentIssuesUpdateAndClosesItsOwnConnection() {
        var fake = new FakeJdbc();
        var repo = new RunRepository(fake.dataSource());

        // FakeJdbc.executeUpdate returns 0 rows, which trips the method's "0 rows updated"
        // guard. We catch it here: the SQL was still prepared and executed, which is what
        // this test asserts (SQL shape + self-managed connection).
        try {
            repo.updateMostRecentFeeEarnerBlob(42, "fe_42_20260611_7.xlsx", new byte[]{9, 9});
        } catch (IllegalStateException expectedFromFakeZeroRows) {
            // expected against the fake
        }

        assertEquals(1, fake.prepared.size());
        assertTrue(fake.prepared.get(0).startsWith("UPDATE report.FeeEarnersRun"));
        assertTrue(fake.prepared.get(0).contains("run_id = (SELECT TOP 1 run_id"),
            "must target the most recent run for the usrID via a subquery");
        assertEquals(1, fake.updateCount, "exactly one executeUpdate");
        assertTrue(fake.closed, "self-managed connection must be closed");
    }
}
