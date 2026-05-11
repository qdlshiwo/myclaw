package com.myclaw.core.store;

import com.myclaw.core.model.CronJob;

import java.util.Collection;
import java.util.Optional;

public interface CronJobStore {
    Collection<CronJob> listAll();
    Optional<CronJob> get(String id);
    CronJob save(CronJob job);
    boolean delete(String id);
    void updateState(String id, java.util.Map<String, Object> state);
}
