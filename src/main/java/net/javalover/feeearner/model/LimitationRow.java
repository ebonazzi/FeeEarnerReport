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
