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
