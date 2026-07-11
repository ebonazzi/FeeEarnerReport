package net.javalover.feeearner.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modal Cancel/Dismiss progress dialog for a single-fee-earner generate/regenerate. Cancel is
 * UI-only: it closes the dialog immediately but never interrupts the background work, which
 * always runs to completion and persists its result. {@code onWorkComplete} always fires when
 * the background work finishes, regardless of whether the dialog was already cancelled/closed,
 * so callers can reliably clear per-row "in flight" state.
 */
public final class GenerationProgressDialog {

    public enum Mode {
        GENERATE("Generating", "generate", "generated"),
        REGENERATE("Regenerating", "regenerate", "regenerated");

        final String gerund;
        final String base;
        final String past;

        Mode(String gerund, String base, String past) {
            this.gerund = gerund;
            this.base = base;
            this.past = past;
        }
    }

    @FunctionalInterface
    public interface Work {
        void run() throws Exception;
    }

    private GenerationProgressDialog() {
    }

    public static void run(Window owner, Mode mode, String feeEarnerName,
                            Work work, Runnable onWorkComplete) {
        var stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setResizable(false);
        stage.setTitle(mode.gerund + " Spreadsheet");

        var message = new Label(mode.gerund + " Spreadsheet");
        message.setWrapText(true);
        message.setMaxWidth(320);

        var cancelBtn = new Button("Cancel");
        var dismissBtn = new Button("Dismiss");
        dismissBtn.setDisable(true);

        var buttons = new HBox(8, cancelBtn, dismissBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        var root = new VBox(12, message, buttons);
        root.setPadding(new Insets(16));
        root.setPrefWidth(360);

        var cancelled = new AtomicBoolean(false);

        cancelBtn.setOnAction(e -> {
            cancelled.set(true);
            stage.close();
        });
        dismissBtn.setOnAction(e -> stage.close());

        stage.setOnCloseRequest(e -> {
            if (dismissBtn.isDisabled()) {
                // Still in progress: X acts like Cancel.
                cancelled.set(true);
            }
            // Already complete: X acts like Dismiss — default close behavior is correct as-is.
        });

        stage.setOnShown(e -> {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
        });

        stage.setScene(new Scene(root));

        var thread = new Thread(() -> {
            Exception failure = null;
            try {
                work.run();
            } catch (Exception ex) {
                failure = ex;
            }
            var finalFailure = failure;
            Platform.runLater(() -> {
                onWorkComplete.run();
                if (cancelled.get()) {
                    return;
                }
                if (finalFailure == null) {
                    message.setText("Spreadsheet " + mode.past + " for " + feeEarnerName);
                } else {
                    var errorText = finalFailure.getMessage() != null
                            ? finalFailure.getMessage()
                            : finalFailure.getClass().getSimpleName();
                    message.setText("Failed to " + mode.base + " spreadsheet for "
                            + feeEarnerName + ": " + errorText);
                }
                cancelBtn.setDisable(true);
                dismissBtn.setDisable(false);
            });
        }, "generation-progress-" + mode.name().toLowerCase());
        thread.setDaemon(true);
        thread.start();

        stage.showAndWait();
    }
}
