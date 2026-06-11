-- 06_sharepoint_params.sql
-- Seeds the six SharePoint parameters read by AppConfig.sharePoint*() for the
-- "Sharepoint Deployment" feature. Idempotent: each row is inserted only if a
-- parameter with that name does not already exist, so this script is safe to
-- re-run. param_id is IDENTITY and is therefore not supplied. active = 1.
--
-- REPLACE the three credential placeholders below with your real values before
-- running (client id, tenant id, client secret). host / site_name / target_dir
-- are pre-filled with the intended VIC reports destination — adjust if needed.
--
-- Run out of band against the database, like the other numbered scripts.

USE MCMBLIVE;
GO

SET NOCOUNT ON;

-- shrpnt_client_id : Azure AD application (client) id
IF NOT EXISTS (SELECT 1 FROM report.report_param WHERE parameter_name = N'shrpnt_client_id')
    INSERT INTO report.report_param (parameter_name, parameter_value, active)
    VALUES (N'shrpnt_client_id', N'<REPLACE_WITH_CLIENT_ID>', 1);

-- shrpnt_tenant_id : Azure AD tenant (directory) id
IF NOT EXISTS (SELECT 1 FROM report.report_param WHERE parameter_name = N'shrpnt_tenant_id')
    INSERT INTO report.report_param (parameter_name, parameter_value, active)
    VALUES (N'shrpnt_tenant_id', N'<REPLACE_WITH_TENANT_ID>', 1);

-- shrpnt_secret_id : OAuth2 client secret VALUE (not the secret id/description)
IF NOT EXISTS (SELECT 1 FROM report.report_param WHERE parameter_name = N'shrpnt_secret_id')
    INSERT INTO report.report_param (parameter_name, parameter_value, active)
    VALUES (N'shrpnt_secret_id', N'<REPLACE_WITH_CLIENT_SECRET>', 1);

-- shrpnt_host : SharePoint hostname
IF NOT EXISTS (SELECT 1 FROM report.report_param WHERE parameter_name = N'shrpnt_host')
    INSERT INTO report.report_param (parameter_name, parameter_value, active)
    VALUES (N'shrpnt_host', N'eliobonazzigmail.sharepoint.com', 1);

-- shrpnt_site_name : 'root' for the top-level site, or a named site (e.g. 'Legal')
IF NOT EXISTS (SELECT 1 FROM report.report_param WHERE parameter_name = N'shrpnt_site_name')
    INSERT INTO report.report_param (parameter_name, parameter_value, active)
    VALUES (N'shrpnt_site_name', N'root', 1);

-- shrpnt_target_dir : folder path inside the document library
IF NOT EXISTS (SELECT 1 FROM report.report_param WHERE parameter_name = N'shrpnt_target_dir')
    INSERT INTO report.report_param (parameter_name, parameter_value, active)
    VALUES (N'shrpnt_target_dir', N'Shared Documents/Shared Documents/LSP/VIC_reports', 1);

GO

-- Verify:
-- SELECT parameter_name, parameter_value, active
-- FROM report.report_param
-- WHERE parameter_name LIKE 'shrpnt[_]%'
-- ORDER BY parameter_name;
