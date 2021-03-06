package com.cgi.eoss.ftep.orchestrator.service;

import com.cgi.eoss.ftep.costing.CostingService;
import com.cgi.eoss.ftep.model.JobConfig;
import com.cgi.eoss.ftep.model.SystematicProcessing;
import com.cgi.eoss.ftep.model.SystematicProcessing.Status;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.persistence.service.SystematicProcessingDataService;
import com.cgi.eoss.ftep.rpc.FtepServiceParams;
import com.cgi.eoss.ftep.rpc.GrpcUtil;
import com.cgi.eoss.ftep.rpc.LocalServiceLauncher;
import com.cgi.eoss.ftep.rpc.SubmitJobResponse;
import com.cgi.eoss.ftep.search.api.SearchFacade;
import com.cgi.eoss.ftep.search.api.SearchParameters;
import com.cgi.eoss.ftep.search.api.SearchResults;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>Service for autoscaling the number of worker nodes based on queue length</p>
 */
@Log4j2
@Service
@ConditionalOnProperty(name = "ftep.server.systematicscheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SystematicProcessingScheduler {

    private final SearchFacade searchFacade;
    private final SystematicProcessingDataService systematicProcessingDataService;
    private final LocalServiceLauncher localServiceLauncher;
    private final CostingService costingService;

    private static final long SYSTEMATIC_PROCESSING_CHECK_RATE_MS = 60 * 60 * 1000L;

    @Autowired
    public SystematicProcessingScheduler(SystematicProcessingDataService systematicProcessingDataService, SearchFacade searchFacade,
        LocalServiceLauncher localServiceLauncher, CostingService costingService) {
        this.systematicProcessingDataService = systematicProcessingDataService;
        this.searchFacade = searchFacade;
        this.localServiceLauncher = localServiceLauncher;
        this.costingService = costingService;
    }

    @Scheduled(fixedRate = SYSTEMATIC_PROCESSING_CHECK_RATE_MS, initialDelay = 10000L)
    public void updateSystematicProcessings() {
        List<SystematicProcessing> activeSystematicProcessings = systematicProcessingDataService.findByStatus(Status.ACTIVE);
        List<SystematicProcessing> blockedSystematicProcessings = systematicProcessingDataService.findByStatus(Status.BLOCKED);

        for (SystematicProcessing activeSystematicProcessing : activeSystematicProcessings) {
            updateSystematicProcessing(activeSystematicProcessing);
        }

        // Try to resume blocked systematic processings
        for (SystematicProcessing blockedSystematicProcessing : blockedSystematicProcessings) {
            User user = blockedSystematicProcessing.getOwner();
            if (user.getWallet().getBalance() > 0) {
                blockedSystematicProcessing.setStatus(Status.ACTIVE);
                systematicProcessingDataService.save(blockedSystematicProcessing);
                updateSystematicProcessing(blockedSystematicProcessing);
            }
        }
    }

    public void updateSystematicProcessing(SystematicProcessing systematicProcessing) {
        ListMultimap<String, String> queryParameters = systematicProcessing.getSearchParameters();
        queryParameters.replaceValues("publishedAfter", Arrays.asList(new String[] {
            ZonedDateTime.of(systematicProcessing.getLastUpdated(), ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }));
        queryParameters.replaceValues("publishedBefore", Arrays.asList(new String[]{ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}));
        queryParameters.replaceValues("sortOrder", Arrays.asList(new String[]{"ascending"}));
        queryParameters.replaceValues("sortParam", Arrays.asList(new String[]{"published"}));
        int page = 0;
        HttpUrl requestUrl = new HttpUrl.Builder().scheme("http").host("local").build();
        SearchResults results;
        JobConfig configTemplate = systematicProcessing.getParentJob().getConfig();
		SystematicProcessing freshSystematicProcessing;
        try {
            do {
                results = getSearchResultsPage(requestUrl, page, queryParameters);
                for (Feature feature : results.getFeatures()) {
					// Check that the systematic processing is still active:
					// Cancelling the systematic processing while it is processing search results does not affect 
					// the current instance so a fresh instance has to be retrieved from the persistent storage
					freshSystematicProcessing = systematicProcessingDataService.getById(systematicProcessing.getId());
					if (freshSystematicProcessing.getStatus() == Status.COMPLETED) {
						systematicProcessing.setStatus(Status.COMPLETED);
						return;
					}
					
                    String url = feature.getProperties().get("ftepUrl").toString();
                    Multimap<String, String> inputs = ArrayListMultimap.create();
                    inputs.putAll(configTemplate.getInputs());
                    inputs.replaceValues(configTemplate.getSystematicParameter(), Arrays.asList(new String[]{url}));
                    configTemplate.getInputs().put(configTemplate.getSystematicParameter(), url);
                    int jobCost = costingService.estimateJobCost(configTemplate);
                    if (results.getFeatures().size() * jobCost > systematicProcessing.getOwner().getWallet().getBalance()) {
                        systematicProcessing.setStatus(Status.BLOCKED);
                        systematicProcessingDataService.save(systematicProcessing);
                        return;
                    }
                    submitJob(configTemplate.getOwner().getName(), configTemplate.getService().getName(), String.valueOf(systematicProcessing.getParentJob().getId()), inputs);
                    Map<String, Object> extraParams = (Map<String, Object>) feature.getProperties().get("extraParams");
                    systematicProcessing.setLastUpdated(ZonedDateTime.parse(extraParams.get("ftepUpdated").toString()).toLocalDateTime().plusSeconds(1));

					// Slow down a bit for safety
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
                }
                page++;
            } while (results.getLinks().containsKey("next"));
        } catch (IOException ioe) {
            LOG.error("Failure running search for systematic processing {}", systematicProcessing.getId());
        } catch (InterruptedException inte) {
            LOG.error("Failure submitting job for systematic processing {}", systematicProcessing.getId());
        } catch (JobSubmissionException jse) {
            LOG.error("Failure submitting job for systematic processing {} ", systematicProcessing.getId());
            systematicProcessing.setStatus(Status.BLOCKED);
        } finally {
            systematicProcessingDataService.save(systematicProcessing);
        }
    }

    private SearchResults getSearchResultsPage(HttpUrl requestUrl, int page, ListMultimap<String, String> queryParameters)
        throws IOException {
        SearchParameters sp = new SearchParameters();
        sp.setPage(page);
        sp.setResultsPerPage(20);
        sp.setRequestUrl(requestUrl);
        sp.setParameters(queryParameters);
        return searchFacade.search(sp);
    }

    private void submitJob(String userName, String serviceName, String parentId, Multimap<String, String> inputs) throws InterruptedException, JobSubmissionException {
        FtepServiceParams.Builder serviceParamsBuilder
            = FtepServiceParams.newBuilder().setJobId(UUID.randomUUID().toString()).setUserId(userName)
            .setJobParent(parentId)
            .setServiceId(serviceName).addAllInputs(GrpcUtil.mapToParams(inputs));

        final CountDownLatch latch = new CountDownLatch(1);
        JobLaunchObserver responseObserver = new JobLaunchObserver(latch);
        localServiceLauncher.asyncSubmitJob(serviceParamsBuilder.build(), responseObserver);
        // Block until the latch counts down (i.e. one message from the server)
        latch.await(1, TimeUnit.MINUTES);
        if (responseObserver.getError() != null) {
            throw new JobSubmissionException(responseObserver.getError());
        }
    }

    private static final class JobLaunchObserver implements StreamObserver<SubmitJobResponse> {
        private final CountDownLatch latch;
        @Getter
        private long intJobId;

        @Getter
        private Throwable error;

        public JobLaunchObserver(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onNext(SubmitJobResponse value) {
            this.intJobId = value.getJob().getIntJobId();
            LOG.info("Received job ID: {}", this.intJobId);
            latch.countDown();
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            latch.countDown(); // just to be safe
        }

        @Override
        public void onCompleted() {
            // No-op, the user has long stopped listening here
            latch.countDown(); // just to be safe
        }
    }

    public class JobSubmissionException extends Exception {
        public JobSubmissionException(Throwable t) {
            super(t);
        }
    }
}
