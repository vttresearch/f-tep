package com.cgi.eoss.ftep.io.download;

import com.cgi.eoss.ftep.io.ServiceIoException;
import com.cgi.eoss.ftep.io.ServiceIo429Exception;
import com.cgi.eoss.ftep.logging.Logging;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.jayway.jsonpath.JsonPath;
import lombok.Builder;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * <p>Downloader for accessing data from <a href="https://finder.eocloud.eu">EO Cloud</a>. Uses IPT's token
 * authentication process.</p>
 */
@Log4j2
public class CreodiasHttpDownloader implements Downloader {

    private static final Multimap<String, String> PROTOCOL_COLLECTIONS = ImmutableMultimap.<String, String>builder()
            .put("sentinel1", "Sentinel1")
            .put("sentinel2", "Sentinel2")
            .put("sentinel3", "Sentinel3")
            .put("sentinel5", "Sentinel5P")
            .put("landsat", "Landsat5")
            .put("landsat", "Landsat7")
            .put("landsat", "Landsat8")
            .put("envisat", "Envisat")
            .build();

    private final OkHttpClient httpClient;
    private final OkHttpClient searchClient;
    private final DownloaderFacade downloaderFacade;
    private final Properties properties;
    private final ProtocolPriority protocolPriority;
    private final CreodiasOrderer creodiasOrderer;

    private final int WAITING_FOR_DOWNLOAD_STATUS = 31;
    private final int ORDERED_STATUS = 32;

    public CreodiasHttpDownloader(OkHttpClient okHttpClient, int downloadTimeout, int searchTimeout, CreodiasHttpAuthenticator authenticator, KeyCloakTokenGenerator keyCloakTokenGenerator, DownloaderFacade downloaderFacade, Properties properties, ProtocolPriority protocolPriority) {
        // Use a long timeout as the data access can be slow
        this.httpClient = okHttpClient.newBuilder()
                .connectTimeout(downloadTimeout, TimeUnit.SECONDS)
                .readTimeout(downloadTimeout, TimeUnit.SECONDS)
                .authenticator(authenticator)
                .build();
        // Use a long timeout as the search query takes a while...
        this.searchClient = okHttpClient.newBuilder().readTimeout(searchTimeout, TimeUnit.SECONDS).build();
        this.downloaderFacade = downloaderFacade;
        this.properties = properties;
        this.protocolPriority = protocolPriority;
        this.creodiasOrderer = new CreodiasOrderer(httpClient, keyCloakTokenGenerator);
    }

    @PostConstruct
    public void postConstruct() {
        downloaderFacade.registerDownloader(this);
    }

    @PreDestroy
    public void preDestroy() {
        downloaderFacade.unregisterDownloader(this);
    }

    @Override
    public Set<String> getProtocols() {
        return PROTOCOL_COLLECTIONS.keySet();
    }

    @Override
    public int getPriority(URI uri) {
        return protocolPriority.get(uri.getScheme());
    }

    @Override
    public Path download(Path targetDir, URI uri) throws IOException {
        LOG.info("Downloading: {}", uri);

        // Ensure an L2A product is ordered if necessary
        HttpUrl downloadUrl;
        if (uri.getQuery() != null && uri.getQuery().contains("L2A=true")) {
            try {
                downloadUrl = getDownloadUrl(uri);
            } catch (Exception e) {
                orderProduct(uri);
                downloadUrl = getDownloadUrl(uri);
            }
        } else {
            downloadUrl = getDownloadUrl(uri);
        }

        LOG.debug("Resolved CREODIAS download URL with auth token: {}", downloadUrl);

        Request request = new Request.Builder().url(downloadUrl).build();
        Path outputFile;

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                checkTooManyRequests(response);
                throw new ServiceIoException("Unsuccessful HTTP response: " + response);
            }

            String filename = Iterables.getLast(downloadUrl.pathSegments()) + ".zip";
            outputFile = targetDir.resolve(filename);

            try (BufferedSource source = response.body().source();
                 BufferedSink sink = Okio.buffer(Okio.sink(outputFile))) {
                long downloadedBytes = sink.writeAll(source);
                LOG.debug("Downloaded {} bytes for {}", downloadedBytes, uri);
            }
        }

        LOG.info("Successfully downloaded via CREODIAS: {}", outputFile);
        return outputFile;
    }

    private void checkTooManyRequests(Response response) throws ServiceIo429Exception {
        if (response != null && response.code() == 429) {
            double seconds = 30; // Default unless the server gives a delay time
            if (response.header("Retry-After") != null) {
                try {
                    seconds = Double.valueOf(response.header("Retry-After"));
                } catch (NumberFormatException e) {
                    LOG.info("Failed to parse Retry-After to seconds: {}", response.header("Retry-After"));
                }
            }
            throw new ServiceIo429Exception("Too many requests response from CREODIAS", seconds);
        }
    }

    private HttpUrl getDownloadUrl(URI uri) {
        List<HttpUrl> searchUrls = PROTOCOL_COLLECTIONS.get(uri.getScheme()).stream()
                .map(collection -> buildSearchUrl(collection, uri))
                .collect(Collectors.toList());

        for (HttpUrl searchUrl : searchUrls) {
            try {
                return findDownloadUrl(uri, searchUrl);
            } catch (ServiceIo429Exception e) {
		throw(e);
            } catch (Exception e) {
                LOG.debug("Failed to locate download URL from search url {}: {}", searchUrl, e.getMessage());
            }
        }
        throw new ServiceIoException("Unable to locate CREODIAS product data for " + uri);
    }

    private HttpUrl findDownloadUrl(URI uri, HttpUrl searchUrl) throws IOException {
        Request request = new Request.Builder().url(searchUrl).get().build();

        try (Response response = searchClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                checkTooManyRequests(response);
                LOG.error("Received unsuccessful HTTP response for CREODIAS search: {}", response.toString());
                throw new ServiceIoException("Unexpected HTTP response from CREODIAS: " + response.message());
            }

            String responseBody = response.body().string();

            boolean emptyResults = ((Integer) JsonPath.read(responseBody, "$.features.length()")) == 0;
            if (emptyResults) {
                throw new NoSuchElementException();
            }

            String productId = JsonPath.read(responseBody, "$.features[0].id");
            int status = JsonPath.read(responseBody, "$.features[0].properties.status");

            // If the status is 31 or 32, the product is offline and must be ordered from CREODIAS
            if (status == WAITING_FOR_DOWNLOAD_STATUS || status == ORDERED_STATUS) {
                orderProduct(uri);
            }

            return HttpUrl.parse(properties.getCreodiasDownloadUrl()).newBuilder()
                    .addPathSegments(productId)
                    .build();
        } catch (SocketTimeoutException timeout) {
            Logging.withUserLoggingContext(() -> LOG.error("Timeout locating EO product data for {}; please try again and contact the F-TEP support team if the error persists", uri));
            throw new ServiceIoException("Timeout locating CREODIAS product data for " + uri);
        }
    }

    private void orderProduct(URI uri) {
        HttpUrl orderUrl = HttpUrl.parse(properties.getCreodiasOrderUrl());
        creodiasOrderer.orderProduct(uri, orderUrl);
    }

    private HttpUrl buildSearchUrl(String collection, URI uri) {
        // Trim the leading slash from the path and get the search URL
        String productId = uri.getPath().substring(1);
        if (uri.getQuery() != null && uri.getQuery().contains("L2A=true")) {
            // URI is of the format S2A_MSIL1C_20190918T114351_N0208_R123_T29UNV_20190918T183519?L2A=true
            // Change processing level and remove processing time
            productId = productId.replaceFirst("L1C", "L2A").substring(0, 44);
            // Change processing baseline
            productId = productId.substring(0, 28) + "%" + productId.substring(32);
        }
        if (collection.equals("Sentinel2") || collection.equals("Sentinel1")) {
            // Limit the query by product date to make it faster
            String productDate = null;
            if (collection.equals("Sentinel2")) {
                productDate = productId.substring(11, 19);
            } else if (collection.equals("Sentinel1")) {
                String[] parts = productId.split("_");
                if (parts.length > 5 && parts[4].length() == 15) {
                    productDate = parts[4].substring(0, 8);
                }
                // Sentinel1 search without .SAFE returns multiple features
                // for some images
                productId += ".SAFE";
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

                    return HttpUrl.parse(properties.getCreodiasSearchUrl()).newBuilder()
                            .addPathSegments("api/collections")
                            .addPathSegment(collection)
                            .addPathSegment("search.json")
                            .addQueryParameter("maxRecords", "1")
                            .addQueryParameter("productIdentifier", "%" + productId + "%")
                            .addQueryParameter("status", "all")
                            .addQueryParameter("startDate", startDate)
                            .addQueryParameter("completionDate", completionDate)
                            .build();
                } catch (ParseException pe) {
                    LOG.error("Failed to parse date from: {}", productDate);
                }
            } else {
                LOG.error("Failed to parse date from: {}", productId);
            }
        }
        return HttpUrl.parse(properties.getCreodiasSearchUrl()).newBuilder()
                .addPathSegments("api/collections")
                .addPathSegment(collection)
                .addPathSegment("search.json")
                .addQueryParameter("maxRecords", "1")
                .addQueryParameter("productIdentifier", "%" + productId + "%")
                .addQueryParameter("status", "all")
                .build();
    }

    @Value
    @Builder
    public static final class Properties {
        private String creodiasSearchUrl;
        private String creodiasDownloadUrl;
        private String creodiasOrderUrl;
    }

}
