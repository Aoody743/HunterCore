package org.huntercore.api.huntengine;

/**
 * Broad content classes used by HunterCore surfaces. New upstream types must map to {@link #OTHER}
 * until a stable HunterCore interaction exists for them.
 */
public enum HuntEngineContentKind {
    ITEM,
    BLOCK,
    FURNITURE,
    RECIPE,
    FONT,
    IMAGE,
    SOUND,
    OTHER
}
