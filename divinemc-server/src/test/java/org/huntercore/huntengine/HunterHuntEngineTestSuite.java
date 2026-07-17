package org.huntercore.huntengine;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/** Runs HuntEngine publication filesystem tests without requiring a Bukkit server runtime. */
@Suite(failIfNoTests = false)
@SelectClasses(HunterHuntEnginePublicationFilesTest.class)
public class HunterHuntEngineTestSuite {
}
