package net.javalover.feeearner.service;

import net.javalover.feeearner.repository.RunRepository;

public class RunService {

    private final RunRepository runRepo;

    public RunService(RunRepository runRepo) {
        this.runRepo = runRepo;
    }

    public int startRun() {
        return runRepo.createRun();
    }

    public void finishRun(int runId) {
        runRepo.closeRun(runId);
    }
}
