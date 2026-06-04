USE MCMBLIVE;
GO

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
