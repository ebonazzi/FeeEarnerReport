USE MCMBLIVE;
GO

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
