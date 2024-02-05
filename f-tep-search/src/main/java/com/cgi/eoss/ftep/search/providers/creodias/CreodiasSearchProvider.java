package com.cgi.eoss.ftep.search.providers.creodias;

import com.cgi.eoss.ftep.catalogue.external.ExternalProductDataService;
import com.cgi.eoss.ftep.search.api.SearchParameters;
import com.cgi.eoss.ftep.search.api.SearchResults;
import com.cgi.eoss.ftep.search.providers.creodias.odata.ODataItem;
import com.cgi.eoss.ftep.search.providers.creodias.odata.ODataResult;
import com.cgi.eoss.ftep.search.providers.resto.RestoResult;
import com.cgi.eoss.ftep.search.providers.resto.RestoSearchProvider;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.geojson.Feature;
import org.springframework.core.io.Resource;
import org.springframework.hateoas.Link;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
public class CreodiasSearchProvider extends RestoSearchProvider {

    @Data
    @Builder
    private static final class MissionPlatform {

        private final String mission;
        private final String platform;
    }

    private static final BiMap<MissionPlatform, String> SUPPORTED_MISSIONS = ImmutableBiMap.<MissionPlatform, String>builder()
            .put(MissionPlatform.builder().mission("envisat").platform(null).build(), "ENVISAT")
            .put(MissionPlatform.builder().mission("landsat").platform("Landsat-5").build(), "LANDSAT-5")
            .put(MissionPlatform.builder().mission("landsat").platform("Landsat-7").build(), "LANDSAT-7")
            .put(MissionPlatform.builder().mission("landsat").platform("Landsat-8").build(), "LANDSAT-8")
            .put(MissionPlatform.builder().mission("sentinel1").platform(null).build(), "SENTINEL-1")
            .put(MissionPlatform.builder().mission("sentinel2").platform(null).build(), "SENTINEL-2")
            .put(MissionPlatform.builder().mission("sentinel3").platform(null).build(), "SENTINEL-3")
            .put(MissionPlatform.builder().mission("sentinel5").platform(null).build(), "SENTINEL-5P")
            .build();
    private static final Set<String> INTERNAL_FTEP_PARAMS = ImmutableSet.of(
            "catalogue",
            "mission",
            "platform"
    );
    private static final Map<String, String> PARAMETER_NAME_MAPPING = ImmutableMap.<String, String>builder()
            .put("semantic", "q")
            .put("aoi", "geometry")
            .put("s1ProcessingLevel", "processingLevel")
            .put("s2ProcessingLevel", "processingLevel")
            .put("s3ProcessingLevel", "processingLevel")
            .put("landsatProcessingLevel", "productType")
            .put("s1ProductType", "productType")
            .put("s1Platform", "platform")
            .put("productDateStart", "startDate")
            .put("productDateEnd", "completionDate")
            .put("maxCloudCover", "cloudCover")
            .put("identifier", "productIdentifier")
            .build();
    private static final Map<String, Function<String, String>> PARAMETER_VALUE_MAPPING = ImmutableMap.<String, Function<String, String>>builder()
            .put("s1ProcessingLevel", v -> "LEVEL" + v)
            .put("s2ProcessingLevel", v -> "S2MSI" + v)
            .put("landsatProcessingLevel", v -> { if (v.equals("GTC_1P")) {return v;} else {return "L" + v;} })
            .put("orbitDirection", String::toUpperCase)
            .build();

    private final int priority;
    private final boolean usableProductsOnly;
    private final ExternalProductDataService externalProductService;

    public CreodiasSearchProvider(int priority, CreodiasSearchProperties searchProperties, OkHttpClient httpClient, ObjectMapper objectMapper, ExternalProductDataService externalProductService) {
        super(searchProperties.getBaseUrl(),
                httpClient.newBuilder()
                        .addInterceptor(new HttpLoggingInterceptor(LOG::trace).setLevel(HttpLoggingInterceptor.Level.BODY))
                        .build(),
                objectMapper);
        this.priority = priority;
        this.usableProductsOnly = searchProperties.isUsableProductsOnly();
        this.externalProductService = externalProductService;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Map<String, String> getQueryParameters(SearchParameters parameters) {
        Map<String, String> queryParameters = new HashMap<>();

        if (usableProductsOnly) {
            queryParameters.put("status", "ONLINE");
        }

        parameters.getParameters().asMap().entrySet().stream()
                .filter(p -> !INTERNAL_FTEP_PARAMS.contains(p.getKey()) && !p.getValue().isEmpty() && !Strings.isNullOrEmpty(Iterables.getFirst(p.getValue(), null)))
                .forEach(p -> addTransformedParameter(queryParameters, p));

        return queryParameters;
    }

    private void addTransformedParameter(Map<String, String> queryParameters, Map.Entry<String, Collection<String>> parameter) {
        String parameterName = parameter.getKey();
        String parameterValue = Iterables.getFirst(parameter.getValue(), null);

        queryParameters.put(
                Optional.ofNullable(PARAMETER_NAME_MAPPING.get(parameterName)).orElse(parameterName),
                Optional.ofNullable(PARAMETER_VALUE_MAPPING.get(parameterName)).map(f -> f.apply(parameterValue)).orElse(parameterValue)
        );
    }

    @Override
    public boolean supports(SearchParameters parameters) {
        String catalogue = parameters.getValue("catalogue", "UNKNOWN");
        String mission = parameters.getValue("mission", "UNKNOWN");
        return catalogue.equals("SATELLITE")
                && (SUPPORTED_MISSIONS.keySet().stream().map(MissionPlatform::getMission).anyMatch(m -> m.equals(mission)) || mission.equals("httpcreodias"));
    }

    @Override
    public boolean supportsQuicklook(String productSource, String productIdentifier) {
        return SUPPORTED_MISSIONS.keySet().stream().map(MissionPlatform::getMission).anyMatch(mission -> mission.equals(productSource));
    }

    @Override
    public Resource getQuicklook(String productSource, String productIdentifier) throws IOException {
        throw new IOException("Not implemented");
    }

    @Override
    protected Map<String, SearchResults.Link> getLinks(HttpUrl requestUrl, SearchResults.Page page, RestoResult restoResult) {
        Map<String, SearchResults.Link> links = new HashMap<>();

        links.putAll(getPagingLinks(page, requestUrl));

        return links;
    }

    @Override
    protected String getCollection(SearchParameters parameters) {
        MissionPlatform missionPlatform = getMissionPlatform(parameters);
        String mission = parameters.getValue("mission", "UNKNOWN");
        if (SUPPORTED_MISSIONS.containsKey(missionPlatform)) {
            return SUPPORTED_MISSIONS.get(missionPlatform);
        } else if (parameters.getValue("mission").isPresent()) {
            // We know it's here *somewhere*...
            return "";
        } else {
            throw new IllegalArgumentException("Could not identify CREODIAS Resto collection for mission: " + missionPlatform);
        }
    }

    private MissionPlatform getMissionPlatform(SearchParameters parameters) {
        return MissionPlatform.builder()
                .mission(parameters.getValue("mission", "UNKNOWN"))
                .platform(parameters.getValue("platform", null))
                .build();
    }

    @Override
    protected SearchResults postProcess(SearchResults results) {
        if (!results.getParameters().isNested()) {
            List<Feature> features = results.getFeatures().stream()
                    .map(f -> addFtepProperties(f, results.getParameters()))
                    .collect(Collectors.toList());
            results.setFeatures(features);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private Feature addFtepProperties(Feature feature, SearchParameters parameters) {

        String collection = feature.getProperty("collection");
        String productSource = SUPPORTED_MISSIONS.inverse().get(collection).getMission();
        String productIdentifier = ((String) feature.getProperty("name")).replace(".SAFE", "");
        URI ftepUri = externalProductService.getUri(productSource, productIdentifier);

        if (isSentinel2ForL2A(parameters)) {
            Optional<String> productIdentifierForL2A = getL2AProductIdentifierOData(productIdentifier);

            if (productIdentifierForL2A.isPresent()) {
                // Use the L2A URI
                try {
                    feature = searchNestedL2A(productIdentifierForL2A.get());

                    // Update values
                    collection = feature.getProperty("collection");
                    productSource = SUPPORTED_MISSIONS.inverse().get(collection).getMission();
                    productIdentifier = ((String) feature.getProperty("name")).replace(".SAFE", "");
                    ftepUri = externalProductService.getUri(productSource, productIdentifier);

                } catch (Exception e) {
                    LOG.info("Unable to find a unique L2A product for identifier {}", productIdentifierForL2A.get());
                    feature.setProperty("processingRequired", true);
                    ftepUri = UriComponentsBuilder.fromUriString(ftepUri.toString()).queryParam("L2A", true).build().toUri();
                }
            } else {
                // L2A not present, use the L1C URI with the query parameter L2A=true to order it
                feature.setProperty("processingRequired", true);
                ftepUri = UriComponentsBuilder.fromUriString(ftepUri.toString()).queryParam("L2A", true).build().toUri();
            }
        }

        boolean ftepUsable = feature.getProperty("online"); 

        // Shuffle the CREODIAS properties into a sub-object for consistency across all search providers
        Map<String, Object> extraParams = new HashMap<>(feature.getProperties());
        feature.getProperties().clear();

        Set<Link> featureLinks = new HashSet<>();
        featureLinks.add(new Link(ftepUri.toASCIIString(), "ftep"));

        Long filesize = Long.valueOf(extraParams.get("contentLength").toString());

        // Required parameters for FtepFile ingestion
        feature.setProperty("productSource", productSource);
        feature.setProperty("productIdentifier", productIdentifier);
        feature.setProperty("ftepUrl", ftepUri);
        feature.setProperty("filesize", filesize);
        feature.setProperty("ftepUsable", ftepUsable);

        // Set "interesting" parameters which clients might want in an easily-accessible form
        // Some are not present depending on the result type, so we have to safely traverse the dynamic properties map
        // These are added to extraParams so that the FtepFile/Resto schema is predictable
        Optional.ofNullable(extraParams.get("contentDateStart"))
                .ifPresent(startDate -> extraParams.put("ftepStartTime", startDate));
        Optional.ofNullable(extraParams.get("contentDateEnd"))
                .ifPresent(completionDate -> extraParams.put("ftepEndTime", completionDate));
        Optional.ofNullable(extraParams.get("cloudCover"))
                .ifPresent(cloudCoverage -> extraParams.put("ftepCloudCoverage", cloudCoverage));
        Optional.ofNullable(extraParams.get("orbitDirection"))
                .ifPresent(orbitDirection -> extraParams.put("ftepOrbitDirection", orbitDirection));
        Optional.ofNullable(extraParams.get("productType"))
                .ifPresent(productType -> extraParams.put("ftepProductType", productType));
        Optional.ofNullable(extraParams.get("modificationDate"))
                .ifPresent(updated -> extraParams.put("ftepUpdated", updated));
        Optional.ofNullable(extraParams.get("publicationDate"))
                .ifPresent(published -> extraParams.put("ftepUpdated", published));
        feature.setProperty("extraParams", extraParams);

        // TODO Find an abstract quicklook representation
        Optional.ofNullable(extraParams.get("thumbnail"))
                .ifPresent(quicklookURL -> featureLinks.add(new Link(quicklookURL.toString(), "quicklook")));

        feature.setProperty("_links", featureLinks.stream().collect(Collectors.toMap(
                Link::getRel,
                l -> ImmutableMap.of("href", l.getHref())
        )));
        return feature;
    }

    private Optional<String> getL2AProductIdentifierOData(String productIdentifier) {
        // Beginning of the corresponding L2A product identifier name
        // Change processing level and remove processing time
        String substringForL2A = productIdentifier.substring(0, 44).replaceFirst("L1C", "L2A");
        // Change processing baseline
        String namePart1 = substringForL2A.substring(0, 28);
        String namePart2 = substringForL2A.substring(32);

        String timeStr = namePart1.split("_")[2];
        String year = timeStr.substring(0, 4);
        String month = timeStr.substring(4, 6);
        String day = timeStr.substring(6, 8);

        String filter = "Collection/Name eq 'SENTINEL-2'";
        filter += " and contains(Name,'" + namePart1 + "') and contains(Name, '" + namePart2 + "')";
        filter += " and ContentDate/Start ge " + year + "-" + month + "-" + day + "T00:00:00.000Z";
        filter += " and ContentDate/Start le " + year + "-" + month + "-" + day + "T23:59:59.000Z";

        // Need to remove the /resto part from the base URL!
        HttpUrl.Builder httpUrl = baseUrl.newBuilder().removePathSegment(0).addPathSegments("odata/v1/Products");
        httpUrl.addQueryParameter("$filter", filter);

        Request request = new Request.Builder().url(httpUrl.build()).get().build();

        LOG.debug("Performing HTTP request for OData search: {}", httpUrl);

        try ( Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOG.error("Received unsuccessful HTTP response for OData search: {}", response.toString());
                throw new IOException("Unexpected HTTP response code from OData: " + response);
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonFactory factory = mapper.getFactory();
            JsonParser parser = factory.createParser(response.body().string());
            JsonNode jsonRoot = mapper.readTree(parser);
            JsonNode valueNode = jsonRoot.findValue("value");
            if (valueNode.isArray()) {
                if (valueNode.size() == 0) {
                    LOG.info("No match found for product with identifier {}", productIdentifier);
                } else {
                    if (valueNode.size() != 1) {
                        LOG.warn("No unique match found for product with identifier {}", productIdentifier);
                    }
                    JsonNode item = valueNode.get(0);
                    String value = item.findValue("Name").asText();

                    return Optional.of(value);
                }
            }
        } catch (Exception e) {
            LOG.error("Could not perform HTTP request for OData search at {}", request.url(), e);
        }

        return Optional.empty();
    }

    private Feature searchNestedL2A(String productIdentifier) throws IOException {
        // Perform another search for this particular L2A product
        String[] parts = productIdentifier.split("/");
        String namePart = parts[parts.length - 1];
        String timeStr = namePart.split("_")[2];
        String year = timeStr.substring(0, 4);
        String month = timeStr.substring(4, 6);
        String day = timeStr.substring(6, 8);

        SearchParameters newL2ASearchParams = new SearchParameters();
        newL2ASearchParams.setRequestUrl(SearchParameters.DEFAULT_REQUEST_URL);
        newL2ASearchParams.setParameters(ImmutableListMultimap.<String, String>builder()
                .put("mission", "sentinel2")
                .put("productIdentifier", productIdentifier)
                .put("startDate", year + "-" + month + "-" + day + "T00:00:00Z")
                .put("completionDate", year + "-" + month + "-" + day + "T23:59:59Z")
                .build());
        newL2ASearchParams.setNested(true);

        SearchResults results = search(newL2ASearchParams);
        List<Feature> newFeatures = results.getFeatures();
        if (newFeatures.size() != 1) {
            LOG.warn("No unique match found for product with identifier {}", productIdentifier);
        }
        return newFeatures.get(0);
    }

    @Override
    public SearchResults search(SearchParameters parameters) throws IOException {
        boolean searchL2A = false;
        if (isSentinel2ForL2A(parameters)) {
            searchL2A = true;
            parameters.setValue("s2ProcessingLevel", "1C");
        }

        String collectionName = getCollection(parameters);

        // Need to remove the /resto part from the base URL!
        HttpUrl.Builder httpUrl = baseUrl.newBuilder().removePathSegment(0).addPathSegments("odata/v1/Products");
        // Add paging
        getPagingParameters(parameters).forEach(httpUrl::addQueryParameter);
        // Add count
        httpUrl.addQueryParameter("$count", "true");
        // Add attributes
        httpUrl.addQueryParameter("$expand", "Attributes");
        // Build filter from query parameters
        String filter = "Collection/Name eq '" + collectionName + "'";

        Map<String, String> queryParams = getQueryParameters(parameters);
        //LOG.info(queryParams);

        if (queryParams.containsKey("productIdentifier")) {
            filter += " and contains(Name,'" + queryParams.get("productIdentifier") + "')";
        }
        if (queryParams.containsKey("startDate")) {
            filter += " and ContentDate/Start ge " + queryParams.get("startDate");
        }
        if (queryParams.containsKey("completionDate")) {
            filter += " and ContentDate/Start le " + queryParams.get("completionDate");
        }
        if (queryParams.containsKey("cloudCover")) {
            filter += " and Attributes/OData.CSC.DoubleAttribute/any(att:att/Name eq 'cloudCover' and att/OData.CSC.DoubleAttribute/Value le "
                    + queryParams.get("cloudCover") + ")";
        }
        if (queryParams.containsKey("processingLevel")) {
            filter += " and Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'processingLevel' and att/OData.CSC.StringAttribute/Value eq '" 
                    + queryParams.get("processingLevel") + "')";
        }
        if (queryParams.containsKey("productType")) {
            filter += " and Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'productType' and att/OData.CSC.StringAttribute/Value eq '" 
                    + queryParams.get("productType") + "')";
        }
        if (queryParams.containsKey("platform")) {
            filter += " and Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'platformSerialIdentifier' and att/OData.CSC.StringAttribute/Value eq '" 
                    + queryParams.get("platform").replace("S1","") + "')";
        }
        if (queryParams.containsKey("orbitDirection")) {
            filter += " and Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'orbitDirection' and att/OData.CSC.StringAttribute/Value eq '" 
                    + queryParams.get("orbitDirection") + "')";
        }
        // Bounding box search:
        if (queryParams.containsKey("geometry")) {
            filter += " and OData.CSC.Intersects(area=geography'SRID=4326;" 
                    + queryParams.get("geometry") + "')";
        }
        httpUrl.addQueryParameter("$filter", filter);

        httpUrl.addQueryParameter("$orderby", "ContentDate/Start desc");

        Request request = new Request.Builder().url(httpUrl.build()).get().build();

        LOG.debug("Performing HTTP request for OData search: {}", httpUrl);

        try ( Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOG.error("Received unsuccessful HTTP response for Resto search: {}", response.toString());
                throw new IOException("Unexpected HTTP response code from Resto: " + response);
            }
            LOG.info("Received successful response for Resto search: {}", request.url());
            ODataResult oDataResult = objectMapper.readValue(response.body().string(), ODataResult.class);

            SearchResults.Page page = getPageInfo(parameters, oDataResult);
            // Set the processing level back to L2A if applicable
            if (searchL2A) {
                parameters.setValue("s2ProcessingLevel", "2A");
            }
            List<Feature> features = new ArrayList<>();
            for (ODataItem item:oDataResult.getValues()) {
                Feature f = item.toFeature();
                f.setProperty("collection", collectionName);
                features.add(f);
            }
            return postProcess(SearchResults.builder()
                    .parameters(parameters)
                    .page(page)
                    .features(features)
                    .links(getPagingLinks(page, parameters.getRequestUrl()))
                    .build());
            
        } catch (Exception e) {
            LOG.error("Could not perform HTTP request for Resto search at {}", request.url(), e);
            throw e;
        }
    }

    private boolean isSentinel2ForL2A(SearchParameters parameters) {
        // Ignore for nested L2A calls
        if (parameters.isNested()) {
            return false;
        }
        if (parameters.getKeys().contains("s2ProcessingLevel")) {
            Optional<String> level = parameters.getValue("s2ProcessingLevel");
            return level.isPresent() && level.get().equals("2A");
        }
        return false;
    }

    @Override
    public Map<String, String> getPagingParameters(SearchParameters parameters) {
        Map<String, String> pagingParameters = new HashMap<>();
        pagingParameters.put("$top", Integer.toString(parameters.getResultsPerPage()));
        pagingParameters.put("$skip", Integer.toString(parameters.getPage() * parameters.getResultsPerPage()));
        return pagingParameters;
    }

    protected SearchResults.Page getPageInfo(SearchParameters parameters, ODataResult result) {
        long queryResultsPerPage = parameters.getResultsPerPage();
        long queryPage = parameters.getPage();
        long totalResultsCount = result.getCount();
        long totalPages = (long)Math.ceil(totalResultsCount / (double)queryResultsPerPage);

        return SearchResults.Page.builder()
                .totalElements(totalResultsCount)
                .size(queryResultsPerPage)
                .number(queryPage)
                .totalPages(totalPages)
                .build();
    }
    
}
