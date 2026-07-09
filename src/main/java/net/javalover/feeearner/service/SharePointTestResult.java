package net.javalover.feeearner.service;

/**
 * Result of {@link DeployService#testConnection}. {@code cleanupWarning} is {@code null}
 * when the post-upload delete of the test file succeeded; otherwise it holds the cleanup
 * failure's message, while the test itself is still reported as a success.
 */
public record SharePointTestResult(String filename, String cleanupWarning) {
}
