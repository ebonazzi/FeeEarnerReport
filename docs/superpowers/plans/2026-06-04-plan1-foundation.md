# Fee Earner Report — Plan 1: Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the Maven project structure, SQL database objects, all model records, configuration loading, logging initialisation, and all JDBC repositories — producing a fully testable data layer with no business logic.

**Architecture:** Single Maven module (Approach A). All classes under `net.javalover.feeearner`. Models are immutable Java 25 records. Repositories receive a `DataSource` and use try-with-resources. Tests include unit tests for models/config and integration tests (tagged `integration`) against the local SQL Server instance.

**Tech Stack:** Java 25 (Liberica Full JDK), Maven, mssql-jdbc 12.8.1, HikariCP 6.2.1, rainbowgum-jdk 0.8.0, SLF4J 2.0.16, JUnit 5.11.4

---

## File Map

```
pom.xml                                                          ← update
src/main/resources/sql/01_tables.sql                            ← create
src/main/resources/sql/02_partition_functions.sql               ← create
src/main/resources/sql/03_partition_schemes.sql                 ← create
src/main/resources/sql/04_apply_partitioning.sql                ← create
src/main/java/net/javalover/feeearner/model/BaseRow.java        ← create
src/main/java/net/javalover/feeearner/model/FeeEarner.java      ← create
src/main/java/net/javalover/feeearner/model/RunInfo.java        ← create
src/main/java/net/javalover/feeearner/model/FeeEarnerRun.java   ← create
src/main/java/net/javalover/feeearner/model/AppParam.java       ← create
src/main/java/net/javalover/feeearner/model/FailedEntry.java    ← create
src/main/java/net/javalover/feeearner/model/ProgressTracker.java← create
src/main/java/net/javalover/feeearner/model/FullTaskRow.java    ← create
src/main/java/net/javalover/feeearner/model/LimitationRow.java  ← create
src/main/java/net/javalover/feeearner/model/AgedRow.java        ← create
src/main/java/net/javalover/feeearner/model/DuplicateRow.java   ← create
src/main/java/net/javalover/feeearner/model/HighVolumeRow.java  ← create
src/main/java/net/javalover/feeearner/config/AppConfig.java     ← create
src/main/java/net/javalover/feeearner/config/CredentialLoader.java ← create
src/main/java/net/javalover/feeearner/config/DbCredentials.java ← create
src/main/java/net/javalover/feeearner/logging/LoggingInitialiser.java ← create
src/main/java/net/javalover/feeearner/repository/ParamRepository.java     ← create
src/main/java/net/javalover/feeearner/repository/FeeEarnerRepository.java ← create
src/main/java/net/javalover/feeearner/repository/RunRepository.java       ← create
src/main/java/net/javalover/feeearner/repository/WorksheetRepository.java ← create
src/main/java/net/javalover/feeearner/repository/ArchiveRepository.java   ← create
src/main/java/net/javalover/feeearner/service/ValidationResult.java       ← create
src/main/java/net/javalover/feeearner/service/ParameterService.java       ← create
src/test/java/net/javalover/feeearner/TestDataSourceFactory.java          ← create
src/test/java/net/javalover/feeearner/model/ModelTest.java                ← create
src/test/java/net/javalover/feeearner/config/CredentialLoaderTest.java    ← create
src/test/java/net/javalover/feeearner/repository/ParamRepositoryIT.java   ← create
src/test/java/net/javalover/feeearner/repository/FeeEarnerRepositoryIT.java ← create
src/test/java/net/javalover/feeearner/service/ParameterServiceTest.java   ← create
src/test/resources/test-credentials.properties                             ← create (not committed)
```

---

## Task 1: Update pom.xml

**Files:** Modify `pom.xml`

- [ ] **Step 1: Replace pom.xml with full dependency set**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>net.javalover.feeearner</groupId>
    <artifactId>FeeEarnerReport</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>25</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <javafx.version>25</javafx.version>
        <poi.version>5.3.0</poi.version>
        <hikari.version>6.2.1</hikari.version>
        <mssql.version>12.8.1.jre11</mssql.version>
        <rainbowgum.version>0.8.0</rainbowgum.version>
        <slf4j.version>2.0.16</slf4j.version>
        <mail.api.version>2.1.3</mail.api.version>
        <angus.version>2.0.3</angus.version>
        <junit.version>5.11.4</junit.version>
        <main.class>net.javalover.feeearner.FxApplication</main.class>
    </properties>

    <dependencies>
        <!-- JavaFX (provided by Liberica Full JDK at runtime) -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-fxml</artifactId>
            <version>${javafx.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- Apache POI -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>${poi.version}</version>
        </dependency>

        <!-- SQL Server JDBC -->
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <version>${mssql.version}</version>
        </dependency>

        <!-- HikariCP -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <version>${hikari.version}</version>
        </dependency>

        <!-- Rainbow Gum logging -->
        <dependency>
            <groupId>io.jstach.rainbowgum</groupId>
            <artifactId>rainbowgum-jdk</artifactId>
            <version>${rainbowgum.version}</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>${slf4j.version}</version>
        </dependency>

        <!-- Email -->
        <dependency>
            <groupId>jakarta.mail</groupId>
            <artifactId>jakarta.mail-api</artifactId>
            <version>${mail.api.version}</version>
        </dependency>
        <dependency>
            <groupId>org.eclipse.angus</groupId>
            <artifactId>angus-mail</artifactId>
            <version>${angus.version}</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
                <configuration>
                    <release>25</release>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
                <configuration>
                    <!-- skip integration tests by default; run with -Pintegration -->
                    <excludedGroups>integration</excludedGroups>
                </configuration>
            </plugin>

            <!-- Uberjar profile (default) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <artifactSet>
                                <!-- JavaFX provided by Liberica JDK at runtime -->
                                <excludes>
                                    <exclude>org.openjfx:*</exclude>
                                </excludes>
                            </artifactSet>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>${main.class}</mainClass>
                                </transformer>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <!-- Run integration tests: mvn test -Pintegration -->
        <profile>
            <id>integration</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <configuration>
                            <excludedGroups/>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>

        <!-- Native image: mvn package -Pnative (requires GraalVM + GRAALVM_HOME) -->
        <profile>
            <id>native</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>com.gluonhq</groupId>
                        <artifactId>gluonfx-maven-plugin</artifactId>
                        <version>1.0.22</version>
                        <configuration>
                            <mainClass>${main.class}</mainClass>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 2: Verify the project compiles with no sources yet**

```bash
cd /dsk01/MauriceBlackburn/IdeaProjects/FeeEarnerReport
mvn compile -q
```

Expected: BUILD SUCCESS (no Java sources yet, compiles empty project)

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: configure Maven dependencies and build profiles for Java 25"
```

---

## Task 2: SQL Table Scripts

**Files:** Create `src/main/resources/sql/01_tables.sql`

- [ ] **Step 1: Create directory and write 01_tables.sql**

```bash
mkdir -p /dsk01/MauriceBlackburn/IdeaProjects/FeeEarnerReport/src/main/resources/sql
```

Write `src/main/resources/sql/01_tables.sql`:

```sql
USE MCMBLIVE;
GO

-- Parameter table (may already exist; skip if present)
IF OBJECT_ID('report.report_param', 'U') IS NULL
BEGIN
    CREATE TABLE report.report_param
    (
        param_id        smallint        IDENTITY(1,1)   NOT NULL,
        parameter_name  nvarchar(128)                   NOT NULL,
        parameter_value nvarchar(4000)                  NOT NULL,
        active          bit                             NOT NULL,
        CONSTRAINT etl_param_pk PRIMARY KEY (param_id)
    );
END
GO

-- Run tracking table
IF OBJECT_ID('report.spreadsheet_run', 'U') IS NULL
BEGIN
    CREATE TABLE report.spreadsheet_run
    (
        run_id      int         IDENTITY(1,1)               NOT NULL,
        day_run     DATE        DEFAULT GETDATE()            NOT NULL,
        started_at  DATETIME2   DEFAULT GETDATE()            NOT NULL,
        finished_at DATETIME2                                NULL,
        CONSTRAINT spreadsheet_run_pkey PRIMARY KEY (run_id)
    );
END
GO

-- Fee earner run tracking (stores xlsx blob)
IF OBJECT_ID('report.FeeEarnersRun', 'U') IS NULL
BEGIN
    CREATE TABLE report.FeeEarnersRun
    (
        run_id              int             NOT NULL,
        day_run             DATE            DEFAULT GETDATE()    NOT NULL,
        usrID               int             NOT NULL,
        [Fee Earner]        nvarchar(50)    COLLATE Latin1_General_CI_AS NOT NULL,
        usrEmail            uEmail          NOT NULL,
        excel_filename      nvarchar(255)   NULL,
        excel_spreadsheet   VARBINARY(MAX)  NULL,
        stored_at           DATETIME2       DEFAULT GETDATE()    NULL,
        CONSTRAINT FeeEarnersRun_pkey PRIMARY KEY (run_id, usrID)
    );
END
GO

-- Archive: Full task report
IF OBJECT_ID('report.full_task_archive', 'U') IS NULL
BEGIN
    CREATE TABLE report.full_task_archive
    (
        day_run                     DATE            NOT NULL,
        run_id                      int             NOT NULL,
        usrID                       int             NOT NULL,
        row_number                  int             NOT NULL,
        [Report Date]               date            NULL,
        [Matter Number]             nvarchar(20)    NOT NULL,
        [Matter Name/Description]   nvarchar(255)   NULL,
        Department                  nvarchar(1000)  NULL,
        [Practice Code]             uCodeLookup     NULL,
        [Office Name]               nvarchar(50)    NULL,
        Jurisdiction                uCodeLookup     NULL,
        [Fee Earner]                nvarchar(50)    NULL,
        [Legal Assistant]           nvarchar(50)    NULL,
        [Supervising Fee Earner]    nvarchar(50)    NULL,
        [Task Description]          nvarchar(300)   NULL,
        [Task Type]                 nvarchar(15)    NULL,
        [Task Notes]                ntext           NULL,
        [Task Owner]                nvarchar(50)    NULL,
        [Task Created Date]         uCreated        NULL,
        [Task Due Date]             datetime        NULL,
        [Task Complete]             bit             NULL,
        [Type]                      varchar(6)      NULL
    );
END
GO

-- Archive: Limitation report
IF OBJECT_ID('report.limitation_archive', 'U') IS NULL
BEGIN
    CREATE TABLE report.limitation_archive
    (
        day_run                     DATE            NOT NULL,
        run_id                      int             NOT NULL,
        usrID                       int             NOT NULL,
        row_number                  int             NOT NULL,
        [Report Date]               date            NULL,
        [Matter Number]             nvarchar(20)    NOT NULL,
        [Matter Name/Description]   nvarchar(255)   NULL,
        Department                  nvarchar(1000)  NULL,
        [Practice Code]             uCodeLookup     NULL,
        [Office Name]               nvarchar(50)    NULL,
        Jurisdiction                uCodeLookup     NULL,
        [Fee Earner]                nvarchar(50)    NULL,
        [Legal Assistant]           nvarchar(50)    NULL,
        [Supervising Fee Earner]    nvarchar(50)    NULL,
        [Task Description]          nvarchar(300)   NOT NULL,
        [Task Type]                 nvarchar(15)    NULL,
        [Task Notes]                ntext           NULL,
        [Task Owner]                nvarchar(50)    NULL,
        [Task Created Date]         uCreated        NULL,
        [Task Due Date]             datetime        NULL,
        [Task Complete]             bit             NULL,
        [Type]                      varchar(6)      NULL,
        [Key Words]                 nvarchar(4000)  NULL
    );
END
GO

-- Archive: Aged report
IF OBJECT_ID('report.aged_archive', 'U') IS NULL
BEGIN
    CREATE TABLE report.aged_archive
    (
        day_run                     DATE            NOT NULL,
        run_id                      int             NOT NULL,
        usrID                       int             NOT NULL,
        row_number                  int             NOT NULL,
        [Report Date]               date            NULL,
        [Matter Number]             nvarchar(20)    NOT NULL,
        [Matter Name/Description]   nvarchar(255)   NULL,
        Department                  nvarchar(1000)  NULL,
        [Practice Code]             uCodeLookup     NULL,
        [Office Name]               nvarchar(50)    NULL,
        Jurisdiction                uCodeLookup     NULL,
        [Fee Earner]                nvarchar(50)    NULL,
        [Legal Assistant]           nvarchar(50)    NULL,
        [Supervising Fee Earner]    nvarchar(50)    NULL,
        [Task Description]          nvarchar(300)   NULL,
        [Task Type]                 nvarchar(15)    NULL,
        [Task Notes]                ntext           NULL,
        [Task Owner]                nvarchar(50)    NULL,
        [Task Created Date]         uCreated        NULL,
        [Task Due Date]             datetime        NULL,
        [Task Complete]             bit             NULL,
        [Type]                      varchar(6)      NULL
    );
END
GO

-- Archive: Duplicate task report
IF OBJECT_ID('report.duplicate_task_archive', 'U') IS NULL
BEGIN
    CREATE TABLE report.duplicate_task_archive
    (
        day_run                     DATE            NOT NULL,
        run_id                      int             NOT NULL,
        usrID                       int             NOT NULL,
        row_number                  int             NOT NULL,
        [Report Date]               date            NULL,
        [Matter Number]             nvarchar(20)    NOT NULL,
        [Matter Name/Description]   nvarchar(255)   NULL,
        Department                  nvarchar(1000)  NULL,
        [Practice Code]             uCodeLookup     NULL,
        [Office Name]               nvarchar(50)    NULL,
        Jurisdiction                uCodeLookup     NULL,
        [Fee Earner]                nvarchar(50)    NULL,
        [Legal Assistant]           nvarchar(50)    NULL,
        [Supervising Fee Earner]    nvarchar(50)    NULL,
        [Task Description]          nvarchar(300)   NULL,
        [Task Type]                 nvarchar(15)    NULL,
        [Task Notes]                ntext           NULL,
        [Task Owner]                nvarchar(50)    NULL,
        [Task Created Date]         uCreated        NULL,
        [Task Due Date]             datetime        NULL,
        [Task Complete]             bit             NULL,
        [Type]                      varchar(6)      NULL,
        Duplicate                   varchar(3)      NULL
    );
END
GO

-- Archive: High volume report
IF OBJECT_ID('report.high_volume_archive', 'U') IS NULL
BEGIN
    CREATE TABLE report.high_volume_archive
    (
        day_run                     DATE            NOT NULL,
        run_id                      int             NOT NULL,
        usrID                       int             NOT NULL,
        row_number                  int             NOT NULL,
        [Report Date]               date            NULL,
        [Matter Number]             nvarchar(20)    NOT NULL,
        [Matter Name/Description]   nvarchar(255)   NULL,
        Department                  nvarchar(1000)  NULL,
        [Practice Code]             uCodeLookup     NULL,
        [Office Name]               nvarchar(50)    NULL,
        Jurisdiction                uCodeLookup     NULL,
        [Fee Earner]                nvarchar(50)    NULL,
        [Legal Assistant]           nvarchar(50)    NULL,
        [Supervising Fee Earner]    nvarchar(50)    NULL,
        [Task Description]          nvarchar(300)   NULL,
        [Task Type]                 nvarchar(15)    NULL,
        [Task Notes]                ntext           NULL,
        [Task Owner]                nvarchar(50)    NULL,
        [Task Created Date]         uCreated        NULL,
        [Task Due Date]             datetime        NULL,
        [Task Complete]             bit             NULL,
        [Type]                      varchar(6)      NULL,
        [Matter Row Count]          int             NULL
    );
END
GO
```

- [ ] **Step 2: Execute 01_tables.sql against local SQL Server**

```bash
sqlcmd -S localhost -d MCMBLIVE -U <user> -P <password> \
  -i src/main/resources/sql/01_tables.sql
```

Expected: Commands complete successfully. All tables created.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/sql/01_tables.sql
git commit -m "feat: add SQL table creation script for all archive and tracking tables"
```

---

## Task 3: SQL Partition Scripts

**Files:** Create `sql/02_partition_functions.sql`, `sql/03_partition_schemes.sql`, `sql/04_apply_partitioning.sql`

- [ ] **Step 1: Write 02_partition_functions.sql**

Write `src/main/resources/sql/02_partition_functions.sql`:

```sql
USE MCMBLIVE;
GO

-- Weekly date boundaries 2026-06-04 through 2028-06-08
-- One partition function per archive table

CREATE PARTITION FUNCTION pf_full_task_archive_weekly (DATE)
AS RANGE LEFT FOR VALUES (
    '2026-06-04','2026-06-11','2026-06-18','2026-06-25',
    '2026-07-02','2026-07-09','2026-07-16','2026-07-23','2026-07-30',
    '2026-08-06','2026-08-13','2026-08-20','2026-08-27',
    '2026-09-03','2026-09-10','2026-09-17','2026-09-24',
    '2026-10-01','2026-10-08','2026-10-15','2026-10-22','2026-10-29',
    '2026-11-05','2026-11-12','2026-11-19','2026-11-26',
    '2026-12-03','2026-12-10','2026-12-17','2026-12-24','2026-12-31',
    '2027-01-07','2027-01-14','2027-01-21','2027-01-28',
    '2027-02-04','2027-02-11','2027-02-18','2027-02-25',
    '2027-03-04','2027-03-11','2027-03-18','2027-03-25',
    '2027-04-01','2027-04-08','2027-04-15','2027-04-22','2027-04-29',
    '2027-05-06','2027-05-13','2027-05-20','2027-05-27',
    '2027-06-03','2027-06-10','2027-06-17','2027-06-24',
    '2027-07-01','2027-07-08','2027-07-15','2027-07-22','2027-07-29',
    '2027-08-05','2027-08-12','2027-08-19','2027-08-26',
    '2027-09-02','2027-09-09','2027-09-16','2027-09-23','2027-09-30',
    '2027-10-07','2027-10-14','2027-10-21','2027-10-28',
    '2027-11-04','2027-11-11','2027-11-18','2027-11-25',
    '2027-12-02','2027-12-09','2027-12-16','2027-12-23','2027-12-30',
    '2028-01-06','2028-01-13','2028-01-20','2028-01-27',
    '2028-02-03','2028-02-10','2028-02-17','2028-02-24',
    '2028-03-02','2028-03-09','2028-03-16','2028-03-23','2028-03-30',
    '2028-04-06','2028-04-13','2028-04-20','2028-04-27',
    '2028-05-04','2028-05-11','2028-05-18','2028-05-25',
    '2028-06-01','2028-06-08'
);
GO

CREATE PARTITION FUNCTION pf_limitation_archive_weekly (DATE)
AS RANGE LEFT FOR VALUES (
    '2026-06-04','2026-06-11','2026-06-18','2026-06-25',
    '2026-07-02','2026-07-09','2026-07-16','2026-07-23','2026-07-30',
    '2026-08-06','2026-08-13','2026-08-20','2026-08-27',
    '2026-09-03','2026-09-10','2026-09-17','2026-09-24',
    '2026-10-01','2026-10-08','2026-10-15','2026-10-22','2026-10-29',
    '2026-11-05','2026-11-12','2026-11-19','2026-11-26',
    '2026-12-03','2026-12-10','2026-12-17','2026-12-24','2026-12-31',
    '2027-01-07','2027-01-14','2027-01-21','2027-01-28',
    '2027-02-04','2027-02-11','2027-02-18','2027-02-25',
    '2027-03-04','2027-03-11','2027-03-18','2027-03-25',
    '2027-04-01','2027-04-08','2027-04-15','2027-04-22','2027-04-29',
    '2027-05-06','2027-05-13','2027-05-20','2027-05-27',
    '2027-06-03','2027-06-10','2027-06-17','2027-06-24',
    '2027-07-01','2027-07-08','2027-07-15','2027-07-22','2027-07-29',
    '2027-08-05','2027-08-12','2027-08-19','2027-08-26',
    '2027-09-02','2027-09-09','2027-09-16','2027-09-23','2027-09-30',
    '2027-10-07','2027-10-14','2027-10-21','2027-10-28',
    '2027-11-04','2027-11-11','2027-11-18','2027-11-25',
    '2027-12-02','2027-12-09','2027-12-16','2027-12-23','2027-12-30',
    '2028-01-06','2028-01-13','2028-01-20','2028-01-27',
    '2028-02-03','2028-02-10','2028-02-17','2028-02-24',
    '2028-03-02','2028-03-09','2028-03-16','2028-03-23','2028-03-30',
    '2028-04-06','2028-04-13','2028-04-20','2028-04-27',
    '2028-05-04','2028-05-11','2028-05-18','2028-05-25',
    '2028-06-01','2028-06-08'
);
GO

CREATE PARTITION FUNCTION pf_aged_archive_weekly (DATE)
AS RANGE LEFT FOR VALUES (
    '2026-06-04','2026-06-11','2026-06-18','2026-06-25',
    '2026-07-02','2026-07-09','2026-07-16','2026-07-23','2026-07-30',
    '2026-08-06','2026-08-13','2026-08-20','2026-08-27',
    '2026-09-03','2026-09-10','2026-09-17','2026-09-24',
    '2026-10-01','2026-10-08','2026-10-15','2026-10-22','2026-10-29',
    '2026-11-05','2026-11-12','2026-11-19','2026-11-26',
    '2026-12-03','2026-12-10','2026-12-17','2026-12-24','2026-12-31',
    '2027-01-07','2027-01-14','2027-01-21','2027-01-28',
    '2027-02-04','2027-02-11','2027-02-18','2027-02-25',
    '2027-03-04','2027-03-11','2027-03-18','2027-03-25',
    '2027-04-01','2027-04-08','2027-04-15','2027-04-22','2027-04-29',
    '2027-05-06','2027-05-13','2027-05-20','2027-05-27',
    '2027-06-03','2027-06-10','2027-06-17','2027-06-24',
    '2027-07-01','2027-07-08','2027-07-15','2027-07-22','2027-07-29',
    '2027-08-05','2027-08-12','2027-08-19','2027-08-26',
    '2027-09-02','2027-09-09','2027-09-16','2027-09-23','2027-09-30',
    '2027-10-07','2027-10-14','2027-10-21','2027-10-28',
    '2027-11-04','2027-11-11','2027-11-18','2027-11-25',
    '2027-12-02','2027-12-09','2027-12-16','2027-12-23','2027-12-30',
    '2028-01-06','2028-01-13','2028-01-20','2028-01-27',
    '2028-02-03','2028-02-10','2028-02-17','2028-02-24',
    '2028-03-02','2028-03-09','2028-03-16','2028-03-23','2028-03-30',
    '2028-04-06','2028-04-13','2028-04-20','2028-04-27',
    '2028-05-04','2028-05-11','2028-05-18','2028-05-25',
    '2028-06-01','2028-06-08'
);
GO

CREATE PARTITION FUNCTION pf_duplicate_task_archive_weekly (DATE)
AS RANGE LEFT FOR VALUES (
    '2026-06-04','2026-06-11','2026-06-18','2026-06-25',
    '2026-07-02','2026-07-09','2026-07-16','2026-07-23','2026-07-30',
    '2026-08-06','2026-08-13','2026-08-20','2026-08-27',
    '2026-09-03','2026-09-10','2026-09-17','2026-09-24',
    '2026-10-01','2026-10-08','2026-10-15','2026-10-22','2026-10-29',
    '2026-11-05','2026-11-12','2026-11-19','2026-11-26',
    '2026-12-03','2026-12-10','2026-12-17','2026-12-24','2026-12-31',
    '2027-01-07','2027-01-14','2027-01-21','2027-01-28',
    '2027-02-04','2027-02-11','2027-02-18','2027-02-25',
    '2027-03-04','2027-03-11','2027-03-18','2027-03-25',
    '2027-04-01','2027-04-08','2027-04-15','2027-04-22','2027-04-29',
    '2027-05-06','2027-05-13','2027-05-20','2027-05-27',
    '2027-06-03','2027-06-10','2027-06-17','2027-06-24',
    '2027-07-01','2027-07-08','2027-07-15','2027-07-22','2027-07-29',
    '2027-08-05','2027-08-12','2027-08-19','2027-08-26',
    '2027-09-02','2027-09-09','2027-09-16','2027-09-23','2027-09-30',
    '2027-10-07','2027-10-14','2027-10-21','2027-10-28',
    '2027-11-04','2027-11-11','2027-11-18','2027-11-25',
    '2027-12-02','2027-12-09','2027-12-16','2027-12-23','2027-12-30',
    '2028-01-06','2028-01-13','2028-01-20','2028-01-27',
    '2028-02-03','2028-02-10','2028-02-17','2028-02-24',
    '2028-03-02','2028-03-09','2028-03-16','2028-03-23','2028-03-30',
    '2028-04-06','2028-04-13','2028-04-20','2028-04-27',
    '2028-05-04','2028-05-11','2028-05-18','2028-05-25',
    '2028-06-01','2028-06-08'
);
GO

CREATE PARTITION FUNCTION pf_high_volume_archive_weekly (DATE)
AS RANGE LEFT FOR VALUES (
    '2026-06-04','2026-06-11','2026-06-18','2026-06-25',
    '2026-07-02','2026-07-09','2026-07-16','2026-07-23','2026-07-30',
    '2026-08-06','2026-08-13','2026-08-20','2026-08-27',
    '2026-09-03','2026-09-10','2026-09-17','2026-09-24',
    '2026-10-01','2026-10-08','2026-10-15','2026-10-22','2026-10-29',
    '2026-11-05','2026-11-12','2026-11-19','2026-11-26',
    '2026-12-03','2026-12-10','2026-12-17','2026-12-24','2026-12-31',
    '2027-01-07','2027-01-14','2027-01-21','2027-01-28',
    '2027-02-04','2027-02-11','2027-02-18','2027-02-25',
    '2027-03-04','2027-03-11','2027-03-18','2027-03-25',
    '2027-04-01','2027-04-08','2027-04-15','2027-04-22','2027-04-29',
    '2027-05-06','2027-05-13','2027-05-20','2027-05-27',
    '2027-06-03','2027-06-10','2027-06-17','2027-06-24',
    '2027-07-01','2027-07-08','2027-07-15','2027-07-22','2027-07-29',
    '2027-08-05','2027-08-12','2027-08-19','2027-08-26',
    '2027-09-02','2027-09-09','2027-09-16','2027-09-23','2027-09-30',
    '2027-10-07','2027-10-14','2027-10-21','2027-10-28',
    '2027-11-04','2027-11-11','2027-11-18','2027-11-25',
    '2027-12-02','2027-12-09','2027-12-16','2027-12-23','2027-12-30',
    '2028-01-06','2028-01-13','2028-01-20','2028-01-27',
    '2028-02-03','2028-02-10','2028-02-17','2028-02-24',
    '2028-03-02','2028-03-09','2028-03-16','2028-03-23','2028-03-30',
    '2028-04-06','2028-04-13','2028-04-20','2028-04-27',
    '2028-05-04','2028-05-11','2028-05-18','2028-05-25',
    '2028-06-01','2028-06-08'
);
GO
```

- [ ] **Step 2: Write 03_partition_schemes.sql**

Write `src/main/resources/sql/03_partition_schemes.sql`:

```sql
USE MCMBLIVE;
GO

CREATE PARTITION SCHEME ps_full_task_archive_weekly
AS PARTITION pf_full_task_archive_weekly
ALL TO ([PRIMARY]);
GO

CREATE PARTITION SCHEME ps_limitation_archive_weekly
AS PARTITION pf_limitation_archive_weekly
ALL TO ([PRIMARY]);
GO

CREATE PARTITION SCHEME ps_aged_archive_weekly
AS PARTITION pf_aged_archive_weekly
ALL TO ([PRIMARY]);
GO

CREATE PARTITION SCHEME ps_duplicate_task_archive_weekly
AS PARTITION pf_duplicate_task_archive_weekly
ALL TO ([PRIMARY]);
GO

CREATE PARTITION SCHEME ps_high_volume_archive_weekly
AS PARTITION pf_high_volume_archive_weekly
ALL TO ([PRIMARY]);
GO
```

- [ ] **Step 3: Write 04_apply_partitioning.sql**

Write `src/main/resources/sql/04_apply_partitioning.sql`:

```sql
USE MCMBLIVE;
GO

-- Apply partitioning to each archive table via a unique clustered index on (day_run, run_id, usrID, row_number).
-- day_run must be the leftmost column to align with the partition function.

CREATE UNIQUE CLUSTERED INDEX CIX_full_task_archive
ON report.full_task_archive (day_run, run_id, usrID, row_number)
ON ps_full_task_archive_weekly(day_run);
GO

CREATE UNIQUE CLUSTERED INDEX CIX_limitation_archive
ON report.limitation_archive (day_run, run_id, usrID, row_number)
ON ps_limitation_archive_weekly(day_run);
GO

CREATE UNIQUE CLUSTERED INDEX CIX_aged_archive
ON report.aged_archive (day_run, run_id, usrID, row_number)
ON ps_aged_archive_weekly(day_run);
GO

CREATE UNIQUE CLUSTERED INDEX CIX_duplicate_task_archive
ON report.duplicate_task_archive (day_run, run_id, usrID, row_number)
ON ps_duplicate_task_archive_weekly(day_run);
GO

CREATE UNIQUE CLUSTERED INDEX CIX_high_volume_archive
ON report.high_volume_archive (day_run, run_id, usrID, row_number)
ON ps_high_volume_archive_weekly(day_run);
GO
```

- [ ] **Step 4: Execute scripts 02, 03, 04 in order against local SQL Server**

```bash
sqlcmd -S localhost -d MCMBLIVE -U <user> -P <password> \
  -i src/main/resources/sql/02_partition_functions.sql

sqlcmd -S localhost -d MCMBLIVE -U <user> -P <password> \
  -i src/main/resources/sql/03_partition_schemes.sql

sqlcmd -S localhost -d MCMBLIVE -U <user> -P <password> \
  -i src/main/resources/sql/04_apply_partitioning.sql
```

Expected: All commands complete successfully.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/sql/
git commit -m "feat: add SQL partition functions, schemes, and clustered indexes for archive tables"
```

---

## Task 4: Model Classes

**Files:** Create all files under `src/main/java/net/javalover/feeearner/model/`

- [ ] **Step 1: Write failing model test**

Create `src/test/java/net/javalover/feeearner/model/ModelTest.java`:

```java
package net.javalover.feeearner.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void feeEarnerRecord() {
        var fe = new FeeEarner(100, "John Smith", "j.smith@example.com", true, "Matter");
        assertEquals(100, fe.usrID());
        assertEquals("John Smith", fe.feeEarner());
        assertEquals("Matter", fe.type());
    }

    @Test
    void appParamRecord() {
        var p = new AppParam(1, "log_dir", "/var/log", true);
        assertEquals("log_dir", p.name());
        assertEquals("/var/log", p.value());
        assertTrue(p.active());
    }

    @Test
    void fullTaskRowImplementsBaseRow() {
        var row = new FullTaskRow(
            "Matter", LocalDate.now(), "MAT-001", "Test Matter",
            "Dept", "299", "Melbourne", "VIC",
            "John Smith", "Jane Doe", "Bob Jones",
            "Task desc", "General", "Notes", "Owner",
            LocalDateTime.now(), LocalDateTime.now()
        );
        assertInstanceOf(BaseRow.class, row);
        assertEquals("Matter", row.type());
        assertEquals("MAT-001", row.matterNumber());
    }

    @Test
    void limitationRowHasKeyWords() {
        var row = new LimitationRow(
            "Enquiry", LocalDate.now(), "ENQ-001", "Test Enquiry",
            "Dept", "216", "Sydney", "NSW",
            "Fee Earner", null, null,
            "Task", "Document", null, "Owner",
            LocalDateTime.now(), null,
            "Limitation, Last Day"
        );
        assertInstanceOf(BaseRow.class, row);
        assertEquals("Limitation, Last Day", row.keyWords());
    }

    @Test
    void highVolumeRowHasMatterRowCount() {
        var row = new HighVolumeRow(
            "Matter", LocalDate.now(), "MAT-002", "High Volume Matter",
            "Dept", "299", "Melbourne", "VIC",
            "Fee Earner", null, null,
            "Task", "General", null, "Owner",
            LocalDateTime.now(), null,
            75
        );
        assertEquals(75, row.matterRowCount());
    }

    @Test
    void failedEntryRecord() {
        var fe = new FailedEntry(123, "John Smith", "DB timeout");
        assertEquals(123, fe.usrID());
        assertEquals("DB timeout", fe.errorMessage());
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure (classes don't exist yet)**

```bash
mvn test -pl . 2>&1 | tail -20
```

Expected: Compilation error — model classes not found.

- [ ] **Step 3: Create BaseRow.java**

```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public sealed interface BaseRow
        permits FullTaskRow, LimitationRow, AgedRow, DuplicateRow, HighVolumeRow {
    String type();
    LocalDate reportDate();
    String matterNumber();
    String matterNameDescription();
    String department();
    String practiceCode();
    String officeName();
    String jurisdiction();
    String feeEarner();
    String legalAssistant();
    String supervisingFeeEarner();
    String taskDescription();
    String taskType();
    String taskNotes();
    String taskOwner();
    LocalDateTime taskCreatedDate();
    LocalDateTime taskDueDate();
}
```

- [ ] **Step 4: Create the five row record classes**

`FullTaskRow.java`:
```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FullTaskRow(
    String type, LocalDate reportDate, String matterNumber,
    String matterNameDescription, String department, String practiceCode,
    String officeName, String jurisdiction, String feeEarner,
    String legalAssistant, String supervisingFeeEarner,
    String taskDescription, String taskType, String taskNotes,
    String taskOwner, LocalDateTime taskCreatedDate, LocalDateTime taskDueDate
) implements BaseRow {}
```

`LimitationRow.java`:
```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LimitationRow(
    String type, LocalDate reportDate, String matterNumber,
    String matterNameDescription, String department, String practiceCode,
    String officeName, String jurisdiction, String feeEarner,
    String legalAssistant, String supervisingFeeEarner,
    String taskDescription, String taskType, String taskNotes,
    String taskOwner, LocalDateTime taskCreatedDate, LocalDateTime taskDueDate,
    String keyWords
) implements BaseRow {}
```

`AgedRow.java`:
```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AgedRow(
    String type, LocalDate reportDate, String matterNumber,
    String matterNameDescription, String department, String practiceCode,
    String officeName, String jurisdiction, String feeEarner,
    String legalAssistant, String supervisingFeeEarner,
    String taskDescription, String taskType, String taskNotes,
    String taskOwner, LocalDateTime taskCreatedDate, LocalDateTime taskDueDate
) implements BaseRow {}
```

`DuplicateRow.java`:
```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DuplicateRow(
    String type, LocalDate reportDate, String matterNumber,
    String matterNameDescription, String department, String practiceCode,
    String officeName, String jurisdiction, String feeEarner,
    String legalAssistant, String supervisingFeeEarner,
    String taskDescription, String taskType, String taskNotes,
    String taskOwner, LocalDateTime taskCreatedDate, LocalDateTime taskDueDate,
    String duplicate
) implements BaseRow {}
```

`HighVolumeRow.java`:
```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HighVolumeRow(
    String type, LocalDate reportDate, String matterNumber,
    String matterNameDescription, String department, String practiceCode,
    String officeName, String jurisdiction, String feeEarner,
    String legalAssistant, String supervisingFeeEarner,
    String taskDescription, String taskType, String taskNotes,
    String taskOwner, LocalDateTime taskCreatedDate, LocalDateTime taskDueDate,
    int matterRowCount
) implements BaseRow {}
```

- [ ] **Step 5: Create remaining model classes**

`FeeEarner.java`:
```java
package net.javalover.feeearner.model;

public record FeeEarner(int usrID, String feeEarner, String usrEmail,
                        boolean usrActive, String type) {}
```

`RunInfo.java`:
```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RunInfo(int runId, LocalDate dayRun,
                      LocalDateTime startedAt, LocalDateTime finishedAt) {}
```

`FeeEarnerRun.java`:
```java
package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeeEarnerRun(int runId, LocalDate dayRun, int usrID,
                           String feeEarner, String usrEmail,
                           String excelFilename, byte[] excelSpreadsheet,
                           LocalDateTime storedAt) {}
```

`AppParam.java`:
```java
package net.javalover.feeearner.model;

public record AppParam(int paramId, String name, String value, boolean active) {}
```

`FailedEntry.java`:
```java
package net.javalover.feeearner.model;

public record FailedEntry(int usrID, String feeEarner, String errorMessage) {}
```

`ProgressTracker.java`:
```java
package net.javalover.feeearner.model;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ProgressTracker {
    public final AtomicInteger total = new AtomicInteger(0);
    public final AtomicInteger completed = new AtomicInteger(0);
    public final AtomicInteger failed = new AtomicInteger(0);
    public final CopyOnWriteArrayList<FailedEntry> failures = new CopyOnWriteArrayList<>();

    public int remaining() {
        return total.get() - completed.get() - failed.get();
    }

    public void recordFailure(int usrID, String feeEarner, String message) {
        failures.add(new FailedEntry(usrID, feeEarner, message));
        failed.incrementAndGet();
    }
}
```

- [ ] **Step 6: Run model tests — expect PASS**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, 5 tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: add all model records and ProgressTracker"
```

---

## Task 5: Config — AppConfig and CredentialLoader

**Files:** Create `config/AppConfig.java`, `config/DbCredentials.java`, `config/CredentialLoader.java`

- [ ] **Step 1: Write failing credential loader test**

Create `src/test/java/net/javalover/feeearner/config/CredentialLoaderTest.java`:

```java
package net.javalover.feeearner.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CredentialLoaderTest {

    @Test
    void loadsValidCredentialFile(@TempDir Path tmp) throws IOException {
        var cred = tmp.resolve("creds.properties");
        Files.writeString(cred, """
            sqlserver_hostname=myserver
            sqlserver_dbname=MCMBLIVE
            sqlserver_portnumber=1433
            sqlserver_username=sa
            sqlserver_password=secret
            """);
        var result = CredentialLoader.load(cred.toString());
        assertEquals("myserver", result.hostname());
        assertEquals("MCMBLIVE", result.dbName());
        assertEquals(1433, result.port());
        assertEquals("sa", result.username());
        assertEquals("secret", result.password());
    }

    @Test
    void throwsWhenFileMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> CredentialLoader.load("/nonexistent/path.properties"));
    }

    @Test
    void throwsWhenKeyMissing(@TempDir Path tmp) throws IOException {
        var cred = tmp.resolve("creds.properties");
        Files.writeString(cred, "sqlserver_hostname=myserver\n");
        assertThrows(IllegalArgumentException.class,
            () -> CredentialLoader.load(cred.toString()));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
mvn test -q 2>&1 | tail -5
```

Expected: Compilation error.

- [ ] **Step 3: Create DbCredentials.java**

```java
package net.javalover.feeearner.config;

public record DbCredentials(String hostname, String dbName, int port,
                             String username, String password) {
    public String jdbcUrl() {
        return "jdbc:sqlserver://" + hostname + ":" + port
               + ";databaseName=" + dbName
               + ";encrypt=false;trustServerCertificate=true";
    }
}
```

- [ ] **Step 4: Create CredentialLoader.java**

```java
package net.javalover.feeearner.config;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public final class CredentialLoader {

    private CredentialLoader() {}

    public static DbCredentials load(String filePath) {
        var props = new Properties();
        try (var reader = new FileReader(filePath)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read credential file: " + filePath, e);
        }
        return new DbCredentials(
            require(props, "sqlserver_hostname"),
            require(props, "sqlserver_dbname"),
            Integer.parseInt(require(props, "sqlserver_portnumber")),
            require(props, "sqlserver_username"),
            require(props, "sqlserver_password")
        );
    }

    private static String require(Properties props, String key) {
        var value = props.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing required credential: " + key);
        return value.trim();
    }
}
```

- [ ] **Step 5: Create AppConfig.java**

```java
package net.javalover.feeearner.config;

import net.javalover.feeearner.model.AppParam;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record AppConfig(Map<String, String> params) {

    public static AppConfig from(List<AppParam> paramList) {
        var map = paramList.stream()
            .filter(AppParam::active)
            .collect(Collectors.toMap(AppParam::name, AppParam::value));
        return new AppConfig(map);
    }

    public String require(String name) {
        var v = params.get(name);
        if (v == null || v.isBlank())
            throw new IllegalStateException("Required parameter missing: " + name);
        return v;
    }

    public String get(String name, String defaultValue) {
        return params.getOrDefault(name, defaultValue);
    }

    public int getInt(String name, int defaultValue) {
        var v = params.get(name);
        if (v == null) return defaultValue;
        return Integer.parseInt(v.trim());
    }

    public String logDir()            { return require("log_dir"); }
    public String outputDir()         { return require("output_dir"); }
    public String debugLevel()        { return get("debug_level", "INFO"); }
    public int threadPoolSize()       { return getInt("thread_pool_size", 2); }
    public int maxThreadPoolSize()    { return getInt("max_thread_pool_size", 4); }
    public String emailRecipients()   { return get("email_recipients", ""); }
    public String emailSender()       { return require("email_sender"); }
    public String emailSubject()      { return require("email_subject"); }
    public String emailBody()         { return require("email_body"); }
    public String smtpServer()        { return require("smtp_server"); }
    public int smtpPort()             { return getInt("smtp_port", 25); }
}
```

- [ ] **Step 6: Run tests — expect PASS**

```bash
mvn test -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: add DbCredentials, CredentialLoader, and AppConfig"
```

---

## Task 6: Logging Initialiser

**Files:** Create `logging/LoggingInitialiser.java`

- [ ] **Step 1: Create LoggingInitialiser.java**

> Note: Rainbow Gum 0.8.0 configuration API should be verified against the library's Javadoc. The implementation below uses the system-properties approach which Rainbow Gum 0.8.0 supports via its JDK integration. Adjust property names if they differ in the installed version.

```java
package net.javalover.feeearner.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class LoggingInitialiser {

    private static final String LOG_FILENAME = "fee_earner_report.log";

    private LoggingInitialiser() {}

    /**
     * Programmatically configure Rainbow Gum before any SLF4J usage.
     * Must be called once, after params are loaded and log_dir is known.
     * Rainbow Gum 0.8.0 reads these system properties during its lazy initialisation.
     */
    public static void init(String logDir, String level) {
        var logPath = Path.of(logDir, LOG_FILENAME).toAbsolutePath().toString();

        // Rainbow Gum 0.8.0 system property configuration
        System.setProperty("rainbowgum.level", normalise(level));
        System.setProperty("rainbowgum.appenders", "file,console");

        // File appender
        System.setProperty("rainbowgum.appender.file.output", logPath);
        System.setProperty("rainbowgum.appender.file.maxFileSize", "100MB");
        System.setProperty("rainbowgum.appender.file.compress", "gz");
        System.setProperty("rainbowgum.appender.file.pattern",
            "%d{yyyy-MM-dd HH:mm:ss.SSS}|%-5level|%t|%logger{36}|%msg|%ex%n");

        // Console appender (startup feedback only)
        System.setProperty("rainbowgum.appender.console.pattern",
            "%d{HH:mm:ss.SSS}|%-5level|%msg%n");

        Logger startupLogger = LoggerFactory.getLogger(LoggingInitialiser.class);
        startupLogger.info("Logging initialised — level={} file={}", level, logPath);
    }

    public static void applyLevel(String level) {
        System.setProperty("rainbowgum.level", normalise(level));
    }

    private static String normalise(String level) {
        return switch (level.toUpperCase()) {
            case "TRACE", "DEBUG", "INFO", "WARN", "ERROR" -> level.toUpperCase();
            default -> "INFO";
        };
    }
}
```

- [ ] **Step 2: Compile to verify no missing imports**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/logging/
git commit -m "feat: add LoggingInitialiser for Rainbow Gum programmatic setup"
```

---

## Task 7: Test Infrastructure

**Files:** Create test credential file and `TestDataSourceFactory.java`

- [ ] **Step 1: Add test-credentials.properties to .gitignore**

Append to `.gitignore`:
```
src/test/resources/test-credentials.properties
```

- [ ] **Step 2: Create test-credentials.properties**

Create `src/test/resources/test-credentials.properties` (fill in local values):

```properties
sqlserver_hostname=localhost
sqlserver_dbname=MCMBLIVE
sqlserver_portnumber=1433
sqlserver_username=your_local_user
sqlserver_password=your_local_password
```

- [ ] **Step 3: Create TestDataSourceFactory.java**

```java
package net.javalover.feeearner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.javalover.feeearner.config.CredentialLoader;
import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Properties;

public final class TestDataSourceFactory {

    private TestDataSourceFactory() {}

    public static DataSource create() {
        var props = new Properties();
        try (InputStream in = TestDataSourceFactory.class
                .getResourceAsStream("/test-credentials.properties")) {
            if (in == null)
                throw new IllegalStateException(
                    "test-credentials.properties not found in src/test/resources/");
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var creds = CredentialLoader.load(
            TestDataSourceFactory.class
                .getResource("/test-credentials.properties")
                .getPath());

        var config = new HikariConfig();
        config.setJdbcUrl(creds.jdbcUrl());
        config.setUsername(creds.username());
        config.setPassword(creds.password());
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add .gitignore src/test/java/net/javalover/feeearner/TestDataSourceFactory.java
git commit -m "test: add TestDataSourceFactory and gitignore for local test credentials"
```

---

## Task 8: ParamRepository

**Files:** Create `repository/ParamRepository.java` and integration test

- [ ] **Step 1: Write failing integration test**

Create `src/test/java/net/javalover/feeearner/repository/ParamRepositoryIT.java`:

```java
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
}
```

- [ ] **Step 2: Run integration test — expect compilation failure**

```bash
mvn test -Pintegration 2>&1 | tail -5
```

- [ ] **Step 3: Create ParamRepository.java**

```java
package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.AppParam;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ParamRepository {

    private final DataSource ds;

    public ParamRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<AppParam> loadAll() {
        var sql = "SELECT param_id, parameter_name, parameter_value, active " +
                  "FROM MCMBLIVE.report.report_param WHERE active = 1";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<AppParam>();
            while (rs.next()) list.add(mapParam(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parameters", e);
        }
    }

    public void save(AppParam param) {
        var sql = "UPDATE MCMBLIVE.report.report_param " +
                  "SET parameter_value = ? WHERE param_id = ?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, param.value());
            stmt.setInt(2, param.paramId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save parameter id=" + param.paramId(), e);
        }
    }

    private AppParam mapParam(ResultSet rs) throws SQLException {
        return new AppParam(
            rs.getInt("param_id"),
            rs.getString("parameter_name"),
            rs.getString("parameter_value"),
            rs.getBoolean("active")
        );
    }
}
```

- [ ] **Step 4: Run integration test — expect PASS**

```bash
mvn test -Pintegration -Dtest=ParamRepositoryIT -q
```

Expected: Tests pass (assumes `report.report_param` has data).

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add ParamRepository with loadAll and save"
```

---

## Task 9: FeeEarnerRepository

**Files:** Create `repository/FeeEarnerRepository.java` and integration test

- [ ] **Step 1: Write failing integration test**

Create `src/test/java/net/javalover/feeearner/repository/FeeEarnerRepositoryIT.java`:

```java
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
```

- [ ] **Step 2: Create FeeEarnerRepository.java**

```java
package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.FeeEarner;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class FeeEarnerRepository {

    private final DataSource ds;

    public FeeEarnerRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<FeeEarner> getLeadFeeEarners() {
        return query("SELECT usrID, [Fee Earner], usrEmail, usrActive, [Type] " +
                     "FROM report.fn_VIC_Lead_Active_FeeEarners()", "Enquiry");
    }

    public List<FeeEarner> getMatterFeeEarners() {
        return query("SELECT usrID, [Fee Earner], usrEmail, usrActive, [Type] " +
                     "FROM report.fn_VIC_Matter_Active_FeeEarners()", "Matter");
    }

    public Set<Integer> getIntersectUserIds() {
        var sql = "SELECT usrID FROM report.fn_VIC_Lead_Active_FeeEarners() " +
                  "INTERSECT " +
                  "SELECT usrID FROM report.fn_VIC_Matter_Active_FeeEarners()";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var ids = new HashSet<Integer>();
            while (rs.next()) ids.add(rs.getInt("usrID"));
            return ids;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch intersect fee earners", e);
        }
    }

    private List<FeeEarner> query(String sql, String expectedType) {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<FeeEarner>();
            while (rs.next()) list.add(mapFeeEarner(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch fee earners", e);
        }
    }

    private FeeEarner mapFeeEarner(ResultSet rs) throws SQLException {
        return new FeeEarner(
            rs.getInt("usrID"),
            rs.getString("Fee Earner"),
            rs.getString("usrEmail"),
            rs.getBoolean("usrActive"),
            rs.getString("Type")
        );
    }
}
```

- [ ] **Step 3: Run integration test — expect PASS**

```bash
mvn test -Pintegration -Dtest=FeeEarnerRepositoryIT -q
```

- [ ] **Step 4: Commit**

```bash
git add src/
git commit -m "feat: add FeeEarnerRepository with lead/matter/intersect queries"
```

---

## Task 10: RunRepository

**Files:** Create `repository/RunRepository.java`

- [ ] **Step 1: Create RunRepository.java**

```java
package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.FeeEarnerRun;
import net.javalover.feeearner.model.RunInfo;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RunRepository {

    private final DataSource ds;

    public RunRepository(DataSource ds) {
        this.ds = ds;
    }

    public int createRun() {
        var sql = "INSERT INTO report.spreadsheet_run (day_run, started_at) " +
                  "OUTPUT INSERTED.run_id VALUES (CAST(GETDATE() AS DATE), GETDATE())";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            var rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
            throw new RuntimeException("INSERT returned no generated run_id");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create run", e);
        }
    }

    public void closeRun(int runId) {
        var sql = "UPDATE report.spreadsheet_run SET finished_at = GETDATE() WHERE run_id = ?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, runId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close run " + runId, e);
        }
    }

    public void insertFeeEarnerRun(FeeEarnerRun run) {
        var sql = "INSERT INTO report.FeeEarnersRun " +
                  "(run_id, day_run, usrID, [Fee Earner], usrEmail, " +
                  " excel_filename, excel_spreadsheet, stored_at) " +
                  "VALUES (?,?,?,?,?,?,?,GETDATE())";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, run.runId());
            stmt.setDate(2, Date.valueOf(run.dayRun()));
            stmt.setInt(3, run.usrID());
            stmt.setString(4, run.feeEarner());
            stmt.setString(5, run.usrEmail());
            stmt.setString(6, run.excelFilename());
            if (run.excelSpreadsheet() != null)
                stmt.setBytes(7, run.excelSpreadsheet());
            else
                stmt.setNull(7, Types.VARBINARY);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert FeeEarnerRun for usrID=" + run.usrID(), e);
        }
    }

    public void updateFeeEarnerRun(int runId, int usrID, String filename, byte[] xlsx) {
        var sql = "UPDATE report.FeeEarnersRun " +
                  "SET excel_filename=?, excel_spreadsheet=?, stored_at=GETDATE() " +
                  "WHERE run_id=? AND usrID=?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filename);
            stmt.setBytes(2, xlsx);
            stmt.setInt(3, runId);
            stmt.setInt(4, usrID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update FeeEarnerRun for usrID=" + usrID, e);
        }
    }

    public Optional<FeeEarnerRun> getMostRecent(int usrID) {
        var sql = "SELECT TOP 1 run_id, day_run, usrID, [Fee Earner], usrEmail, " +
                  "excel_filename, excel_spreadsheet, stored_at " +
                  "FROM report.FeeEarnersRun WHERE usrID = ? " +
                  "ORDER BY run_id DESC";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, usrID);
            var rs = stmt.executeQuery();
            if (!rs.next()) return Optional.empty();
            return Optional.of(mapRun(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get most recent run for usrID=" + usrID, e);
        }
    }

    public List<RunInfo> getAllRuns() {
        var sql = "SELECT run_id, day_run, started_at, finished_at " +
                  "FROM report.spreadsheet_run ORDER BY run_id DESC";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<RunInfo>();
            while (rs.next()) {
                var finTs = rs.getTimestamp("finished_at");
                list.add(new RunInfo(
                    rs.getInt("run_id"),
                    rs.getDate("day_run").toLocalDate(),
                    rs.getTimestamp("started_at").toLocalDateTime(),
                    finTs != null ? finTs.toLocalDateTime() : null
                ));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load runs", e);
        }
    }

    public List<FeeEarnerRun> getRunsForRunId(int runId) {
        var sql = "SELECT run_id, day_run, usrID, [Fee Earner], usrEmail, " +
                  "excel_filename, excel_spreadsheet, stored_at " +
                  "FROM report.FeeEarnersRun WHERE run_id = ?";
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, runId);
            var rs = stmt.executeQuery();
            var list = new ArrayList<FeeEarnerRun>();
            while (rs.next()) list.add(mapRun(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load FeeEarnerRuns for runId=" + runId, e);
        }
    }

    private FeeEarnerRun mapRun(ResultSet rs) throws SQLException {
        var storedAt = rs.getTimestamp("stored_at");
        return new FeeEarnerRun(
            rs.getInt("run_id"),
            rs.getDate("day_run").toLocalDate(),
            rs.getInt("usrID"),
            rs.getString("Fee Earner"),
            rs.getString("usrEmail"),
            rs.getString("excel_filename"),
            rs.getBytes("excel_spreadsheet"),
            storedAt != null ? storedAt.toLocalDateTime() : null
        );
    }
}
```

- [ ] **Step 2: Compile to verify**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "feat: add RunRepository (createRun, closeRun, insertFeeEarnerRun, getMostRecent)"
```

---

## Task 11: WorksheetRepository

**Files:** Create `repository/WorksheetRepository.java`

- [ ] **Step 1: Create WorksheetRepository.java**

```java
package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorksheetRepository {

    private final DataSource ds;

    public WorksheetRepository(DataSource ds) {
        this.ds = ds;
    }

    // ── Lead functions ────────────────────────────────────────────────────────

    public List<FullTaskRow> getLeadFullTaskData(int usrID) {
        return queryFull("SELECT * FROM report.fn_VIC_Lead_Full_Task_Data(?)", usrID);
    }

    public List<LimitationRow> getLeadLimitation(int usrID) {
        return queryLimitation("SELECT * FROM report.fn_VIC_Lead_Limitation_Report(?)", usrID);
    }

    public List<AgedRow> getLeadAged(int usrID) {
        return queryAged("SELECT * FROM report.fn_VIC_Lead_Aged_Report(?)", usrID);
    }

    public List<DuplicateRow> getLeadDuplicate(int usrID) {
        return queryDuplicate("SELECT * FROM report.fn_VIC_Lead_Duplicate_Tasks_Report(?)", usrID);
    }

    public List<HighVolumeRow> getLeadHighVolume(int usrID) {
        return queryHighVolume("SELECT * FROM report.fn_VIC_Lead_High_Volume_Report(?)", usrID);
    }

    // ── Matter functions ──────────────────────────────────────────────────────

    public List<FullTaskRow> getMatterFullTaskData(int usrID) {
        return queryFull("SELECT * FROM report.fn_VIC_Matter_Full_Task_Data(?)", usrID);
    }

    public List<LimitationRow> getMatterLimitation(int usrID) {
        return queryLimitation("SELECT * FROM report.fn_VIC_Matter_Limitation_Report(?)", usrID);
    }

    public List<AgedRow> getMatterAged(int usrID) {
        return queryAged("SELECT * FROM report.fn_VIC_Matter_Aged_Report(?)", usrID);
    }

    public List<DuplicateRow> getMatterDuplicate(int usrID) {
        return queryDuplicate("SELECT * FROM report.fn_VIC_Matter_Duplicate_Tasks_Report(?)", usrID);
    }

    public List<HighVolumeRow> getMatterHighVolume(int usrID) {
        return queryHighVolume("SELECT * FROM report.fn_VIC_Matter_High_Volume_Report(?)", usrID);
    }

    // ── Private query helpers ─────────────────────────────────────────────────

    private List<FullTaskRow> queryFull(String sql, int usrID) {
        try (var conn = ds.getConnection();
             var stmt = prepared(conn, sql, usrID);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<FullTaskRow>();
            while (rs.next()) list.add(mapFull(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("queryFull failed for usrID=" + usrID, e);
        }
    }

    private List<LimitationRow> queryLimitation(String sql, int usrID) {
        try (var conn = ds.getConnection();
             var stmt = prepared(conn, sql, usrID);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<LimitationRow>();
            while (rs.next()) list.add(mapLimitation(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("queryLimitation failed for usrID=" + usrID, e);
        }
    }

    private List<AgedRow> queryAged(String sql, int usrID) {
        try (var conn = ds.getConnection();
             var stmt = prepared(conn, sql, usrID);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<AgedRow>();
            while (rs.next()) list.add(mapAged(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("queryAged failed for usrID=" + usrID, e);
        }
    }

    private List<DuplicateRow> queryDuplicate(String sql, int usrID) {
        try (var conn = ds.getConnection();
             var stmt = prepared(conn, sql, usrID);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<DuplicateRow>();
            while (rs.next()) list.add(mapDuplicate(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("queryDuplicate failed for usrID=" + usrID, e);
        }
    }

    private List<HighVolumeRow> queryHighVolume(String sql, int usrID) {
        try (var conn = ds.getConnection();
             var stmt = prepared(conn, sql, usrID);
             var rs   = stmt.executeQuery()) {
            var list = new ArrayList<HighVolumeRow>();
            while (rs.next()) list.add(mapHighVolume(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("queryHighVolume failed for usrID=" + usrID, e);
        }
    }

    private PreparedStatement prepared(Connection conn, String sql, int usrID) throws SQLException {
        var stmt = conn.prepareStatement(sql);
        stmt.setInt(1, usrID);
        return stmt;
    }

    // ── Row mappers ───────────────────────────────────────────────────────────

    private FullTaskRow mapFull(ResultSet rs) throws SQLException {
        return new FullTaskRow(
            rs.getString("Type"),
            toLocalDate(rs.getDate("Report Date")),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs.getTimestamp("Task Created Date")),
            toLocalDateTime(rs.getTimestamp("Task Due Date"))
        );
    }

    private LimitationRow mapLimitation(ResultSet rs) throws SQLException {
        return new LimitationRow(
            rs.getString("Type"),
            toLocalDate(rs.getDate("Report Date")),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs.getTimestamp("Task Created Date")),
            toLocalDateTime(rs.getTimestamp("Task Due Date")),
            rs.getString("Key Words")
        );
    }

    private AgedRow mapAged(ResultSet rs) throws SQLException {
        return new AgedRow(
            rs.getString("Type"),
            toLocalDate(rs.getDate("Report Date")),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs.getTimestamp("Task Created Date")),
            toLocalDateTime(rs.getTimestamp("Task Due Date"))
        );
    }

    private DuplicateRow mapDuplicate(ResultSet rs) throws SQLException {
        return new DuplicateRow(
            rs.getString("Type"),
            toLocalDate(rs.getDate("Report Date")),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs.getTimestamp("Task Created Date")),
            toLocalDateTime(rs.getTimestamp("Task Due Date")),
            rs.getString("Duplicate")
        );
    }

    private HighVolumeRow mapHighVolume(ResultSet rs) throws SQLException {
        return new HighVolumeRow(
            rs.getString("Type"),
            toLocalDate(rs.getDate("Report Date")),
            rs.getString("Matter Number"),
            rs.getString("Matter Name/Description"),
            rs.getString("Department"),
            rs.getString("Practice Code"),
            rs.getString("Office Name"),
            rs.getString("Jurisdiction"),
            rs.getString("Fee Earner"),
            rs.getString("Legal Assistant"),
            rs.getString("Supervising Fee Earner"),
            rs.getString("Task Description"),
            rs.getString("Task Type"),
            rs.getString("Task Notes"),
            rs.getString("Task Owner"),
            toLocalDateTime(rs.getTimestamp("Task Created Date")),
            toLocalDateTime(rs.getTimestamp("Task Due Date")),
            rs.getInt("Matter Row Count")
        );
    }

    private LocalDate toLocalDate(java.sql.Date d) {
        return d != null ? d.toLocalDate() : null;
    }

    private LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/repository/WorksheetRepository.java
git commit -m "feat: add WorksheetRepository for all 10 SQL Server function calls"
```

---

## Task 12: ArchiveRepository

**Files:** Create `repository/ArchiveRepository.java`

- [ ] **Step 1: Create ArchiveRepository.java**

```java
package net.javalover.feeearner.repository;

import net.javalover.feeearner.model.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;

public class ArchiveRepository {

    private final DataSource ds;

    public ArchiveRepository(DataSource ds) {
        this.ds = ds;
    }

    public void insertFullTaskRows(int runId, LocalDate dayRun, int usrID,
                                   List<FullTaskRow> rows) {
        var sql = "INSERT INTO report.full_task_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Task Complete],[Type]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseColumns(stmt, 5, row);
            stmt.setString(21, row.type());
        });
    }

    public void insertLimitationRows(int runId, LocalDate dayRun, int usrID,
                                     List<LimitationRow> rows) {
        var sql = "INSERT INTO report.limitation_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Task Complete],[Type],[Key Words]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseColumns(stmt, 5, row);
            stmt.setString(21, row.type());
            stmt.setString(22, row.keyWords());
        });
    }

    public void insertAgedRows(int runId, LocalDate dayRun, int usrID,
                               List<AgedRow> rows) {
        var sql = "INSERT INTO report.aged_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Task Complete],[Type]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseColumns(stmt, 5, row);
            stmt.setString(21, row.type());
        });
    }

    public void insertDuplicateRows(int runId, LocalDate dayRun, int usrID,
                                    List<DuplicateRow> rows) {
        var sql = "INSERT INTO report.duplicate_task_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Task Complete],[Type],Duplicate) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseColumns(stmt, 5, row);
            stmt.setString(21, row.type());
            stmt.setString(22, row.duplicate());
        });
    }

    public void insertHighVolumeRows(int runId, LocalDate dayRun, int usrID,
                                     List<HighVolumeRow> rows) {
        var sql = "INSERT INTO report.high_volume_archive " +
                  "(day_run,run_id,usrID,row_number,[Report Date],[Matter Number]," +
                  "[Matter Name/Description],Department,[Practice Code],[Office Name]," +
                  "Jurisdiction,[Fee Earner],[Legal Assistant],[Supervising Fee Earner]," +
                  "[Task Description],[Task Type],[Task Notes],[Task Owner]," +
                  "[Task Created Date],[Task Due Date],[Task Complete],[Type],[Matter Row Count]) " +
                  "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)";
        batchInsert(sql, runId, dayRun, usrID, rows, (stmt, row, rowNum) -> {
            stmt.setDate(1, Date.valueOf(dayRun));
            stmt.setInt(2, runId);
            stmt.setInt(3, usrID);
            stmt.setInt(4, rowNum);
            setBaseColumns(stmt, 5, row);
            stmt.setString(21, row.type());
            stmt.setInt(22, row.matterRowCount());
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface RowBinder<R extends BaseRow> {
        void bind(PreparedStatement stmt, R row, int rowNumber) throws SQLException;
    }

    private <R extends BaseRow> void batchInsert(String sql, int runId, LocalDate dayRun,
                                                  int usrID, List<R> rows,
                                                  RowBinder<R> binder) {
        if (rows.isEmpty()) return;
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            int rowNum = 1;
            for (var row : rows) {
                binder.bind(stmt, row, rowNum++);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert failed for usrID=" + usrID, e);
        }
    }

    private void setBaseColumns(PreparedStatement stmt, int offset, BaseRow row)
            throws SQLException {
        setNullableDate(stmt, offset,     row.reportDate());
        stmt.setString(offset + 1,        row.matterNumber());
        stmt.setString(offset + 2,        row.matterNameDescription());
        stmt.setString(offset + 3,        row.department());
        stmt.setString(offset + 4,        row.practiceCode());
        stmt.setString(offset + 5,        row.officeName());
        stmt.setString(offset + 6,        row.jurisdiction());
        stmt.setString(offset + 7,        row.feeEarner());
        stmt.setString(offset + 8,        row.legalAssistant());
        stmt.setString(offset + 9,        row.supervisingFeeEarner());
        stmt.setString(offset + 10,       row.taskDescription());
        stmt.setString(offset + 11,       row.taskType());
        stmt.setString(offset + 12,       row.taskNotes());
        stmt.setString(offset + 13,       row.taskOwner());
        setNullableTimestamp(stmt, offset + 14, row.taskCreatedDate());
        setNullableTimestamp(stmt, offset + 15, row.taskDueDate());
    }

    private void setNullableDate(PreparedStatement stmt, int idx,
                                  java.time.LocalDate d) throws SQLException {
        if (d != null) stmt.setDate(idx, Date.valueOf(d));
        else           stmt.setNull(idx, Types.DATE);
    }

    private void setNullableTimestamp(PreparedStatement stmt, int idx,
                                       java.time.LocalDateTime dt) throws SQLException {
        if (dt != null) stmt.setTimestamp(idx, Timestamp.valueOf(dt));
        else            stmt.setNull(idx, Types.TIMESTAMP);
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/javalover/feeearner/repository/ArchiveRepository.java
git commit -m "feat: add ArchiveRepository with batch insert for all 5 archive tables"
```

---

## Task 13: ParameterService (validation only)

**Files:** Create `service/ValidationResult.java`, `service/ParameterService.java`, and unit test

- [ ] **Step 1: Write failing test**

Create `src/test/java/net/javalover/feeearner/service/ParameterServiceTest.java`:

```java
package net.javalover.feeearner.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ParameterServiceTest {

    @Test
    void validatesExistingWritableDir(@TempDir Path tmp) {
        var result = ParameterService.validateDirectory(tmp.toString());
        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    void rejectsNonExistentDir() {
        var result = ParameterService.validateDirectory("/nonexistent/path/xyz");
        assertFalse(result.valid());
        assertNotNull(result.errorMessage());
    }

    @Test
    void rejectsBlankPath() {
        var result = ParameterService.validateDirectory("  ");
        assertFalse(result.valid());
    }

    @Test
    void validateParamPassesThroughNonDirParams() {
        var result = ParameterService.validate("smtp_server", "mail.example.com");
        assertTrue(result.valid());
    }

    @Test
    void validateParamChecksLogDir(@TempDir Path tmp) {
        var result = ParameterService.validate("log_dir", tmp.toString());
        assertTrue(result.valid());
    }

    @Test
    void validateParamRejectsInvalidLogDir() {
        var result = ParameterService.validate("log_dir", "/no/such/path");
        assertFalse(result.valid());
    }
}
```

- [ ] **Step 2: Run — expect compilation failure**

```bash
mvn test -q 2>&1 | tail -5
```

- [ ] **Step 3: Create ValidationResult.java**

```java
package net.javalover.feeearner.service;

public record ValidationResult(boolean valid, String errorMessage) {
    public static ValidationResult ok() { return new ValidationResult(true, null); }
    public static ValidationResult fail(String msg) { return new ValidationResult(false, msg); }
}
```

- [ ] **Step 4: Create ParameterService.java**

```java
package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.model.AppParam;
import net.javalover.feeearner.repository.ParamRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class ParameterService {

    private static final Set<String> DIR_PARAMS = Set.of("log_dir", "output_dir");

    private final ParamRepository paramRepo;

    public ParameterService(ParamRepository paramRepo) {
        this.paramRepo = paramRepo;
    }

    public AppConfig load() {
        return AppConfig.from(paramRepo.loadAll());
    }

    public AppConfig reload() {
        return load();
    }

    public ValidationResult save(AppParam param) {
        var check = validate(param.name(), param.value());
        if (!check.valid()) return check;
        paramRepo.save(param);
        return ValidationResult.ok();
    }

    public static ValidationResult validate(String name, String value) {
        if (DIR_PARAMS.contains(name)) return validateDirectory(value);
        return ValidationResult.ok();
    }

    public static ValidationResult validateDirectory(String path) {
        if (path == null || path.isBlank())
            return ValidationResult.fail("Path must not be blank");
        var p = Path.of(path.trim());
        if (!Files.isDirectory(p))
            return ValidationResult.fail("Directory does not exist: " + path);
        if (!Files.isWritable(p))
            return ValidationResult.fail("Directory is not writable: " + path);
        return ValidationResult.ok();
    }

    public List<AppParam> loadAll() {
        return paramRepo.loadAll();
    }
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
mvn test -q
```

Expected: BUILD SUCCESS, all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: add ParameterService with directory validation and ValidationResult"
```

---

## Task 14: Final Compile & Full Test Run

- [ ] **Step 1: Full compile**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS with no warnings about missing classes.

- [ ] **Step 2: Run all unit tests**

```bash
mvn test -q
```

Expected: BUILD SUCCESS. All non-integration tests pass.

- [ ] **Step 3: Run integration tests against local SQL Server**

Ensure `src/test/resources/test-credentials.properties` has valid local credentials, then:

```bash
mvn test -Pintegration -q
```

Expected: All tests pass. If any repository integration test fails, check:
- SQL Server is running on `localhost:1433`
- `MCMBLIVE` database is accessible
- Tables from `01_tables.sql` have been created
- `report.report_param` has at least one active row

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: plan 1 complete — foundation layer verified"
```

---

## Plan 1 Completion

At this point the project has:
- Maven build with all dependencies, uberjar and native profiles
- SQL scripts for all database objects (tables, partitions, indexes)
- All immutable model records with sealed `BaseRow` interface
- `AppConfig`, `CredentialLoader`, `DbCredentials`
- `LoggingInitialiser` (Rainbow Gum)
- All 5 repositories: `ParamRepository`, `FeeEarnerRepository`, `RunRepository`, `WorksheetRepository`, `ArchiveRepository`
- `ParameterService` with directory validation
- Unit tests passing, integration tests verified against local SQL Server

**Next:** Plan 2 — Core Services + CLI (`SpreadsheetService`, `EmailService`, `RunService`, all Excel builders, `MailSender`, `Main.java`)
