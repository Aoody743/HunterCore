package org.bxteam.divinemc.region;

import java.io.IOException;

public interface IRegionCreateFunction {
    AutoCloseable create(RegionFileInfo info) throws IOException;
}
