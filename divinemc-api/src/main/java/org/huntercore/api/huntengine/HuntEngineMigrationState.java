package org.huntercore.api.huntengine;

/** Overall state of HunterAssets to HuntEngine migration. */
public enum HuntEngineMigrationState {
    NOT_REQUIRED,
    PENDING,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_MANUAL_WORK,
    FAILED
}
