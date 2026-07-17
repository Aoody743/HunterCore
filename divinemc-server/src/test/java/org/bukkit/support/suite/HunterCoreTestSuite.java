package org.bukkit.support.suite;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/** Runs standalone HunterCore server tests without requiring Minecraft registry setup. */
@Suite(failIfNoTests = false)
@SelectPackages("org.huntercore.plugin")
public class HunterCoreTestSuite {
}
