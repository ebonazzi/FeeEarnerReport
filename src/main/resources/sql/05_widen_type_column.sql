USE MCMBLIVE;
GO

-- Widen [Type] from varchar(6) to varchar(20) on all archive tables.
-- The lead-side TVFs return [Type]='Enquiry' (7 chars), which overflowed
-- varchar(6) and caused "String or binary data would be truncated" on insert.
-- Matter rows ([Type]='Matter', 6 chars) fit, which is why only lead/enquiry
-- fee earners failed.

ALTER TABLE report.full_task_archive      ALTER COLUMN [Type] varchar(20) NULL;
ALTER TABLE report.limitation_archive     ALTER COLUMN [Type] varchar(20) NULL;
ALTER TABLE report.aged_archive           ALTER COLUMN [Type] varchar(20) NULL;
ALTER TABLE report.duplicate_task_archive ALTER COLUMN [Type] varchar(20) NULL;
ALTER TABLE report.high_volume_archive    ALTER COLUMN [Type] varchar(20) NULL;
GO
