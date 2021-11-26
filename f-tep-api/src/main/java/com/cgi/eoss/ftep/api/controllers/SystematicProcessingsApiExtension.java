package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.model.Job;
import com.cgi.eoss.ftep.model.SystematicProcessing;
import com.cgi.eoss.ftep.rpc.LocalServiceLauncher;
import com.cgi.eoss.ftep.rpc.CancelJobParams;
import com.cgi.eoss.ftep.rpc.CancelJobResponse;
import com.cgi.eoss.ftep.rpc.GrpcUtil;
import com.cgi.eoss.ftep.persistence.service.SystematicProcessingDataService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RestController
@BasePathAwareController
@RequestMapping("/systematicProcessings")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class SystematicProcessingsApiExtension {

    private final SystematicProcessingDataService systematicProcessingDataService;
    private final LocalServiceLauncher localServiceLauncher;

    @PostMapping("/{systematicProcessingId}/terminate")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#systematicProcessing, 'write')")
    public ResponseEntity terminate(@ModelAttribute("systematicProcessingId") SystematicProcessing systematicProcessing) throws InterruptedException {
        LOG.debug("Terminating systematic processing: {}", systematicProcessing.getId());
        systematicProcessing.setStatus(SystematicProcessing.Status.COMPLETED);
        systematicProcessingDataService.save(systematicProcessing);

        Job parentJob = systematicProcessing.getParentJob();
        if (parentJob != null) {
            CancelJobParams cancelJobParams = CancelJobParams.newBuilder().setJob(GrpcUtil.toRpcJob(parentJob)).build();
            final CountDownLatch latch = new CountDownLatch(1);
            CancelJobObserver responseObserver = new CancelJobObserver(latch);
            localServiceLauncher.asyncCancelJob(cancelJobParams, responseObserver);
            latch.await(1, TimeUnit.MINUTES);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{systematicProcessingId}/parentJob")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#systematicProcessing, 'read')")
    public ResponseEntity<Resource<Job>> getParentJob(@ModelAttribute("systematicProcessingId") SystematicProcessing systematicProcessing) {
        return ResponseEntity.ok().body(new Resource<>(systematicProcessing.getParentJob()));
    }

    private static final class CancelJobObserver implements StreamObserver<CancelJobResponse> {
        private final CountDownLatch latch;

        CancelJobObserver(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onNext(CancelJobResponse value) {
            LOG.debug("Received CancelJobResponse for systematic processing job: {}", value);
            latch.countDown();
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("Failed to cancel systematic processing job via REST API", t);
        }

        @Override
        public void onCompleted() {
            // No-op, the user has long stopped listening here
        }
    }
}