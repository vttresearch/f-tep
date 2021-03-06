package com.cgi.eoss.ftep.io.download;

import com.cgi.eoss.ftep.io.ServiceIoException;
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
import okio.Okio;
import org.apache.logging.log4j.CloseableThreadContext;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>Downloader for accessing data from <a href="https://finder.eocloud.eu">EO Cloud</a> using our data gateway.</p>
 */
@Log4j2
public class IptEodataServerDownloader implements Downloader {

    private static final Multimap<String, String> PROTOCOL_COLLECTIONS = ImmutableMultimap.<String, String>builder()
            .put("sentinel1", "Sentinel1")
            .put("sentinel2", "Sentinel2")
            .put("sentinel3", "Sentinel3")
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

    public IptEodataServerDownloader(OkHttpClient okHttpClient, int downloadTimeout, int searchTimeout, IptEodataServerAuthenticator iptEodataServerAuthenticator, DownloaderFacade downloaderFacade, Properties properties, ProtocolPriority protocolPriority, KeyCloakTokenGenerator keyCloakTokenGenerator) {
        // Use a long timeout as the data access can be slow
        this.httpClient = okHttpClient.newBuilder()
                .connectTimeout(downloadTimeout, TimeUnit.SECONDS)
                .readTimeout(downloadTimeout, TimeUnit.SECONDS)
                .authenticator(iptEodataServerAuthenticator)
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

        HttpUrl downloadUrl = getDownloadUrl(uri);

        LOG.debug("Resolved IPT download URL: {}", downloadUrl);

        String filename = Iterables.getLast(downloadUrl.pathSegments()) + ".zip";
        Path outputFile = targetDir.resolve(filename);

        Request request = new Request.Builder().url(downloadUrl).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ServiceIoException("Unsuccessful HTTP response: " + response);
            }

            try (BufferedSink sink = Okio.buffer(Okio.sink(outputFile))) {
                long downloadedBytes = sink.writeAll(response.body().source());
                LOG.debug("Downloaded {} bytes for {}", downloadedBytes, uri);
            }
        }

        LOG.info("Successfully downloaded via IPT: {}", outputFile);
        return outputFile;
    }

    private HttpUrl getDownloadUrl(URI uri) throws IOException {
        // Trim the leading slash from the path and get the search URL
        //String productId = uri.getPath().substring(1);

        List<HttpUrl> searchUrls = PROTOCOL_COLLECTIONS.get(uri.getScheme()).stream()
                .map(collection -> buildSearchUrl(collection, uri))
                .collect(Collectors.toList());

        for (HttpUrl searchUrl : searchUrls) {
            try {
                return findDownloadUrl(uri, searchUrl);
            } catch (Exception e) {
                LOG.debug("Failed to locate download URL: {}", e.getMessage());
            }
        }
        throw new ServiceIoException("Unable to locate IPT product data for " + uri);
    }

    private HttpUrl findDownloadUrl(URI uri, HttpUrl searchUrl) throws IOException {
        Request request = new Request.Builder().url(searchUrl).get().build();

        try (Response response = searchClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOG.error("Received unsuccessful HTTP response for IPT search: {}", response.toString());
                throw new ServiceIoException("Unexpected HTTP response from IPT: " + response.message());
            }

            String responseBody = response.body().string();
            String productPath = ((String) JsonPath.read(responseBody, "$.features[0].properties.productIdentifier")).replaceFirst("^/", "");
            int status = JsonPath.read(responseBody, "$.features[0].properties.status"); // TODO: correct?

            // If the status is 31 or 32, the product is offline and must be ordered from CREODIAS
            if (status == WAITING_FOR_DOWNLOAD_STATUS || status == ORDERED_STATUS) {
                HttpUrl orderUrl = HttpUrl.parse(properties.getIptOrderUrl());
                creodiasOrderer.orderProduct(uri, orderUrl);
            }

            return HttpUrl.parse(properties.getIptDownloadUrl()).newBuilder()
                    .addPathSegments(productPath)
                    .build();
        } catch (SocketTimeoutException timeout) {
            try (CloseableThreadContext.Instance userCtc = Logging.userLoggingContext()) {
                LOG.error("Timeout locating EO product data for {}; please try again and contact the F-TEP support team if the error persists", uri);
            }
            throw new ServiceIoException("Timeout locating IPT product data for " + uri);
        }
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

        return HttpUrl.parse(properties.getIptSearchUrl()).newBuilder()
                .addPathSegments("api/collections")
                .addPathSegment(collection)
                .addPathSegment("search.json")
                .addQueryParameter("maxRecords", "1")
                .addQueryParameter("productIdentifier", "%" + productId + "%")
                .build();
    }

    @Value
    @Builder
    public static final class Properties {
        private String iptSearchUrl;
        private String iptDownloadUrl;
        private String iptOrderUrl;
    }

}
