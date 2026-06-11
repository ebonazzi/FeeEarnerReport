# SharePoint Deployment, Past-Run Regeneration, Menu Restructure & Email Recipient Change — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SharePoint upload (with resumable >4 MB upload), regeneration of spreadsheets from archive tables of a past run, a five-menu GUI, and change email delivery to the configured recipient list only.

**Architecture:** Layered as today (`ui` → `service` → `repository` → SQL Server). New `sharepoint` package holds a `SharePointService` (JDK `HttpClient` + Jackson, emulating the reference Python uploader). A new `DeployService` mirrors `EmailService`. `ArchiveRepository` gains read methods so `SpreadsheetService` can rebuild a workbook from a past run and store it as the fee earner's most-recent blob. The GUI's single "Actions" menu becomes five top-level menus.

**Tech Stack:** Java 25, JavaFX (Liberica Full JDK), Apache POI, mssql-jdbc/HikariCP, Jakarta Mail, JUnit 5, **new:** `jackson-databind` + the built-in `java.net.http.HttpClient`.

**Design doc:** `docs/superpowers/specs/2026-06-11-sharepoint-deploy-and-past-regeneration-design.md`

---

## File Structure

**Created:**
- `src/main/java/net/javalover/feeearner/sharepoint/SharePointService.java` — Graph REST: token, site, drive, simple + resumable upload. Pure URL/chunk helpers are static.
- `src/main/java/net/javalover/feeearner/sharepoint/SharePointException.java` — runtime exception for Graph failures.
- `src/main/java/net/javalover/feeearner/service/DeployService.java` — orchestrates uploads (all / single), builds the SharePoint filename. Mirrors `EmailService`.
- `src/main/java/net/javalover/feeearner/ui/GenerateAllPastWindow.java` — pick a past run, regenerate all its fee earners.
- `src/main/java/net/javalover/feeearner/ui/GenerateSinglePastWindow.java` — pick a past run + one fee earner, regenerate that one.
- `src/main/java/net/javalover/feeearner/ui/DeployAllWindow.java` — clone of `EmailAllWindow` for SharePoint.
- `src/main/java/net/javalover/feeearner/ui/SingleDeployWindow.java` — clone of `SingleEmailWindow` for SharePoint.
- Tests: `AppConfigSharePointTest`, `SharePointServiceTest`, `DeployServiceTest`, `RunRepositoryTest`, `SpreadsheetServiceArchiveTest`, `ArchiveRepositoryReadIT`.

**Modified:**
- `pom.xml` — add `jackson-databind`.
- `config/AppConfig.java` — six SharePoint accessors.
- `email/MailSender.java` — recipients-only delivery.
- `repository/ArchiveRepository.java` — read methods + `getFeeEarnerIdsForRun`; nullable `DataSource`.
- `repository/RunRepository.java` — `updateMostRecentFeeEarnerBlob`.
- `service/SpreadsheetService.java` — `generateFromArchive`, `generateAllFromArchive`.
- `ui/MainWindow.java` — five menus; constructor gains `DeployService`.
- `FxApplication.java`, `Main.java` — wire `ArchiveRepository(ds)`, `SharePointService`, `DeployService`.
- `email/MailSenderTest.java` — update for recipients-only.

---

## Task 1: Add Jackson dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add the version property**

In `pom.xml`, inside `<properties>` (after the `junit.version` line, before `main.class`), add:

```xml
        <jackson.version>2.18.2</jackson.version>
```

- [ ] **Step 2: Add the dependency**

In `pom.xml`, inside `<dependencies>`, immediately after the `angus-mail` dependency block (the `</dependency>` on the line before `<!-- Test -->`), add:

```xml
        <!-- JSON parsing for Microsoft Graph (SharePoint) REST calls.
             The HTTP client itself is java.net.http.HttpClient from the JDK. -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
```

- [ ] **Step 3: Verify it resolves and the build still compiles**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS (Jackson downloaded, no compile errors).

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add jackson-databind for SharePoint Graph REST calls"
```

---

## Task 2: SharePoint accessors on AppConfig

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/config/AppConfig.java`
- Test: `src/test/java/net/javalover/feeearner/config/AppConfigSharePointTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/net/javalover/feeearner/config/AppConfigSharePointTest.java`:

```java
package net.javalover.feeearner.config;

import net.javalover.feeearner.model.AppParam;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigSharePointTest {

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "shrpnt_client_id",  "client-123",                         true),
            new AppParam(2, "shrpnt_tenant_id",  "tenant-456",                         true),
            new AppParam(3, "shrpnt_secret_id",  "secret-789",                         true),
            new AppParam(4, "shrpnt_host",       "example.sharepoint.com",             true),
            new AppParam(5, "shrpnt_site_name",  "root",                               true),
            new AppParam(6, "shrpnt_target_dir", "Shared Documents/LSP/VIC_reports",   true)
        ));
    }

    @Test
    void exposesAllSixSharePointParams() {
        var c = config();
        assertEquals("client-123", c.sharePointClientId());
        assertEquals("tenant-456", c.sharePointTenantId());
        assertEquals("secret-789", c.sharePointSecretId());
        assertEquals("example.sharepoint.com", c.sharePointHost());
        assertEquals("root", c.sharePointSiteName());
        assertEquals("Shared Documents/LSP/VIC_reports", c.sharePointTargetDir());
    }

    @Test
    void missingSharePointParamThrows() {
        var c = AppConfig.from(List.of());
        assertThrows(IllegalStateException.class, c::sharePointClientId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AppConfigSharePointTest`
Expected: FAIL — compile error, `sharePointClientId()` not defined.

- [ ] **Step 3: Add the accessors**

In `src/main/java/net/javalover/feeearner/config/AppConfig.java`, after the `smtpPort()` method (before the closing brace of the record), add:

```java

    public String sharePointClientId()
    {
        return require("shrpnt_client_id");
    }

    public String sharePointTenantId()
    {
        return require("shrpnt_tenant_id");
    }

    public String sharePointSecretId()
    {
        return require("shrpnt_secret_id");
    }

    public String sharePointHost()
    {
        return require("shrpnt_host");
    }

    public String sharePointSiteName()
    {
        return require("shrpnt_site_name");
    }

    public String sharePointTargetDir()
    {
        return require("shrpnt_target_dir");
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AppConfigSharePointTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/config/AppConfig.java \
        src/test/java/net/javalover/feeearner/config/AppConfigSharePointTest.java
git commit -m "feat: add six SharePoint params to AppConfig"
```

---

## Task 3: Email goes to recipients only (never the fee earner)

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/email/MailSender.java`
- Test: `src/test/java/net/javalover/feeearner/email/MailSenderTest.java`

- [ ] **Step 1: Rewrite the test to assert recipients-only behavior**

Replace the entire body of `src/test/java/net/javalover/feeearner/email/MailSenderTest.java` with:

```java
package net.javalover.feeearner.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMultipart;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.AppParam;
import net.javalover.feeearner.model.FeeEarnerRun;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class MailSenderTest {

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "smtp_server",      "localhost",            true),
            new AppParam(2, "smtp_port",        "25",                   true),
            new AppParam(3, "email_sender",     "reports@law.com",      true),
            new AppParam(4, "email_recipients", "a@law.com|b@law.com",  true),
            new AppParam(5, "email_subject",    "Fee Earner Report",    true),
            new AppParam(6, "email_body",       "Please find attached.", true),
            new AppParam(7, "log_dir",          "/tmp",                 true)
        ));
    }

    private FeeEarnerRun run() {
        return new FeeEarnerRun(1, LocalDate.of(2026, 6, 5), 100,
            "Alice Smith", "alice@law.com", "fe_100_20260605_1.xlsx",
            new byte[]{1, 2, 3}, null);
    }

    @Test
    void toRecipientsAreTheConfiguredListNotTheFeeEarner() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var toAddrs = msg.getRecipients(jakarta.mail.Message.RecipientType.TO);
        assertEquals(2, toAddrs.length);
        var asText = java.util.Arrays.stream(toAddrs).map(Object::toString).toList();
        assertTrue(asText.contains("a@law.com"));
        assertTrue(asText.contains("b@law.com"));
        assertFalse(asText.contains("alice@law.com"), "fee earner must NOT be a recipient");
    }

    @Test
    void feeEarnerIsNeverACcRecipient() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var ccAddrs = msg.getRecipients(jakarta.mail.Message.RecipientType.CC);
        assertNull(ccAddrs, "no CC recipients; fee earner is not emailed");
    }

    @Test
    void blankRecipientListThrows() {
        var cfg = AppConfig.from(List.of(
            new AppParam(1, "smtp_server",      "localhost",        true),
            new AppParam(2, "smtp_port",        "25",               true),
            new AppParam(3, "email_sender",     "reports@law.com",  true),
            new AppParam(4, "email_recipients", "",                 true),
            new AppParam(5, "email_subject",    "Fee Earner Report", true),
            new AppParam(6, "email_body",       "x",                true)
        ));
        var session = Session.getInstance(new Properties());
        assertThrows(IllegalStateException.class,
            () -> MailSender.buildMessage(session, run(), cfg));
    }

    @Test
    void messageHasXlsxAttachment() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        var multipart = (MimeMultipart) msg.getContent();
        assertEquals(2, multipart.getCount());
        assertEquals("fe_100_20260605_1.xlsx", multipart.getBodyPart(1).getFileName());
    }

    @Test
    void messageHasCorrectSubject() throws Exception {
        var session = Session.getInstance(new Properties());
        var msg = MailSender.buildMessage(session, run(), config());
        assertEquals("Fee Earner Report", msg.getSubject());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=MailSenderTest`
Expected: FAIL — current code adds the fee earner as TO and recipients as CC.

- [ ] **Step 3: Change `buildMessage` to recipients-only**

In `src/main/java/net/javalover/feeearner/email/MailSender.java`, replace the recipient block. Change the `send` method's blank-email guard and `buildMessage`'s TO/CC logic.

Replace these lines in `send(...)`:

```java
        if (run == null) throw new IllegalArgumentException("run must not be null");
        if (run.usrEmail() == null || run.usrEmail().isBlank())
            throw new IllegalArgumentException("run.usrEmail() must not be blank for usrID=" + run.usrID());
```

with:

```java
        if (run == null) throw new IllegalArgumentException("run must not be null");
```

Then replace this block in `buildMessage`:

```java
        var msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(config.emailSender()));
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(run.usrEmail()));

        var recipients = config.emailRecipients();
        if (!recipients.isBlank()) {
            for (var addr : recipients.split("\\|")) {
                var trimmed = addr.trim();
                if (!trimmed.isEmpty()) {
                    msg.addRecipient(Message.RecipientType.CC, new InternetAddress(trimmed));
                }
            }
        }
```

with:

```java
        var msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(config.emailSender()));

        // Spreadsheets go ONLY to the configured email_recipients list; the fee earner
        // is never emailed. A blank list is an error (we do not send a recipient-less message).
        int added = 0;
        var recipients = config.emailRecipients();
        if (!recipients.isBlank()) {
            for (var addr : recipients.split("\\|")) {
                var trimmed = addr.trim();
                if (!trimmed.isEmpty()) {
                    msg.addRecipient(Message.RecipientType.TO, new InternetAddress(trimmed));
                    added++;
                }
            }
        }
        if (added == 0) {
            throw new IllegalStateException(
                "email_recipients is empty — no recipients to send the spreadsheet to (usrID="
                + run.usrID() + ")");
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=MailSenderTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/email/MailSender.java \
        src/test/java/net/javalover/feeearner/email/MailSenderTest.java
git commit -m "feat: email spreadsheets to recipient list only, never the fee earner"
```

---

## Task 4: Archive read methods

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/repository/ArchiveRepository.java`
- Test: `src/test/java/net/javalover/feeearner/repository/ArchiveRepositoryReadIT.java`

> DB reads in this repo are covered by integration tests (`*IT.java`, real SQL Server), consistent with `WorksheetRepository` (which has no unit test). The unit test suite (`mvn test`) stays green because `*IT` is excluded.

- [ ] **Step 1: Add a nullable `DataSource` and a second constructor**

In `src/main/java/net/javalover/feeearner/repository/ArchiveRepository.java`, add the import and a field+constructors at the top of the class. After the `ARCHIVE_TABLES` constant, add:

```java

    /** Non-null only when read methods are needed; write methods are connection-participants. */
    private final javax.sql.DataSource ds;

    /** Write-only / test constructor — read methods will throw if used. */
    public ArchiveRepository() {
        this(null);
    }

    /** Full constructor enabling the read methods (past-run regeneration). */
    public ArchiveRepository(javax.sql.DataSource ds) {
        this.ds = ds;
    }
```

- [ ] **Step 2: Add the read methods and helpers**

In the same file, before the final closing brace `}`, add:

```java

    // ── Reads (past-run regeneration) ─────────────────────────────────────────

    public List<Integer> getFeeEarnerIdsForRun(int runId) {
        var sql = "SELECT usrID FROM report.full_task_archive   WHERE run_id=? " +
                  "UNION SELECT usrID FROM report.limitation_archive WHERE run_id=? " +
                  "UNION SELECT usrID FROM report.aged_archive       WHERE run_id=? " +
                  "UNION SELECT usrID FROM report.duplicate_task_archive WHERE run_id=? " +
                  "UNION SELECT usrID FROM report.high_volume_archive WHERE run_id=? " +
                  "ORDER BY usrID";
        try (var conn = requireDs().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) stmt.setInt(i, runId);
            try (var rs = stmt.executeQuery()) {
                var ids = new java.util.ArrayList<Integer>();
                while (rs.next()) ids.add(rs.getInt("usrID"));
                return ids;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list fee earners for runId=" + runId, e);
        }
    }

    public List<FullTaskRow> readFullTask(int runId, int usrID) {
        return read("report.full_task_archive", runId, usrID, rs -> new FullTaskRow(
            rs.getString("Type"), toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"), rs.getString("Matter Name/Description"),
            rs.getString("Department"), rs.getString("Practice Code"),
            rs.getString("Office Name"), rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"), rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"), rs.getString("Task Description"),
            rs.getString("Task Type"), rs.getString("Task Notes"), rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"), toLocalDateTime(rs, "Task Due Date")));
    }

    public List<LimitationRow> readLimitation(int runId, int usrID) {
        return read("report.limitation_archive", runId, usrID, rs -> new LimitationRow(
            rs.getString("Type"), toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"), rs.getString("Matter Name/Description"),
            rs.getString("Department"), rs.getString("Practice Code"),
            rs.getString("Office Name"), rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"), rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"), rs.getString("Task Description"),
            rs.getString("Task Type"), rs.getString("Task Notes"), rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"), toLocalDateTime(rs, "Task Due Date"),
            rs.getString("Key Words")));
    }

    public List<AgedRow> readAged(int runId, int usrID) {
        return read("report.aged_archive", runId, usrID, rs -> new AgedRow(
            rs.getString("Type"), toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"), rs.getString("Matter Name/Description"),
            rs.getString("Department"), rs.getString("Practice Code"),
            rs.getString("Office Name"), rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"), rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"), rs.getString("Task Description"),
            rs.getString("Task Type"), rs.getString("Task Notes"), rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"), toLocalDateTime(rs, "Task Due Date")));
    }

    public List<DuplicateRow> readDuplicate(int runId, int usrID) {
        return read("report.duplicate_task_archive", runId, usrID, rs -> new DuplicateRow(
            rs.getString("Type"), toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"), rs.getString("Matter Name/Description"),
            rs.getString("Department"), rs.getString("Practice Code"),
            rs.getString("Office Name"), rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"), rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"), rs.getString("Task Description"),
            rs.getString("Task Type"), rs.getString("Task Notes"), rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"), toLocalDateTime(rs, "Task Due Date"),
            rs.getString("Duplicate")));
    }

    public List<HighVolumeRow> readHighVolume(int runId, int usrID) {
        return read("report.high_volume_archive", runId, usrID, rs -> new HighVolumeRow(
            rs.getString("Type"), toLocalDate(rs, "Report Date"),
            rs.getString("Matter Number"), rs.getString("Matter Name/Description"),
            rs.getString("Department"), rs.getString("Practice Code"),
            rs.getString("Office Name"), rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"), rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"), rs.getString("Task Description"),
            rs.getString("Task Type"), rs.getString("Task Notes"), rs.getString("Task Owner"),
            toLocalDateTime(rs, "Task Created Date"), toLocalDateTime(rs, "Task Due Date"),
            rs.getInt("Matter Row Count")));
    }

    @FunctionalInterface
    private interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private <T> List<T> read(String table, int runId, int usrID, RowMapper<T> mapper) {
        var sql = "SELECT * FROM " + table +
                  " WHERE run_id=? AND usrID=? ORDER BY row_number";
        try (var conn = requireDs().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, runId);
            stmt.setInt(2, usrID);
            try (var rs = stmt.executeQuery()) {
                var list = new java.util.ArrayList<T>();
                while (rs.next()) list.add(mapper.map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Archive read failed for " + table +
                " run_id=" + runId + " usrID=" + usrID, e);
        }
    }

    private javax.sql.DataSource requireDs() {
        if (ds == null) throw new IllegalStateException(
            "ArchiveRepository was constructed without a DataSource; read methods unavailable");
        return ds;
    }

    private static java.time.LocalDate toLocalDate(ResultSet rs, String col) throws SQLException {
        var d = rs.getDate(col);
        return d != null ? d.toLocalDate() : null;
    }

    private static java.time.LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        var ts = rs.getTimestamp(col);
        return ts != null ? ts.toLocalDateTime() : null;
    }
```

- [ ] **Step 3: Verify it compiles and existing unit tests still pass**

Run: `mvn -q test -Dtest=ArchiveRepositoryTest`
Expected: PASS (3 tests) — the no-arg constructor still works, write methods unchanged.

- [ ] **Step 4: Add a round-trip integration test**

Create `src/test/java/net/javalover/feeearner/repository/ArchiveRepositoryReadIT.java`:

```java
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
```

- [ ] **Step 5: Verify the IT compiles (run only if a SQL Server + test-credentials.properties is available)**

Run (optional, needs DB): `mvn -q -Pintegration test -Dtest=ArchiveRepositoryReadIT`
Expected: PASS. If no DB is configured, at minimum confirm compilation with `mvn -q -Pintegration test-compile`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/javalover/feeearner/repository/ArchiveRepository.java \
        src/test/java/net/javalover/feeearner/repository/ArchiveRepositoryReadIT.java
git commit -m "feat: read methods on ArchiveRepository for past-run regeneration"
```

---

## Task 5: `RunRepository.updateMostRecentFeeEarnerBlob`

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/repository/RunRepository.java`
- Test: `src/test/java/net/javalover/feeearner/repository/RunRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/net/javalover/feeearner/repository/RunRepositoryTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RunRepositoryTest`
Expected: FAIL — `updateMostRecentFeeEarnerBlob` not defined.

- [ ] **Step 3: Implement the method**

In `src/main/java/net/javalover/feeearner/repository/RunRepository.java`, after `updateFeeEarnerRun(...)` (before `getAllRuns()`), add:

```java

    /**
     * Replaces the stored spreadsheet of a fee earner's <em>most recent</em> run row
     * (highest run_id) and stamps {@code stored_at = now}. Used by past-run regeneration:
     * the regenerated workbook becomes the current spreadsheet, regardless of which run it
     * was rebuilt from. Manages its own connection (not a transaction participant).
     */
    public void updateMostRecentFeeEarnerBlob(int usrID, String filename, byte[] xlsx) {
        var sql = "UPDATE report.FeeEarnersRun " +
                  "SET excel_filename=?, excel_spreadsheet=?, stored_at=GETDATE() " +
                  "WHERE run_id = (SELECT TOP 1 run_id FROM report.FeeEarnersRun " +
                  "                WHERE usrID=? ORDER BY run_id DESC) " +
                  "  AND usrID=?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filename);
            stmt.setBytes(2, xlsx);
            stmt.setInt(3, usrID);
            stmt.setInt(4, usrID);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException(
                    "No FeeEarnersRun row to update for usrID=" + usrID +
                    " (regeneration requires an existing run row)");
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                "Failed to update most-recent blob for usrID=" + usrID, e);
        }
    }
```

> Note: the `updated == 0` guard is what the Step 1 test's try/catch tolerates — `FakeJdbc.executeUpdate` returns 0 rows, so the guard throws even though the SQL ran. The integration path (real DB) updates one row and does not throw.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RunRepositoryTest`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/repository/RunRepository.java \
        src/test/java/net/javalover/feeearner/repository/RunRepositoryTest.java
git commit -m "feat: RunRepository.updateMostRecentFeeEarnerBlob for past-run regeneration"
```

---

## Task 6: `SpreadsheetService` archive-regeneration methods

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/service/SpreadsheetService.java`
- Test: `src/test/java/net/javalover/feeearner/service/SpreadsheetServiceArchiveTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/net/javalover/feeearner/service/SpreadsheetServiceArchiveTest.java`:

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.FakeJdbc;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.excel.WorkbookBuilder;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetServiceArchiveTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 5);

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "output_dir",       "/tmp", true),
            new AppParam(2, "thread_pool_size", "2",    true),
            new AppParam(3, "log_dir",          "/tmp", true)
        ));
    }

    private FullTaskRow fullTask() {
        return new FullTaskRow("Matter", DAY, "M-1", "Matter", "Dept", "PC", "Off",
            "VIC", "Joseph Tran", "LA", "Sup", "Desc", "TT", "Notes", "Owner",
            LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 17, 0));
    }

    /** Stub repos shared by tests. `stored` records each blob update. */
    private RunRepository runRepoRecording(CopyOnWriteArrayList<String> stored, int mostRecentRunId) {
        return new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return Optional.of(new FeeEarnerRun(mostRecentRunId, DAY, usrID,
                    "Joseph Tran", "x@law.com", "old.xlsx", new byte[]{0}, null));
            }
            @Override public void updateMostRecentFeeEarnerBlob(int usrID, String filename, byte[] xlsx) {
                stored.add(usrID + ":" + filename + ":" + xlsx.length);
            }
        };
    }

    private ArchiveRepository archiveWithRows(List<Integer> ids) {
        return new ArchiveRepository() {
            @Override public List<Integer> getFeeEarnerIdsForRun(int runId) { return ids; }
            @Override public List<FullTaskRow> readFullTask(int r, int u) { return List.of(fullTask()); }
            @Override public List<LimitationRow> readLimitation(int r, int u) { return List.of(); }
            @Override public List<AgedRow> readAged(int r, int u) { return List.of(); }
            @Override public List<DuplicateRow> readDuplicate(int r, int u) { return List.of(); }
            @Override public List<HighVolumeRow> readHighVolume(int r, int u) { return List.of(); }
        };
    }

    private SpreadsheetService service(ArchiveRepository archive, RunRepository runRepo) {
        return new SpreadsheetService(new FakeJdbc().dataSource(),
            new WorksheetRepository(null), archive, runRepo,
            new FeeEarnerRepository(null), new WorkbookBuilder());
    }

    @Test
    void generateFromArchiveStoresIntoMostRecentRow() {
        var stored = new CopyOnWriteArrayList<String>();
        var svc = service(archiveWithRows(List.of(100)), runRepoRecording(stored, 7));
        svc.generateFromArchive(100, 3, config());     // source run = 3, most-recent run = 7
        assertEquals(1, stored.size());
        var entry = stored.get(0);                     // "usrID:filename:blobLen"
        assertTrue(entry.startsWith("100:fe_100_"), "uses standard DB filename with usrID");
        assertTrue(entry.contains("_7.xlsx:"),
            "filename uses the most-recent runId (7), not the source run (3)");
    }

    @Test
    void generateAllFromArchiveProcessesEveryFeeEarner() {
        var stored = new CopyOnWriteArrayList<String>();
        var svc = service(archiveWithRows(List.of(100, 200, 300)), runRepoRecording(stored, 7));
        var tracker = new ProgressTracker(0);
        svc.generateAllFromArchive(3, config(), tracker);
        assertEquals(3, tracker.completed().get());
        assertEquals(3, tracker.total().get());
        assertEquals(3, stored.size());
    }

    /** A fee earner with no most-recent row is recorded as a failure, batch continues. */
    @Test
    void generateAllFromArchiveTracksFailures() {
        var stored = new CopyOnWriteArrayList<String>();
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return usrID == 200 ? Optional.empty()
                    : Optional.of(new FeeEarnerRun(7, DAY, usrID, "N", "e", "o.xlsx", new byte[]{0}, null));
            }
            @Override public void updateMostRecentFeeEarnerBlob(int usrID, String f, byte[] x) {
                stored.add(String.valueOf(usrID));
            }
        };
        var svc = service(archiveWithRows(List.of(100, 200, 300)), runRepo);
        var tracker = new ProgressTracker(0);
        svc.generateAllFromArchive(3, config(), tracker);
        assertEquals(2, tracker.completed().get());
        assertEquals(1, tracker.failed().get());
        assertEquals(1, tracker.failures().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SpreadsheetServiceArchiveTest`
Expected: FAIL — `generateFromArchive` / `generateAllFromArchive` not defined.

- [ ] **Step 3: Implement the two methods**

In `src/main/java/net/javalover/feeearner/service/SpreadsheetService.java`, after `generateAll(...)` (before `private void doGenerate`), add:

```java

    /**
     * Rebuilds one fee earner's spreadsheet from the archive tables of {@code runId} and
     * stores it as that fee earner's MOST RECENT spreadsheet (blob replaced, stored_at=now).
     * The archive tables are the read-only source and are not modified. Requires an existing
     * FeeEarnersRun row for the fee earner.
     */
    public void generateFromArchive(int usrID, int runId, AppConfig config) {
        var fullTask   = archiveRepo.readFullTask(runId, usrID);
        var limitation = archiveRepo.readLimitation(runId, usrID);
        var aged       = archiveRepo.readAged(runId, usrID);
        var duplicate  = archiveRepo.readDuplicate(runId, usrID);
        var highVolume = archiveRepo.readHighVolume(runId, usrID);

        byte[] xlsx;
        try {
            xlsx = workbookBuilder.build(fullTask, limitation, aged, duplicate, highVolume);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build workbook for usrID=" + usrID, e);
        }

        var recent = runRepo.getMostRecent(usrID)
            .orElseThrow(() -> new IllegalStateException(
                "No FeeEarnersRun row for usrID=" + usrID + "; cannot regenerate"));
        String filename = "fe_" + usrID + "_" + LocalDate.now().format(DATE_FMT)
                          + "_" + recent.runId() + ".xlsx";
        runRepo.updateMostRecentFeeEarnerBlob(usrID, filename, xlsx);
    }

    /**
     * Regenerates every fee earner present in {@code runId}'s archive, recording per-fee-earner
     * failures in the tracker and continuing the batch (one bad fee earner never aborts it).
     */
    public void generateAllFromArchive(int runId, AppConfig config, ProgressTracker tracker) {
        var ids = archiveRepo.getFeeEarnerIdsForRun(runId);
        tracker.total().set(ids.size());
        var pool = Executors.newFixedThreadPool(config.threadPoolSize());
        var completion = new ExecutorCompletionService<Void>(pool);

        for (var usrID : ids) {
            completion.submit(() -> {
                try {
                    generateFromArchive(usrID, runId, config);
                    tracker.completed().incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to regenerate from archive for usrID={} run={}", usrID, runId, e);
                    tracker.failed().incrementAndGet();
                    tracker.failures().add(new FailedEntry(usrID, String.valueOf(usrID), e.getMessage()));
                }
                return null;
            });
        }
        try {
            for (int i = 0; i < ids.size(); i++) {
                try {
                    completion.take().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ignored) {
                    // handled inside the Callable
                }
            }
        } finally {
            pool.shutdown();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SpreadsheetServiceArchiveTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the existing service tests to confirm no regression**

Run: `mvn -q test -Dtest=SpreadsheetServiceTest`
Expected: PASS (all existing tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/javalover/feeearner/service/SpreadsheetService.java \
        src/test/java/net/javalover/feeearner/service/SpreadsheetServiceArchiveTest.java
git commit -m "feat: regenerate spreadsheets from archive into the most-recent run blob"
```

---

## Task 7: `SharePointService` (Graph REST + resumable upload)

**Files:**
- Create: `src/main/java/net/javalover/feeearner/sharepoint/SharePointException.java`
- Create: `src/main/java/net/javalover/feeearner/sharepoint/SharePointService.java`
- Test: `src/test/java/net/javalover/feeearner/sharepoint/SharePointServiceTest.java`

- [ ] **Step 1: Write the failing test (pure helpers only — no network)**

Create `src/test/java/net/javalover/feeearner/sharepoint/SharePointServiceTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SharePointServiceTest`
Expected: FAIL — class/methods not defined.

- [ ] **Step 3: Create the exception type**

Create `src/main/java/net/javalover/feeearner/sharepoint/SharePointException.java`:

```java
package net.javalover.feeearner.sharepoint;

/** Wraps any failure (transport, auth, HTTP non-2xx) talking to Microsoft Graph. */
public class SharePointException extends RuntimeException {
    public SharePointException(String message) { super(message); }
    public SharePointException(String message, Throwable cause) { super(message, cause); }
}
```

- [ ] **Step 4: Create `SharePointService`**

Create `src/main/java/net/javalover/feeearner/sharepoint/SharePointService.java`:

```java
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
 * <p>The four network methods are {@code public} and non-final so they can be overridden in
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=SharePointServiceTest`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/javalover/feeearner/sharepoint/ \
        src/test/java/net/javalover/feeearner/sharepoint/SharePointServiceTest.java
git commit -m "feat: SharePointService — Graph token/site/drive + simple and resumable upload"
```

---

## Task 8: `DeployService`

**Files:**
- Create: `src/main/java/net/javalover/feeearner/service/DeployService.java`
- Test: `src/test/java/net/javalover/feeearner/service/DeployServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/net/javalover/feeearner/service/DeployServiceTest.java`:

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.*;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.sharepoint.SharePointService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class DeployServiceTest {

    private AppConfig config() {
        return AppConfig.from(List.of(
            new AppParam(1, "shrpnt_client_id",  "c", true),
            new AppParam(2, "shrpnt_tenant_id",  "t", true),
            new AppParam(3, "shrpnt_secret_id",  "s", true),
            new AppParam(4, "shrpnt_host",       "h.sharepoint.com", true),
            new AppParam(5, "shrpnt_site_name",  "root", true),
            new AppParam(6, "shrpnt_target_dir", "Folder", true)
        ));
    }

    private FeeEarnerRun run(int usrID, String name) {
        return new FeeEarnerRun(7, LocalDate.of(2026, 6, 5), usrID, name,
            "e@law.com", "fe.xlsx", new byte[]{1, 2, 3}, null);
    }

    @Test
    void sharePointFileNameFormat() {
        assertEquals("Fee Earner_35189_Joseph Tran_VIC_task_report.xlsx",
            DeployService.sharePointFileName(35189, "Joseph Tran"));
    }

    /** Records each upload's filename; resolves token/site/drive once. */
    private SharePointService recordingSharePoint(CopyOnWriteArrayList<String> uploads, int[] tokenCalls) {
        return new SharePointService() {
            @Override public String acquireToken(AppConfig c) { tokenCalls[0]++; return "tok"; }
            @Override public String resolveSiteId(String t, AppConfig c) { return "site"; }
            @Override public String resolveDriveId(String t, String s) { return "drive"; }
            @Override public void upload(String t, String d, String dir, String file, byte[] b) {
                uploads.add(file);
            }
        };
    }

    @Test
    void deployAllUploadsEachWithSharePointNameAndResolvesTokenOnce() {
        var uploads = new CopyOnWriteArrayList<String>();
        var tokenCalls = new int[]{0};
        var svc = new DeployService(recordingSharePoint(uploads, tokenCalls), new RunRepository(null));
        var tracker = new ProgressTracker(0);
        svc.deployAll(List.of(run(1, "Joseph Tran"), run(2, "Mary Lee")), config(), tracker);

        assertEquals(2, tracker.completed().get());
        assertEquals(1, tokenCalls[0], "token/site/drive resolved once per batch");
        assertTrue(uploads.contains("Fee Earner_1_Joseph Tran_VIC_task_report.xlsx"));
        assertTrue(uploads.contains("Fee Earner_2_Mary Lee_VIC_task_report.xlsx"));
    }

    @Test
    void deployAllTracksFailures() {
        var sp = new SharePointService() {
            @Override public String acquireToken(AppConfig c) { return "tok"; }
            @Override public String resolveSiteId(String t, AppConfig c) { return "site"; }
            @Override public String resolveDriveId(String t, String s) { return "drive"; }
            @Override public void upload(String t, String d, String dir, String file, byte[] b) {
                throw new RuntimeException("graph down");
            }
        };
        var svc = new DeployService(sp, new RunRepository(null));
        var tracker = new ProgressTracker(0);
        svc.deployAll(List.of(run(1, "Joseph Tran")), config(), tracker);
        assertEquals(1, tracker.failed().get());
        assertEquals(1, tracker.failures().size());
    }

    @Test
    void deployForFeeEarnerUsesMostRecentRun() {
        var uploads = new CopyOnWriteArrayList<String>();
        var sp = recordingSharePoint(uploads, new int[]{0});
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) {
                return Optional.of(run(usrID, "Joseph Tran"));
            }
        };
        var svc = new DeployService(sp, runRepo);
        svc.deployForFeeEarner(35189, config());
        assertEquals(1, uploads.size());
        assertEquals("Fee Earner_35189_Joseph Tran_VIC_task_report.xlsx", uploads.get(0));
    }

    @Test
    void deployForFeeEarnerThrowsWhenNoRun() {
        var sp = recordingSharePoint(new CopyOnWriteArrayList<>(), new int[]{0});
        var runRepo = new RunRepository(null) {
            @Override public Optional<FeeEarnerRun> getMostRecent(int usrID) { return Optional.empty(); }
        };
        var svc = new DeployService(sp, runRepo);
        assertThrows(IllegalStateException.class, () -> svc.deployForFeeEarner(1, config()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=DeployServiceTest`
Expected: FAIL — `DeployService` not defined.

- [ ] **Step 3: Implement `DeployService`**

Create `src/main/java/net/javalover/feeearner/service/DeployService.java`:

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FailedEntry;
import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.sharepoint.SharePointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Uploads stored spreadsheets to SharePoint. Mirrors {@link EmailService}: a per-fee-earner
 * loop that records failures in a {@link ProgressTracker} and never aborts the batch. The
 * SharePoint token, site id and drive id are resolved once per batch and reused.
 */
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    private final SharePointService sharePoint;
    private final RunRepository runRepo;

    public DeployService(SharePointService sharePoint, RunRepository runRepo) {
        this.sharePoint = sharePoint;
        this.runRepo = runRepo;
    }

    /** SharePoint upload name, e.g. {@code Fee Earner_35189_Joseph Tran_VIC_task_report.xlsx}. */
    public static String sharePointFileName(int usrID, String feeEarnerName) {
        return "Fee Earner_" + usrID + "_" + feeEarnerName + "_VIC_task_report.xlsx";
    }

    public void deployAll(List<FeeEarnerRun> runs, AppConfig config, ProgressTracker tracker) {
        tracker.total().set(runs.size());
        String token   = sharePoint.acquireToken(config);
        String siteId  = sharePoint.resolveSiteId(token, config);
        String driveId = sharePoint.resolveDriveId(token, siteId);

        for (var run : runs) {
            try {
                uploadOne(token, driveId, config, run);
                tracker.completed().incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to deploy to SharePoint for usrID={}", run.usrID(), e);
                tracker.failed().incrementAndGet();
                tracker.failures().add(
                    new FailedEntry(run.usrID(), run.feeEarner(), e.getMessage()));
            }
        }
    }

    public void deployForFeeEarner(int usrID, AppConfig config) {
        var run = runRepo.getMostRecent(usrID)
            .orElseThrow(() -> new IllegalStateException(
                "No FeeEarnerRun found for usrID=" + usrID));
        String token   = sharePoint.acquireToken(config);
        String siteId  = sharePoint.resolveSiteId(token, config);
        String driveId = sharePoint.resolveDriveId(token, siteId);
        uploadOne(token, driveId, config, run);
    }

    private void uploadOne(String token, String driveId, AppConfig config, FeeEarnerRun run) {
        if (run.excelSpreadsheet() == null) {
            throw new IllegalStateException(
                "No stored spreadsheet for usrID=" + run.usrID());
        }
        String filename = sharePointFileName(run.usrID(), run.feeEarner());
        sharePoint.upload(token, driveId, config.sharePointTargetDir(),
            filename, run.excelSpreadsheet());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=DeployServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/service/DeployService.java \
        src/test/java/net/javalover/feeearner/service/DeployServiceTest.java
git commit -m "feat: DeployService uploads stored spreadsheets to SharePoint"
```

---

## Task 9: New UI windows

> UI windows have no automated tests in this repo (consistent with the existing windows). Each step verifies by compiling. These four windows are clones of existing ones with the noted changes.

**Files:**
- Create: `src/main/java/net/javalover/feeearner/ui/GenerateAllPastWindow.java`
- Create: `src/main/java/net/javalover/feeearner/ui/GenerateSinglePastWindow.java`
- Create: `src/main/java/net/javalover/feeearner/ui/DeployAllWindow.java`
- Create: `src/main/java/net/javalover/feeearner/ui/SingleDeployWindow.java`

- [ ] **Step 1: Create `GenerateAllPastWindow`**

Create `src/main/java/net/javalover/feeearner/ui/GenerateAllPastWindow.java`:

```java
package net.javalover.feeearner.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.model.RunInfo;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.SpreadsheetService;

public class GenerateAllPastWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunRepository runRepo;
    private final AppConfig config;

    public GenerateAllPastWindow(SpreadsheetService spreadsheetSvc, RunRepository runRepo,
                                 AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runRepo        = runRepo;
        this.config         = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Generate All Past Spreadsheets");

        var runTable = buildRunTable();
        runTable.setItems(FXCollections.observableArrayList(runRepo.getAllRuns()));

        var totalLabel     = new Label("Total: —");
        var completedLabel = new Label("Completed: 0");
        var remainingLabel = new Label("Remaining: —");
        var failureList    = new ListView<String>();
        failureList.setItems(FXCollections.observableArrayList());

        var generateBtn = new Button("Generate All Past");
        var exitBtn     = new Button("Exit");
        exitBtn.setOnAction(e -> stage.close());

        final ProgressTracker[] trackerRef = { null };
        var timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            var t = trackerRef[0];
            if (t == null) return;
            int completed = t.completed().get(), total = t.total().get(), failed = t.failed().get();
            completedLabel.setText("Completed: " + (completed + failed));
            remainingLabel.setText("Remaining: " + Math.max(0, total - completed - failed));
            t.failures().forEach(f -> {
                String entry = f.usrID() + " | " + f.feeEarner() + " | " + f.errorMessage();
                if (!failureList.getItems().contains(entry)) failureList.getItems().add(entry);
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        generateBtn.setOnAction(e -> {
            var selected = runTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                var a = new Alert(Alert.AlertType.WARNING, "Select a run first.", ButtonType.OK);
                a.initOwner(stage);
                a.showAndWait();
                return;
            }
            generateBtn.setDisable(true);
            var tracker = new ProgressTracker(0);
            trackerRef[0] = tracker;
            timeline.play();
            int runId = selected.runId();

            var thread = new Thread(() -> {
                boolean[] showAlert = { true };
                try {
                    spreadsheetSvc.generateAllFromArchive(runId, config, tracker);
                    Platform.runLater(() -> totalLabel.setText("Total: " + tracker.total().get()));
                } catch (Exception ex) {
                    showAlert[0] = false;
                    Platform.runLater(() -> {
                        var a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Error");
                        a.setContentText("Regeneration failed: " + String.valueOf(ex.getMessage()));
                        a.showAndWait();
                    });
                } finally {
                    final boolean show = showAlert[0];
                    Platform.runLater(() -> {
                        timeline.stop();
                        var t = trackerRef[0];
                        if (t != null) {
                            completedLabel.setText("Completed: " + (t.completed().get() + t.failed().get()));
                            remainingLabel.setText("Remaining: 0");
                        }
                        if (show) {
                            var a = new Alert(Alert.AlertType.INFORMATION);
                            a.setTitle("Complete");
                            a.setContentText("Regeneration complete. Completed: " +
                                (t != null ? t.completed().get() : 0) +
                                "  Failed: " + (t != null ? t.failed().get() : 0));
                            a.showAndWait();
                        }
                        generateBtn.setDisable(false);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        var statsBox = new HBox(20, totalLabel, completedLabel, remainingLabel);
        var buttons  = new HBox(10, generateBtn, exitBtn);
        var root = new VBox(10, new Label("Select a past run:"), runTable,
                            statsBox, new Label("Failures:"), failureList, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(runTable, Priority.ALWAYS);
        VBox.setVgrow(failureList, Priority.ALWAYS);

        stage.setScene(new Scene(root, 640, 560));
        stage.showAndWait();
    }

    static TableView<RunInfo> buildRunTable() {
        var table = new TableView<RunInfo>();
        var idCol = new TableColumn<RunInfo, String>("Run ID");
        idCol.setPrefWidth(80);
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().runId())));
        var dayCol = new TableColumn<RunInfo, String>("Day Run");
        dayCol.setPrefWidth(120);
        dayCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().dayRun())));
        var startCol = new TableColumn<RunInfo, String>("Started");
        startCol.setPrefWidth(180);
        startCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().startedAt())));
        var finishCol = new TableColumn<RunInfo, String>("Finished");
        finishCol.setPrefWidth(180);
        finishCol.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().finishedAt() != null ? String.valueOf(d.getValue().finishedAt()) : "—"));
        table.getColumns().addAll(idCol, dayCol, startCol, finishCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }
}
```

- [ ] **Step 2: Create `GenerateSinglePastWindow`**

Create `src/main/java/net/javalover/feeearner/ui/GenerateSinglePastWindow.java`:

```java
package net.javalover.feeearner.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.RunInfo;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.SpreadsheetService;

public class GenerateSinglePastWindow {

    private final SpreadsheetService spreadsheetSvc;
    private final RunRepository runRepo;
    private final AppConfig config;

    public GenerateSinglePastWindow(SpreadsheetService spreadsheetSvc, RunRepository runRepo,
                                    AppConfig config) {
        this.spreadsheetSvc = spreadsheetSvc;
        this.runRepo        = runRepo;
        this.config         = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Generate Single Past Spreadsheet");
        stage.setWidth(720);
        stage.setHeight(620);

        var runTable = GenerateAllPastWindow.buildRunTable();
        runTable.setItems(FXCollections.observableArrayList(runRepo.getAllRuns()));

        var feTable = buildFeeEarnerTable(stage);

        runTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                feTable.setItems(FXCollections.observableArrayList(
                    runRepo.getFeeEarnerRunsForRun(sel.runId())));
            } else {
                feTable.getItems().clear();
            }
        });

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());
        var footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8));

        var root = new VBox(8,
            new Label("Select a past run:"), runTable,
            new Label("Then a fee earner to regenerate:"), feTable, footer);
        root.setPadding(new Insets(10));
        VBox.setVgrow(runTable, Priority.ALWAYS);
        VBox.setVgrow(feTable, Priority.ALWAYS);

        stage.setScene(new Scene(root));
        stage.show();
    }

    private TableView<FeeEarnerRun> buildFeeEarnerTable(Stage stage) {
        var table = new TableView<FeeEarnerRun>();

        var idCol = new TableColumn<FeeEarnerRun, String>("User ID");
        idCol.setPrefWidth(80);
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().usrID())));

        var nameCol = new TableColumn<FeeEarnerRun, String>("Fee Earner");
        nameCol.setPrefWidth(240);
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().feeEarner()));

        var actionCol = new TableColumn<FeeEarnerRun, Void>("Action");
        actionCol.setPrefWidth(140);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Regenerate");
            {
                btn.setOnAction(event -> {
                    var fer = getTableView().getItems().get(getIndex());
                    handleRegenerate(fer, getScene().getWindow());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(idCol, nameCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private void handleRegenerate(FeeEarnerRun fer, Window owner) {
        try {
            spreadsheetSvc.generateFromArchive(fer.usrID(), fer.runId(), config);
            showAlert(owner, Alert.AlertType.INFORMATION, "Success",
                "Regenerated spreadsheet for " + fer.feeEarner());
        } catch (Exception ex) {
            showAlert(owner, Alert.AlertType.ERROR, "Error",
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private static void showAlert(Window owner, Alert.AlertType type, String title, String msg) {
        var alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
```

- [ ] **Step 3: Create `DeployAllWindow`** (clone of `EmailAllWindow`, calling `deployAll`)

Create `src/main/java/net/javalover/feeearner/ui/DeployAllWindow.java`:

```java
package net.javalover.feeearner.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.ProgressTracker;
import net.javalover.feeearner.repository.RunRepository;
import net.javalover.feeearner.service.DeployService;

public class DeployAllWindow {

    private final DeployService deploySvc;
    private final RunRepository runRepo;
    private final AppConfig config;

    public DeployAllWindow(DeployService deploySvc, RunRepository runRepo, AppConfig config) {
        this.deploySvc = deploySvc;
        this.runRepo   = runRepo;
        this.config    = config;
    }

    public void show(Window owner) {
        var stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Deploy All Spreadsheets");

        var totalLabel     = new Label("Total: —");
        var completedLabel = new Label("Completed: 0");
        var remainingLabel = new Label("Remaining: —");
        var failureList    = new ListView<String>();
        failureList.setItems(FXCollections.observableArrayList());

        var deployBtn = new Button("Deploy All");
        var exitBtn   = new Button("Exit");
        exitBtn.setOnAction(e -> stage.close());

        final ProgressTracker[] trackerRef = { null };
        var timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            var t = trackerRef[0];
            if (t == null) return;
            int completed = t.completed().get(), total = t.total().get(), failed = t.failed().get();
            completedLabel.setText("Completed: " + (completed + failed));
            remainingLabel.setText("Remaining: " + Math.max(0, total - completed - failed));
            t.failures().forEach(f -> {
                String entry = f.usrID() + " | " + f.feeEarner() + " | " + f.errorMessage();
                if (!failureList.getItems().contains(entry)) failureList.getItems().add(entry);
            });
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        deployBtn.setOnAction(e -> {
            deployBtn.setDisable(true);
            var tracker = new ProgressTracker(0);
            trackerRef[0] = tracker;
            timeline.play();

            var thread = new Thread(() -> {
                boolean[] showAlert = { true };
                try {
                    var allRuns = runRepo.getAllRuns();
                    if (allRuns.isEmpty()) {
                        showAlert[0] = false;
                        Platform.runLater(() -> {
                            timeline.stop();
                            var a = new Alert(Alert.AlertType.WARNING);
                            a.setTitle("No Runs Found");
                            a.setContentText("No runs found. Generate spreadsheets first.");
                            a.showAndWait();
                            deployBtn.setDisable(false);
                        });
                        return;
                    }
                    int latestRunId = allRuns.get(0).runId();
                    var feeEarnerRuns = runRepo.getFeeEarnerRunsForRun(latestRunId);
                    tracker.total().set(feeEarnerRuns.size());
                    Platform.runLater(() -> totalLabel.setText("Total: " + feeEarnerRuns.size()));
                    deploySvc.deployAll(feeEarnerRuns, config, tracker);
                } catch (Exception ex) {
                    showAlert[0] = false;
                    Platform.runLater(() -> {
                        var a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Error");
                        a.setContentText("Deploy failed: " + String.valueOf(ex.getMessage()));
                        a.showAndWait();
                    });
                } finally {
                    final boolean show = showAlert[0];
                    Platform.runLater(() -> {
                        timeline.stop();
                        var t = trackerRef[0];
                        if (t != null) {
                            completedLabel.setText("Completed: " + (t.completed().get() + t.failed().get()));
                            remainingLabel.setText("Remaining: 0");
                        }
                        if (show) {
                            var a = new Alert(Alert.AlertType.INFORMATION);
                            a.setTitle("Complete");
                            a.setContentText("Deploy complete. Completed: " +
                                (t != null ? t.completed().get() : 0) +
                                "  Failed: " + (t != null ? t.failed().get() : 0));
                            a.showAndWait();
                        }
                        deployBtn.setDisable(false);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        var statsBox = new HBox(20, totalLabel, completedLabel, remainingLabel);
        var buttons  = new HBox(10, deployBtn, exitBtn);
        var root = new VBox(10, statsBox, new Label("Failures:"), failureList, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(failureList, Priority.ALWAYS);

        stage.setScene(new Scene(root, 600, 450));
        stage.showAndWait();
    }
}
```

- [ ] **Step 4: Create `SingleDeployWindow`** (clone of `SingleEmailWindow`, calling `deployForFeeEarner`)

Create `src/main/java/net/javalover/feeearner/ui/SingleDeployWindow.java`:

```java
package net.javalover.feeearner.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.FeeEarner;
import net.javalover.feeearner.repository.FeeEarnerRepository;
import net.javalover.feeearner.service.DeployService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SingleDeployWindow {

    private final DeployService deploySvc;
    private final FeeEarnerRepository feeEarnerRepo;
    private final AppConfig config;

    public SingleDeployWindow(DeployService deploySvc, FeeEarnerRepository feeEarnerRepo,
                              AppConfig config) {
        this.deploySvc     = deploySvc;
        this.feeEarnerRepo = feeEarnerRepo;
        this.config        = config;
    }

    public void show(Window owner) {
        var feeEarners = merge(feeEarnerRepo.getLeadFeeEarners(),
                               feeEarnerRepo.getMatterFeeEarners());

        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Deploy Single Spreadsheet");
        stage.setWidth(700);
        stage.setHeight(500);

        var table = buildTable();
        table.setItems(FXCollections.observableArrayList(feeEarners));

        var closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());
        var footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 12, 8, 12));

        var root = new BorderPane();
        root.setCenter(table);
        root.setBottom(footer);
        BorderPane.setMargin(table, new Insets(8, 8, 0, 8));

        stage.setScene(new Scene(root));
        stage.show();
    }

    private TableView<FeeEarner> buildTable() {
        var table = new TableView<FeeEarner>();

        var idCol = new TableColumn<FeeEarner, String>("User ID");
        idCol.setPrefWidth(80);
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().usrID())));

        var nameCol = new TableColumn<FeeEarner, String>("Fee Earner");
        nameCol.setPrefWidth(240);
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().feeEarner()));

        var actionCol = new TableColumn<FeeEarner, Void>("Action");
        actionCol.setPrefWidth(140);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Deploy");
            {
                btn.setOnAction(event -> {
                    var fe = getTableView().getItems().get(getIndex());
                    handleDeploy(fe, getScene().getWindow());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(idCol, nameCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private void handleDeploy(FeeEarner fe, Window owner) {
        try {
            deploySvc.deployForFeeEarner(fe.usrID(), config);
            showAlert(owner, Alert.AlertType.INFORMATION, "Success",
                "Deployed to SharePoint for " + fe.feeEarner());
        } catch (Exception ex) {
            showAlert(owner, Alert.AlertType.ERROR, "Error",
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private static void showAlert(Window owner, Alert.AlertType type, String title, String msg) {
        var alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }

    private static List<FeeEarner> merge(List<FeeEarner> lead, List<FeeEarner> matter) {
        var result  = new ArrayList<>(lead);
        var leadIds = lead.stream().map(FeeEarner::usrID).collect(Collectors.toSet());
        matter.stream().filter(fe -> !leadIds.contains(fe.usrID())).forEach(result::add);
        return result;
    }
}
```

- [ ] **Step 5: Verify all four windows compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/GenerateAllPastWindow.java \
        src/main/java/net/javalover/feeearner/ui/GenerateSinglePastWindow.java \
        src/main/java/net/javalover/feeearner/ui/DeployAllWindow.java \
        src/main/java/net/javalover/feeearner/ui/SingleDeployWindow.java
git commit -m "feat: new windows for past regeneration and SharePoint deployment"
```

---

## Task 10: Five-menu `MainWindow`

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/ui/MainWindow.java`

- [ ] **Step 1: Add the `DeployService` field and constructor parameter**

In `src/main/java/net/javalover/feeearner/ui/MainWindow.java`, change the fields and constructor. Replace:

```java
    private final EmailService emailSvc;
    private final FeeEarnerRepository feeEarnerRepo;
    // Config is loaded at startup; changes via ParameterEditorWindow take effect on next app start.
    private final AppConfig config;

    public MainWindow(ParameterService paramSvc, RunRepository runRepo,
                      SpreadsheetService spreadsheetSvc, RunService runSvc,
                      EmailService emailSvc, FeeEarnerRepository feeEarnerRepo,
                      AppConfig config) {
        this.paramSvc       = paramSvc;
        this.runRepo        = runRepo;
        this.spreadsheetSvc = spreadsheetSvc;
        this.runSvc         = runSvc;
        this.emailSvc       = emailSvc;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }
```

with:

```java
    private final EmailService emailSvc;
    private final DeployService deploySvc;
    private final FeeEarnerRepository feeEarnerRepo;
    // Config is loaded at startup; changes via ParameterEditorWindow take effect on next app start.
    private final AppConfig config;

    public MainWindow(ParameterService paramSvc, RunRepository runRepo,
                      SpreadsheetService spreadsheetSvc, RunService runSvc,
                      EmailService emailSvc, DeployService deploySvc,
                      FeeEarnerRepository feeEarnerRepo, AppConfig config) {
        this.paramSvc       = paramSvc;
        this.runRepo        = runRepo;
        this.spreadsheetSvc = spreadsheetSvc;
        this.runSvc         = runSvc;
        this.emailSvc       = emailSvc;
        this.deploySvc      = deploySvc;
        this.feeEarnerRepo  = feeEarnerRepo;
        this.config         = config;
    }
```

- [ ] **Step 2: Replace the `show` method's menu construction**

In the same file, replace the entire body of `show(Stage primaryStage)` from the first `var modifyParams = ...` line through the `var menuBar = new MenuBar(actionsMenu);` line with:

```java
        // ── Parameters ──
        var modifyParams = new MenuItem("Modify Parameters");
        modifyParams.setOnAction(e ->
            new ParameterEditorWindow(paramSvc).show(primaryStage));
        var parametersMenu = new Menu("Parameters", null, modifyParams);

        // ── Runs ──
        var showRuns = new MenuItem("Previous Runs");
        showRuns.setOnAction(e ->
            new PreviousRunsWindow(runRepo).show(primaryStage));
        var runsMenu = new Menu("Runs", null, showRuns);

        // ── Spreadsheet Generation ──
        var generateAll = new MenuItem("Generate All Spreadsheets");
        generateAll.setOnAction(e ->
            new GenerateAllWindow(spreadsheetSvc, runSvc, feeEarnerRepo, config)
                .show(primaryStage));
        var generateSingle = new MenuItem("Generate Single Spreadsheet");
        generateSingle.setOnAction(e ->
            new SingleGenerateWindow(spreadsheetSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));
        var generateAllPast = new MenuItem("Generate All Past Spreadsheets");
        generateAllPast.setOnAction(e ->
            new GenerateAllPastWindow(spreadsheetSvc, runRepo, config)
                .show(primaryStage));
        var generateSinglePast = new MenuItem("Generate Single Past Spreadsheets");
        generateSinglePast.setOnAction(e ->
            new GenerateSinglePastWindow(spreadsheetSvc, runRepo, config)
                .show(primaryStage));
        var generationMenu = new Menu("Spreadsheet Generation", null,
            generateAll, generateSingle, generateAllPast, generateSinglePast);

        // ── Email Send ──
        var emailAll = new MenuItem("Email All Spreadsheets");
        emailAll.setOnAction(e ->
            new EmailAllWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));
        var emailSingle = new MenuItem("Email Single Spreadsheet");
        emailSingle.setOnAction(e ->
            new SingleEmailWindow(emailSvc, runRepo, feeEarnerRepo, config)
                .show(primaryStage));
        var emailMenu = new Menu("Email Send", null, emailAll, emailSingle);

        // ── Sharepoint Deployment ──
        var deployAll = new MenuItem("Deploy All Spreadsheets");
        deployAll.setOnAction(e ->
            new DeployAllWindow(deploySvc, runRepo, config).show(primaryStage));
        var deploySingle = new MenuItem("Deploy Single Spreadsheet");
        deploySingle.setOnAction(e ->
            new SingleDeployWindow(deploySvc, feeEarnerRepo, config).show(primaryStage));
        var sharepointMenu = new Menu("Sharepoint Deployment", null, deployAll, deploySingle);

        var menuBar = new MenuBar(parametersMenu, runsMenu, generationMenu,
            emailMenu, sharepointMenu);
```

- [ ] **Step 3: Add the `DeployService` import**

In the same file, the existing wildcard import `import net.javalover.feeearner.service.*;` already covers `DeployService`. Confirm no change needed. (If imports were explicit, add `import net.javalover.feeearner.service.DeployService;`.)

- [ ] **Step 4: Verify compilation (will fail at the call sites until Task 11)**

Run: `mvn -q -DskipTests compile`
Expected: FAIL — `FxApplication`/`Main` still call the old 7-arg `MainWindow` constructor. This is expected and fixed in Task 11. (Do not commit yet.)

> Proceed directly to Task 11; commit MainWindow + bootstrap together since the constructor signature change spans both.

---

## Task 11: Bootstrap wiring (`FxApplication`, `Main`)

**Files:**
- Modify: `src/main/java/net/javalover/feeearner/FxApplication.java`
- Modify: `src/main/java/net/javalover/feeearner/Main.java`

- [ ] **Step 1: Wire SharePoint + Deploy services in `FxApplication`**

In `src/main/java/net/javalover/feeearner/FxApplication.java`:

(a) Add imports after the existing `import net.javalover.feeearner.service.*;` line:

```java
import net.javalover.feeearner.sharepoint.SharePointService;
```

(b) Change the archive repo construction to pass the `DataSource`. Replace:

```java
            var archiveRepo = new ArchiveRepository();
```

with:

```java
            var archiveRepo = new ArchiveRepository(ds);
```

(c) Add the new services. Replace:

```java
            var mailSender = new MailSender(config);
            var emailSvc = new EmailService(mailSender, runRepo);
            var paramSvc = new ParameterService(paramRepo);

            new MainWindow(paramSvc, runRepo, spreadsheetSvc, runSvc,
                    emailSvc, feeEarnerRepo, config)
                    .show(primaryStage);
```

with:

```java
            var mailSender = new MailSender(config);
            var emailSvc = new EmailService(mailSender, runRepo);
            var sharePointSvc = new SharePointService();
            var deploySvc = new DeployService(sharePointSvc, runRepo);
            var paramSvc = new ParameterService(paramRepo);

            new MainWindow(paramSvc, runRepo, spreadsheetSvc, runSvc,
                    emailSvc, deploySvc, feeEarnerRepo, config)
                    .show(primaryStage);
```

- [ ] **Step 2: Update `Main` archive repo construction**

In `src/main/java/net/javalover/feeearner/Main.java`, replace:

```java
            var archiveRepo = new ArchiveRepository();
```

with:

```java
            var archiveRepo = new ArchiveRepository(ds);
```

> `Main` (the CLI bulk run) does not construct `MainWindow`, so no other change is needed there. SharePoint/Deploy are GUI-driven; the CLI continues to do live bulk generation only.

- [ ] **Step 3: Verify the whole project compiles**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Run the full unit-test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS — all unit tests pass (integration `*IT` excluded).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/javalover/feeearner/ui/MainWindow.java \
        src/main/java/net/javalover/feeearner/FxApplication.java \
        src/main/java/net/javalover/feeearner/Main.java
git commit -m "feat: five-menu MainWindow; wire SharePoint + Deploy services"
```

---

## Task 12: Final verification & docs

**Files:**
- Modify: `CLAUDE.md` (optional doc touch-up)

- [ ] **Step 1: Build the uberjar to confirm shading still works**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS; `target/FeeEarnerReport-1.0-SNAPSHOT.jar` produced. Jackson is shaded in.

- [ ] **Step 2: Confirm Jackson's service files survived shading (no duplicate-binding warnings)**

Run: `unzip -l target/FeeEarnerReport-1.0-SNAPSHOT.jar | grep -i jackson | head`
Expected: Jackson classes present in the jar.

- [ ] **Step 3: Update `CLAUDE.md` notes (architecture deltas)**

In `CLAUDE.md`, under the `service/` bullet, append a sentence noting the new capabilities. Add after the existing `service/` paragraph:

```markdown
- **SharePoint/Deploy**: `sharepoint/SharePointService` issues Microsoft Graph REST calls (JDK `HttpClient` + Jackson) — client-credentials token, site/drive resolution, simple upload (≤4 MB) or resumable upload session (>4 MB, 10 MB chunks). `service/DeployService` mirrors `EmailService`: it uploads each fee earner's most-recent stored blob under the SharePoint name `Fee Earner_<usrID>_<name>_VIC_task_report.xlsx`, resolving the token/site/drive once per batch. Past-run regeneration (`SpreadsheetService.generateFromArchive`/`generateAllFromArchive`) rebuilds a workbook purely from the five archive tables of a chosen `run_id` and stores it as the fee earner's most-recent `FeeEarnersRun` blob (archive tables untouched). Email now goes ONLY to `email_recipients` (the fee earner is never emailed).
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: note SharePoint deploy, past-run regeneration, email recipient change"
```

- [ ] **Step 5 (operational, not code): apply the new params to the DB out of band**

Insert the six SharePoint params into `report.report_param` (`shrpnt_client_id`, `shrpnt_tenant_id`, `shrpnt_secret_id`, `shrpnt_host`, `shrpnt_site_name`, `shrpnt_target_dir`) and ensure `email_recipients` is populated. The app does not create param rows. This is a deployment step, not part of the build.

---

## Notes for the implementer

- **Test conventions:** services are unit-tested by subclassing the concrete repository/service and overriding methods (see `SpreadsheetServiceTest`, `EmailServiceTest`). `FakeJdbc` (dynamic JDBC proxies) tests transaction/connection behavior. Keep new tests in that style.
- **`FakeJdbc.executeUpdate` returns 0**, so any code path asserting "rows updated > 0" will throw against the fake — handle in the test as shown in Task 5.
- **Resumable upload** reads the whole blob into memory (it already is a `byte[]` from the DB) and slices it; no temp file. 10 MB is a 320 KB multiple as Graph requires.
- **Native build:** `-Pnative` may need a GraalVM hint for `HttpClient` TLS; out of scope for the JVM uberjar and not gated here.
```
