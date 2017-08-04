package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.model.Job;
import com.cgi.eoss.ftep.rpc.GrpcUtil;
import com.cgi.eoss.ftep.rpc.LocalServiceLauncher;
import com.cgi.eoss.ftep.rpc.StopServiceParams;
import com.cgi.eoss.ftep.rpc.StopServiceResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.text.StrSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@BasePathAwareController
@RequestMapping("/jobs")
@Transactional
@Log4j2
public class JobsApiExtension {

    // TODO Make configurable
    @Value("${ftep.api.logs.graylogApiQuery:contextStack%3A%22%5BIn-Docker%2C%20F-TEP%20Worker%5D%22%20AND%20zooId%3A@{zooId}}")
    private String dockerJobLogQuery;

    @Value("${ftep.api.logs.graylogApiUrl:http://ftep-monitor:8087/log/api}")
    private String graylogApiUrl;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LocalServiceLauncher localServiceLauncher;

    @Autowired
    public JobsApiExtension(@Value("${ftep.api.logs.username:admin}") String username,
                            @Value("${ftep.api.logs.password:graylogpass}") String password,
                            LocalServiceLauncher localServiceLauncher) {
        this.httpClient = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Request authenticatedRequest = request.newBuilder()
                                .header("Authorization", Credentials.basic(username, password))
                                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                                .build();
                        return chain.proceed(authenticatedRequest);
                    }
                })
                .addInterceptor(new HttpLoggingInterceptor(LOG::trace).setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();
        this.objectMapper = new ObjectMapper();
        this.localServiceLauncher = localServiceLauncher;
    }

    @GetMapping("/{jobId}/logs")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#job, 'read')")
    @ResponseBody
    public List<SimpleMessage> getJobContainerLogs(@ModelAttribute("jobId") Job job) throws IOException {
        Map<String, String> parameters = ImmutableMap.<String, String>builder()
                .put("range", "0")
                .put("sort", "timestamp%3Aasc")
                .put("decorate", "false")
                .put("fields", "timestamp%2Cmessage")
                .put("query", StrSubstitutor.replace(dockerJobLogQuery, ImmutableMap.of("zooId", job.getExtId()), "@{", "}"))
                .build();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(graylogApiUrl).newBuilder()
                .addPathSegments("search/universal/relative");

        parameters.forEach(urlBuilder::addEncodedQueryParameter);

        Request request = new Request.Builder()
                .get()
                .url(urlBuilder.build())
                .build();

        LOG.debug("Retrieving job {} logs from url: {}", job.getId(), request.url());
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return objectMapper.readValue(response.body().string(), GraylogApiResponse.class).getMessages().stream()
                        .map(GraylogMessage::getMessage)
                        .collect(Collectors.toList());
            } else {
                LOG.error("Failed to retrieve job {} logs: {} -- {}", job.getId(), response.code(), response.message());
                LOG.debug("Graylog response: {}", response.body());
                return ImmutableList.of();
            }
        }
    }

    @PostMapping("/{jobId}/terminate")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#job, 'write')")
    public ResponseEntity stop(@ModelAttribute("jobId") Job job) throws InterruptedException {
        StopServiceParams stopServiceParams = StopServiceParams.newBuilder()
                .setJob(GrpcUtil.toRpcJob(job))
                .build();

        final CountDownLatch latch = new CountDownLatch(1);
        JobStopObserver responseObserver = new JobStopObserver(latch);

        localServiceLauncher.asyncStopService(stopServiceParams, responseObserver);

        latch.await(1, TimeUnit.MINUTES);
        return ResponseEntity.noContent().build();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class GraylogApiResponse {
        private List<GraylogMessage> messages;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class GraylogMessage {
        private SimpleMessage message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class SimpleMessage {
        private String timestamp;
        private String message;
    }

    private static final class JobStopObserver implements StreamObserver<StopServiceResponse> {
        private final CountDownLatch latch;

        JobStopObserver(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onNext(StopServiceResponse value) {
            LOG.debug("Received StopServiceResponse: {}", value);
            latch.countDown();
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("Failed to stop service via REST API", t);
        }

        @Override
        public void onCompleted() {
            // No-op, the user has long stopped listening here
        }
    }

}
