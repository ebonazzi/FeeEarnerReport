package net.javalover.feeearner.sharepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javalover.feeearner.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uploads files to SharePoint via the Microsoft Graph API, emulating the reference Python
 * uploader: client-credentials token, site/drive resolution, then a simple PUT (&le;4 MB) or
 * a resumable upload session in 10 MB chunks (&gt;4 MB). Built on the JDK HttpClient + Jackson.
 *
 * <p>The five network methods are {@code public} and non-final so they can be overridden in
 * unit tests; the URL/chunk helpers are static and pure.
 */
public class SharePointService {

    private static final Logger log = LoggerFactory.getLogger(SharePointService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final int  SIMPLE_MAX = 4 * 1024 * 1024;    // 4 MB
    static final int  CHUNK_SIZE = 10 * 1024 * 1024;   // 10 MB (multiple of 320 KB)
    private static final String GRAPH = "https://graph.microsoft.com/v1.0";

    private final HttpClient http;

    public SharePointService() {
        this(HttpClient.newHttpClient());
    }

    public SharePointService(HttpClient http) {
        this.http = http;
    }

    // ── Network operations (overridable for tests) ────────────────────────────

    public String acquireToken(AppConfig config) {
        String body = "grant_type=client_credentials"
            + "&client_id="     + enc(config.sharePointClientId())
            + "&client_secret=" + enc(config.sharePointSecretId())
            + "&scope="         + enc("https://graph.microsoft.com/.default");
        var req = HttpRequest.newBuilder(URI.create(tokenUrl(config.sharePointTenantId())))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var resp = sendString(req, "token request");
        return field(resp.body(), "access_token", "token response");
    }

    public String resolveSiteId(String token, AppConfig config) {
        var req = authedGet(siteUrl(config.sharePointHost(), config.sharePointSiteName()), token);
        var resp = sendString(req, "site lookup");
        return field(resp.body(), "id", "site response");
    }

    public String resolveDriveId(String token, String siteId) {
        var req = authedGet(GRAPH + "/sites/" + siteId + "/drive", token);
        var resp = sendString(req, "drive lookup");
        return field(resp.body(), "id", "drive response");
    }

    public void upload(String token, String driveId, String targetDir,
                       String filename, byte[] bytes) {
        if (bytes.length <= SIMPLE_MAX) {
            simpleUpload(token, driveId, targetDir, filename, bytes);
        } else {
            resumableUpload(token, driveId, targetDir, filename, bytes);
        }
    }

    public void delete(String token, String driveId, String targetDir, String filename) {
        var req = HttpRequest.newBuilder(URI.create(deleteUrl(driveId, targetDir, filename)))
            .header("Authorization", "Bearer " + token)
            .DELETE()
            .build();
        sendString(req, "delete of " + filename);
        log.info("Deleted '{}' from SharePoint.", filename);
    }

    private void simpleUpload(String token, String driveId, String targetDir,
                              String filename, byte[] bytes) {
        var req = HttpRequest.newBuilder(URI.create(simpleUploadUrl(driveId, targetDir, filename)))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/octet-stream")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build();
        sendString(req, "simple upload of " + filename);
        log.info("Uploaded '{}' to SharePoint ({} bytes, simple).", filename, bytes.length);
    }

    private void resumableUpload(String token, String driveId, String targetDir,
                                 String filename, byte[] bytes) {
        // 1. create the upload session
        String sessionBody = "{\"item\":{\"@microsoft.graph.conflictBehavior\":\"replace\","
            + "\"name\":\"" + jsonEscape(filename) + "\"}}";
        var sessionReq = HttpRequest.newBuilder(
                URI.create(createUploadSessionUrl(driveId, targetDir, filename)))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sessionBody))
            .build();
        var sessionResp = sendString(sessionReq, "create upload session for " + filename);
        String uploadUrl = field(sessionResp.body(), "uploadUrl", "upload session response");

        // 2. PUT each chunk to the pre-authenticated uploadUrl (no Authorization header)
        long total = bytes.length;
        for (long[] range : chunkRanges(total, CHUNK_SIZE)) {
            int start = (int) range[0];
            int end   = (int) range[1];
            byte[] chunk = Arrays.copyOfRange(bytes, start, end + 1);
            var chunkReq = HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Range", contentRange(range[0], range[1], total))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build();
            sendString(chunkReq, "upload chunk " + start + "-" + end + " of " + filename);
        }
        log.info("Uploaded '{}' to SharePoint ({} bytes, resumable).", filename, total);
    }

    // ── HTTP plumbing ─────────────────────────────────────────────────────────

    private HttpRequest authedGet(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    }

    private HttpResponse<String> sendString(HttpRequest req, String what) {
        try {
            var resp = http.send(req, BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new SharePointException(
                    what + " failed: HTTP " + resp.statusCode() + " — " + resp.body());
            }
            return resp;
        } catch (IOException e) {
            throw new SharePointException(what + " failed (I/O)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SharePointException(what + " interrupted", e);
        }
    }

    private static String field(String json, String name, String what) {
        try {
            JsonNode node = MAPPER.readTree(json).get(name);
            if (node == null || node.isNull() || node.asText().isBlank()) {
                throw new SharePointException("Missing '" + name + "' in " + what + ": " + json);
            }
            return node.asText();
        } catch (IOException e) {
            throw new SharePointException("Unparseable " + what, e);
        }
    }

    // ── Pure helpers (unit-tested) ────────────────────────────────────────────

    static String tokenUrl(String tenantId) {
        return "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
    }

    static String siteUrl(String host, String siteName) {
        return "root".equalsIgnoreCase(siteName)
            ? GRAPH + "/sites/" + host
            : GRAPH + "/sites/" + host + ":/sites/" + siteName;
    }

    static String simpleUploadUrl(String driveId, String targetDir, String filename) {
        return GRAPH + "/drives/" + driveId + "/root:/" + graphPath(targetDir, filename) + ":/content";
    }

    static String createUploadSessionUrl(String driveId, String targetDir, String filename) {
        return GRAPH + "/drives/" + driveId + "/root:/" + graphPath(targetDir, filename)
            + ":/createUploadSession";
    }

    static String deleteUrl(String driveId, String targetDir, String filename) {
        return GRAPH + "/drives/" + driveId + "/root:/" + graphPath(targetDir, filename);
    }

    static String contentRange(long start, long end, long total) {
        return "bytes " + start + "-" + end + "/" + total;
    }

    /** Inclusive [start,end] byte ranges covering {@code total} bytes in {@code chunk}-sized pieces. */
    static List<long[]> chunkRanges(long total, long chunk) {
        var ranges = new ArrayList<long[]>();
        long start = 0;
        while (start < total) {
            long end = Math.min(start + chunk, total) - 1;
            ranges.add(new long[]{start, end});
            start = end + 1;
        }
        return ranges;
    }

    /** Percent-encodes each path segment (spaces -> %20) while preserving the "/" separators. */
    private static String graphPath(String targetDir, String filename) {
        String combined = (targetDir + "/" + filename).replaceFirst("^/+", "");
        var encoded = new ArrayList<String>();
        for (String seg : combined.split("/")) {
            if (!seg.isEmpty()) encoded.add(encSegment(seg));
        }
        return String.join("/", encoded);
    }

    private static String encSegment(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
