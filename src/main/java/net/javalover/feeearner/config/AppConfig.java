package net.javalover.feeearner.config;

import net.javalover.feeearner.model.AppParam;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record AppConfig(Map<String, String> params) {

    public static AppConfig from(List<AppParam> paramList) {
        var map = paramList.stream()
            .filter(AppParam::active)
            .collect(Collectors.toMap(AppParam::name, AppParam::value));
        return new AppConfig(map);
    }

    public String require(String name) {
        var v = params.get(name);
        if (v == null || v.isBlank())
            throw new IllegalStateException("Required parameter missing: " + name);
        return v;
    }

    public String get(String name, String defaultValue) {
        return params.getOrDefault(name, defaultValue);
    }

    public int getInt(String name, int defaultValue) {
        var v = params.get(name);
        if (v == null) return defaultValue;
        return Integer.parseInt(v.trim());
    }

    public String logDir()            { return require("log_dir"); }
    public String outputDir()         { return require("output_dir"); }
    public String debugLevel()        { return get("debug_level", "INFO"); }
    public int threadPoolSize()       { return getInt("thread_pool_size", 2); }
    public int maxThreadPoolSize()    { return getInt("max_thread_pool_size", 4); }
    public String emailRecipients()   { return get("email_recipients", ""); }
    public String emailSender()       { return require("email_sender"); }
    public String emailSubject()      { return require("email_subject"); }
    public String emailBody()         { return require("email_body"); }
    public String smtpServer()        { return require("smtp_server"); }
    public int smtpPort()             { return getInt("smtp_port", 25); }
}
