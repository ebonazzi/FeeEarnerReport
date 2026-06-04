package net.javalover.feeearner.repository;

import net.javalover.feeearner.TestDataSourceFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class FeeEarnerRepositoryIT {

    private final FeeEarnerRepository repo =
        new FeeEarnerRepository(TestDataSourceFactory.create());

    @Test
    void fetchesLeadFeeEarners() {
        var list = repo.getLeadFeeEarners();
        assertFalse(list.isEmpty(), "Expected at least one active lead fee earner");
        list.forEach(fe -> {
            assertTrue(fe.usrID() > 0);
            assertFalse(fe.feeEarner().isBlank());
            assertEquals("Enquiry", fe.type());
        });
    }

    @Test
    void fetchesMatterFeeEarners() {
        var list = repo.getMatterFeeEarners();
        assertFalse(list.isEmpty(), "Expected at least one active matter fee earner");
        list.forEach(fe -> assertEquals("Matter", fe.type()));
    }

    @Test
    void intersectContainsOnlyValidIds() {
        var leads  = repo.getLeadFeeEarners();
        var matters = repo.getMatterFeeEarners();
        var intersect = repo.getIntersectUserIds();

        var leadIds   = leads.stream().map(f -> f.usrID()).collect(java.util.stream.Collectors.toSet());
        var matterIds = matters.stream().map(f -> f.usrID()).collect(java.util.stream.Collectors.toSet());

        intersect.forEach(id -> {
            assertTrue(leadIds.contains(id), id + " must be in lead fee earners");
            assertTrue(matterIds.contains(id), id + " must be in matter fee earners");
        });
    }
}
