package com.cgi.eoss.ftep.catalogue.geoserver;

import java.nio.file.Path;

/**
 * <p>Facade to a Geoserver instance, to enhance/enable F-TEP W*S functionality.</p>
 */
public interface GeoserverService {
    /**
     * <p>Ingest the file at the given path to the selected workspace. The native CRS of the file must be supplied.</p>
     */
    void ingest(String workspace, Path path, String crs);

    /**
     * <p>Delete the layer with the given name from the selected workspace.</p>
     */
    void delete(String workspace, String layerName);
}