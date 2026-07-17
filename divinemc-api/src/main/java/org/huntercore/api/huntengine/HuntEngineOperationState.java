package org.huntercore.api.huntengine;

/** Lifecycle state for a non-blocking engine operation. */
public enum HuntEngineOperationState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
