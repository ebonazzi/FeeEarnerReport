package net.javalover.feeearner.repository;

import net.javalover.feeearner.TestDataSourceFactory;
import net.javalover.feeearner.model.AppParam;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ParamRepositoryIT {

    private final ParamRepository repo =
        new ParamRepository(TestDataSourceFactory.create());

    @Test
    void loadsAllActiveParams() {
        var params = repo.loadAll();
        assertFalse(params.isEmpty(), "report.report_param must have at least one active row");
    }

    @Test
    void allParamsHaveNonBlankName() {
        repo.loadAll().forEach(p ->
            assertFalse(p.name().isBlank(), "param name should not be blank"));
    }

    @Test
    void saveThrowsForNonExistentId() {
        var ghost = new net.javalover.feeearner.model.AppParam(
            Integer.MAX_VALUE, "ghost", "value", true);
        assertThrows(RuntimeException.class, () -> repo.save(ghost));
    }
}
