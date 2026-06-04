package net.javalover.feeearner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RunInfo(int runId, LocalDate dayRun,
                      LocalDateTime startedAt, LocalDateTime finishedAt) {}
