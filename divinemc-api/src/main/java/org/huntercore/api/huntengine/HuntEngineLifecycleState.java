package org.huntercore.api.huntengine;

/** Current runtime state of the HuntEngine plugin. */
public enum HuntEngineLifecycleState {
    UNAVAILABLE,
    STARTING,
    READY,
    RELOADING,
    FAILED,
    STOPPING
}
