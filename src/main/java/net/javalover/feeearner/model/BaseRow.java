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
