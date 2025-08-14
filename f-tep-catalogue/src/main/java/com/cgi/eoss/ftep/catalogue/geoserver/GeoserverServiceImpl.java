package com.cgi.eoss.ftep.catalogue.geoserver;

import com.cgi.eoss.ftep.catalogue.IngestionException;
import com.google.common.base.Preconditions;
import com.google.common.io.MoreFiles;
import it.geosolutions.geoserver.rest.GeoServerRESTManager;
import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import it.geosolutions.geoserver.rest.GeoServerRESTReader;
import it.geosolutions.geoserver.rest.decoder.RESTCoverageStore;
import it.geosolutions.geoserver.rest.encoder.GSLayerEncoder;
import it.geosolutions.geoserver.rest.encoder.GSResourceEncoder;
import it.geosolutions.geoserver.rest.encoder.coverage.GSCoverageEncoder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.Set;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static it.geosolutions.geoserver.rest.encoder.GSResourceEncoder.ProjectionPolicy.REPROJECT_TO_DECLARED;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@Log4j2
public class GeoserverServiceImpl implements GeoserverService {

    private static final String RASTER_STYLE = "raster";

    @Getter
    private final HttpUrl externalUrl;
    private final GeoServerRESTPublisher publisher;
    private final GeoServerRESTReader reader;
    private final URI geoserverURI;
    private final String user;
    private final String pw;

    @Value("${ftep.catalogue.geoserver.enabled:true}")
    private boolean geoserverEnabled;

    @Value("#{'${ftep.catalogue.geoserver.ingest-filetypes:TIF}'.split(',')}")
    private Set<String> ingestableFiletypes;

    @Autowired
    public GeoserverServiceImpl(@Value("${ftep.catalogue.geoserver.url:http://ftep-geoserver:9080/geoserver/}") String url,
                                @Value("${ftep.catalogue.geoserver.externalUrl:http://ftep-geoserver:9080/geoserver/}") String externalUrl,
                                @Value("${ftep.catalogue.geoserver.username:ftepgeoserver}") String username,
                                @Value("${ftep.catalogue.geoserver.password:ftepgeoserverpass}") String password) throws MalformedURLException {
        this.externalUrl = HttpUrl.parse(externalUrl);
        GeoServerRESTManager geoserver = new GeoServerRESTManager(new URL(url), username, password);
        this.publisher = geoserver.getPublisher();
        this.reader = geoserver.getReader();
        this.geoserverURI = URI.create(url);
        this.user = username;
        this.pw = password;
    }

    @Override
    public String ingest(String workspace, Path path, String crs) {
        Path fileName = path.getFileName();
        String datastoreName = MoreFiles.getNameWithoutExtension(fileName);
        String layerName = MoreFiles.getNameWithoutExtension(fileName);

        if (!geoserverEnabled) {
            LOG.warn("Geoserver is disabled; 'ingested' file: {}:{}", workspace, layerName);
            return null;
        }

        ensureWorkspaceExists(workspace);

        if (!isIngestibleFile(fileName.toString())) {
            // TODO Ingest more filetypes
            LOG.info("Unable to ingest product with filename: {}" + fileName);
            return null;
        }

        try {
            URL url1 = this.geoserverURI
                    .resolve("rest/workspaces/" + workspace + "/coveragestores")
                    .toURL();

            String content1 = 
                    "{ \"coverageStore\": { "
                    + "\"name\":\"" + datastoreName + "\", "
                    + "\"url\":\"file:" + path.toAbsolutePath() + "\", "
                    + "\"workspace\":{\"name\": \"" + workspace + "\"}, "
                    + "\"type\":\"GeoTIFF\", "
                    + "\"enabled\":true } }";

            HttpURLConnection con = (HttpURLConnection)url1.openConnection();
            String auth = this.user + ":" + this.pw;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeaderValue = "Basic " + new String(encodedAuth);
            con.setRequestProperty("Authorization", authHeaderValue);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = content1.getBytes("utf-8");
                os.write(input, 0, input.length);			
            }
            
            int resp = con.getResponseCode();
            if (resp != 201) {
                throw new IOException("Response code not 201");
            }
        } catch (IOException ex) {
            LOG.error("Failed to create coveragestore for file: {}", path, ex);
            throw new IngestionException(ex);
        }

        try {
            URL url2 = this.geoserverURI
                    .resolve("rest/workspaces/" + workspace + "/coveragestores/" + datastoreName + "/coverages")
                    .toURL();
            
            String content2 = 
                    "{ \"coverage\": { "
                    + "\"name\":\"" + datastoreName + "\", "
                    + "\"title\":\"" + layerName + "\", "
                    + "\"enabled\":true } }";
            
            HttpURLConnection con = (HttpURLConnection)url2.openConnection();
            String auth = this.user + ":" + this.pw;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeaderValue = "Basic " + new String(encodedAuth);
            con.setRequestProperty("Authorization", authHeaderValue);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = content2.getBytes("utf-8");
                os.write(input, 0, input.length);			
            }

            int resp = con.getResponseCode();
            if (resp != 201) {
                throw new IOException("Response code not 201");
            }

            LOG.info("Ingested GeoTIFF to geoserver with id: {}:{}", workspace, layerName);
            return url2.toString(); // This is actually not used anywhere
            
        } catch (IOException ex) {
            LOG.error("Failed to create coverage for file: {}", path, ex);
            throw new IngestionException(ex);
        }
    }

    @Override
    public boolean isIngestibleFile(String filename) {
        return ingestableFiletypes.stream().anyMatch(ft -> filename.toUpperCase().endsWith("." + ft.toUpperCase()));
    }

    @Override
    public void delete(String workspace, String layerName) {
        if (!geoserverEnabled) {
            LOG.warn("Geoserver is disabled; no deletion occurring for {}:{}", workspace, layerName);
            return;
        }

        publisher.removeLayer(workspace, layerName);
        LOG.info("Deleted layer from geoserver: {}{}", workspace, layerName);
    }

    private void ensureWorkspaceExists(String workspace) {
        if (!reader.existsWorkspace(workspace)) {
            LOG.info("Creating new workspace {}", workspace);
            publisher.createWorkspace(workspace);
        }
    }

    private RESTCoverageStore publishExternalGeoTIFF(String workspace, String storeName, File geotiff,
                                                     String coverageName, String srs, GSResourceEncoder.ProjectionPolicy policy, String defaultStyle)
            throws FileNotFoundException {
        for (Object param : new Object[]{workspace, storeName, geotiff, coverageName, srs, policy, defaultStyle}){
            Preconditions.checkNotNull(param, "Unable to publish GeoTIFF with null parameter");
        }

        // config coverage props (srs)
        final GSCoverageEncoder coverageEncoder = new GSCoverageEncoder();
        coverageEncoder.setName(coverageName);
        coverageEncoder.setTitle(coverageName);
        coverageEncoder.setSRS(srs);
        coverageEncoder.setProjectionPolicy(policy);

        // config layer props (style, ...)
        final GSLayerEncoder layerEncoder = new GSLayerEncoder();
        layerEncoder.setDefaultStyle(defaultStyle);

        return publisher.publishExternalGeoTIFF(workspace, storeName, geotiff, coverageEncoder, layerEncoder);
    }

}
