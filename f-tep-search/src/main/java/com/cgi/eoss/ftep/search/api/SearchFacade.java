package com.cgi.eoss.ftep.search.api;

import com.cgi.eoss.ftep.catalogue.CatalogueService;
import com.cgi.eoss.ftep.model.FtepFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import lombok.extern.log4j.Log4j2;
import org.geojson.Feature;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Stream;

@Log4j2
public class SearchFacade {

    private final List<SearchProvider> searchProviders;
    private final ObjectMapper yamlMapper;
    private final String parametersSchemaFile;
    private final CatalogueService catalogueService;

    public SearchFacade(Collection<SearchProvider> searchProviders, String parametersSchemaFile, CatalogueService catalogueService) {
        this.searchProviders = ImmutableList.sortedCopyOf(searchProviders);
        this.catalogueService = catalogueService;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.parametersSchemaFile = parametersSchemaFile;
    }

    public SearchResults search(SearchParameters parameters) throws IOException {
        SearchProvider provider = getProvider(parameters);
        return provider.search(parameters);
    }

    public Map<String, Object> getParametersSchema() throws IOException {
        InputStream parametersFile = Strings.isNullOrEmpty(parametersSchemaFile)
                ? getClass().getResourceAsStream("parameters.yaml")
                : Files.newInputStream(Paths.get(parametersSchemaFile));

        return yamlMapper.readValue(ByteStreams.toByteArray(parametersFile), new TypeReference<Map>() {
        });
    }

    private SearchProvider getProvider(SearchParameters parameters) {
        return searchProviders.stream()
                .filter(sp -> sp.supports(parameters))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No search providers found for parameters: " + parameters));
    }

    public Resource getQuicklookResource(String productSource, String productIdentifier) throws IOException {
        SearchProvider provider = getQuicklooksProvider(productSource, productIdentifier);
        return provider.getQuicklook(productSource, productIdentifier);
    }

    private SearchProvider getQuicklooksProvider(String productSource, String productIdentifier) {
        return searchProviders.stream()
                .filter(sp -> sp.supportsQuicklook(productSource, productIdentifier))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No quicklook providers found for product: " + productIdentifier));
    }

    public Stream<FtepFile> findProducts(URI uri) {
        Stream<URI> uriStream = catalogueService.expand(uri);

        return uriStream
                .map(this::sanitiseUri)
                .flatMap(this::findProduct);
    }

    private Stream<FtepFile> findProduct(URI uri) {
        Optional<FtepFile> existing = catalogueService.get(uri);
        return existing.map(Stream::of)
                .orElseGet(() -> searchForAndCreateSatelliteProductReference(uri).map(Stream::of)
                        .orElse(Stream.empty()));
    }

    private URI sanitiseUri(URI uri) {
        // If the URI ends with .SAFE, trim it for consistency with older product URIs
        URI productUri;
        if (uri.toString().endsWith(".SAFE")) {
            productUri = URI.create(uri.toString().replace(".SAFE", ""));
        } else {
            productUri = uri;
        }
        return productUri;
    }

    public Optional<FtepFile> searchForAndCreateSatelliteProductReference(URI uri) {
        // Shortcut return things which are probably not identifiable products
        ImmutableSet<String> genericProtocols = ImmutableSet.of("http", "https", "file", "ftp", "ftep");
        if (Strings.isNullOrEmpty(uri.getPath()) || genericProtocols.contains(uri.getScheme())) {
            return Optional.empty();
        }

        try {
            String identifier = StringUtils.getFilename(uri.getPath())
                    .replace(".SAFE", "")
                    .replace(".zip", "");

            Map<String,String> constraints = getTimeConstraints(uri.getScheme(), identifier);
            constraints.put("catalogue", "SATELLITE");
            constraints.put("mission", uri.getScheme());
            constraints.put("identifier", identifier);
            SearchParameters searchParameters = new SearchParameters();
            searchParameters.setRequestUrl(SearchParameters.DEFAULT_REQUEST_URL);
            searchParameters.setParameters(ImmutableListMultimap.<String, String>builder()
                    .putAll(constraints.entrySet())
                    .build());
            
            List<Feature> features = search(searchParameters).getFeatures();

            if (features.size() != 1) {
                LOG.warn("Found zero or multiple products for URI {}", uri);
                return Optional.empty();
            } else {
                return Optional.of(catalogueService.indexExternalProduct(features.get(0)));
            }
        } catch (Exception e) {
            LOG.warn("Could not create FtepFile for URI {}", uri, e);
            return Optional.empty();
        }
    }

    private Map<String,String> getTimeConstraints(String mission, String productId) {
        Map<String,String> constraints = new HashMap<>();
        if (mission.equals("sentinel2") || mission.equals("sentinel1")) {
            // Limit the query by product date to make it faster
            String productDate = null;
            if (mission.equals("sentinel2")) {
                productDate = productId.substring(11, 19);
            } else if (mission.equals("sentinel1")) {
                String[] parts = productId.split("_");
                if (parts.length > 5 && parts[4].length() == 15) {
                    productDate = parts[4].substring(0, 8);
                }
            }
            if (productDate != null) {
                SimpleDateFormat sdfIn = new SimpleDateFormat("yyyyMMdd");
                sdfIn.setTimeZone(TimeZone.getTimeZone("UTC"));
                SimpleDateFormat sdfOut = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
                sdfOut.setTimeZone(TimeZone.getTimeZone("UTC"));
                try {
                    Date d = sdfIn.parse(productDate);
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(d);
                    cal.add(Calendar.DAY_OF_MONTH, 1);

                    String startDate = sdfOut.format(d);
                    String completionDate = sdfOut.format(cal.getTime());

                    constraints.put("productDateStart", startDate);
                    constraints.put("productDateEnd", completionDate);
                } catch (ParseException pe) {
                    LOG.error("Failed to parse date from: {}", productDate);
                }
            } else {
                LOG.error("Failed to parse date from: {}", productId);
            }
        }
        return constraints;
    }
}