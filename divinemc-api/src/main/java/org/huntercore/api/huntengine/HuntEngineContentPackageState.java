package org.huntercore.api.huntengine;

/** Lifecycle state for a native content package staged by HuntEngine. */
public enum HuntEngineContentPackageState {
    STAGED,
    VALID,
    INVALID,
    BUILDING,
    PUBLISHED
}
