USE MCMBLIVE;
GO

-- Fee Earner Report — reset generated data for a clean run.
-- Wipes ONLY the seven generated-data/output tables.
-- Does NOT touch report.report_param (your parameters) or any source TVFs / PMS tables.

-- Per-fee-earner output (xlsx blobs + metadata)
TRUNCATE TABLE report.FeeEarnersRun;

-- Archive tables (the five worksheet datasets)
TRUNCATE TABLE report.full_task_archive;
TRUNCATE TABLE report.limitation_archive;
TRUNCATE TABLE report.aged_archive;
TRUNCATE TABLE report.duplicate_task_archive;
TRUNCATE TABLE report.high_volume_archive;

-- Run ledger (resets run_id IDENTITY back to 1)
TRUNCATE TABLE report.spreadsheet_run;
GO
