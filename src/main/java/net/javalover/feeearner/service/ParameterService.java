package net.javalover.feeearner.service;

import net.javalover.feeearner.config.AppConfig;
import net.javalover.feeearner.logging.LoggingInitialiser;
import net.javalover.feeearner.model.AppParam;
import net.javalover.feeearner.repository.ParamRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class ParameterService {

    private static final Set<String> DIR_PARAMS = Set.of("log_dir", "output_dir");

    private final ParamRepository paramRepo;

    public ParameterService(ParamRepository paramRepo) {
        this.paramRepo = paramRepo;
    }

    public AppConfig load() {
        return AppConfig.from(paramRepo.loadAll());
    }

    public List<AppParam> loadAll() {
        return paramRepo.loadAll();
    }

    public AppConfig reload() {
        var config = load();
        LoggingInitialiser.applyLevel(config.debugLevel());
        return config;
    }

    public ValidationResult save(AppParam param) {
        var check = validate(param.name(), param.value());
        if (!check.valid()) return check;
        paramRepo.save(param);
        return ValidationResult.ok();
    }

    public static ValidationResult validate(String name, String value) {
        if (DIR_PARAMS.contains(name)) return validateDirectory(value);
        return ValidationResult.ok();
    }

    public static ValidationResult validateDirectory(String path) {
        if (path == null || path.isBlank())
            return ValidationResult.fail("Path must not be blank");
        var p = Path.of(path.trim());
        if (!Files.isDirectory(p))
            return ValidationResult.fail("Not a directory: " + path);
        if (!Files.isWritable(p))
            return ValidationResult.fail("Directory not writable: " + path);
        return ValidationResult.ok();
    }
}
