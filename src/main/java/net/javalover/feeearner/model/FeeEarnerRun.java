package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeeEarnerRun(int runId, LocalDate dayRun, int usrID,
                           String feeEarner, String usrEmail,
                           String excelFilename, byte[] excelSpreadsheet,
                           LocalDateTime storedAt) {}
