package org.huntercore.api.huntengine;

/** Outcome for one preserved legacy asset. */
public enum HuntEngineMigrationEntryState {
    DETECTED,
    BACKED_UP,
    ISOLATED,
    PRESERVED,
    DRAFTED,
    MANUAL_REQUIRED,
    FAILED
}
