package net.javalover.feeearner.sharepoint;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SharePointServiceTest {

    @Test
    void tokenUrlUsesTenant() {
        assertEquals("https://login.microsoftonline.com/tenant-1/oauth2/v2.0/token",
            SharePointService.tokenUrl("tenant-1"));
    }

    @Test
    void siteUrlForRootIsHostOnly() {
        assertEquals("https://graph.microsoft.com/v1.0/sites/h.sharepoint.com",
            SharePointService.siteUrl("h.sharepoint.com", "root"));
    }

    @Test
    void siteUrlForNamedSite() {
        assertEquals("https://graph.microsoft.com/v1.0/sites/h.sharepoint.com:/sites/Legal",
            SharePointService.siteUrl("h.sharepoint.com", "Legal"));
    }

    @Test
    void contentUrlEncodesSpacesInFolderAndFile() {
        var url = SharePointService.simpleUploadUrl("drv1",
            "Shared Documents/VIC reports", "Fee Earner_1_Joseph Tran_VIC_task_report.xlsx");
        assertEquals("https://graph.microsoft.com/v1.0/drives/drv1/root:/"
            + "Shared%20Documents/VIC%20reports/"
            + "Fee%20Earner_1_Joseph%20Tran_VIC_task_report.xlsx:/content", url);
    }

    @Test
    void createSessionUrlEndsWithCreateUploadSession() {
        var url = SharePointService.createUploadSessionUrl("drv1", "Folder", "f.xlsx");
        assertEquals("https://graph.microsoft.com/v1.0/drives/drv1/root:/"
            + "Folder/f.xlsx:/createUploadSession", url);
    }

    @Test
    void contentRangeHeaderFormat() {
        assertEquals("bytes 0-9/100", SharePointService.contentRange(0, 9, 100));
        assertEquals("bytes 10-19/100", SharePointService.contentRange(10, 19, 100));
    }

    @Test
    void chunkRangesCoverWholeFileWithNoGaps() {
        // 25 bytes, 10-byte chunks -> [0-9],[10-19],[20-24]
        var ranges = SharePointService.chunkRanges(25, 10);
        assertEquals(3, ranges.size());
        assertArrayEquals(new long[]{0, 9},  ranges.get(0));
        assertArrayEquals(new long[]{10, 19}, ranges.get(1));
        assertArrayEquals(new long[]{20, 24}, ranges.get(2));
    }

    @Test
    void chunkRangesExactMultiple() {
        var ranges = SharePointService.chunkRanges(20, 10);
        assertEquals(2, ranges.size());
        assertArrayEquals(new long[]{10, 19}, ranges.get(1));
    }
}
