package com.cgi.eoss.ftep.orchestrator;

import com.cgi.eoss.ftep.model.JobStatus;
import com.cgi.eoss.ftep.model.JobStep;
import com.cgi.eoss.ftep.model.internal.FtepJob;
import com.cgi.eoss.ftep.model.rest.ApiEntity;
import com.cgi.eoss.ftep.model.rest.ResourceJob;
import com.cgi.eoss.ftep.rpc.GrpcUtil;
import com.cgi.eoss.ftep.rpc.ProcessorLauncherGrpc;
import com.cgi.eoss.ftep.rpc.ProcessorParams;
import com.cgi.eoss.ftep.rpc.ProcessorResponse;
import com.google.common.collect.Multimap;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * <p>Server endpoint for the ProcessorLauncher RPC service.</p>
 */
@Slf4j
public class ProcessorLauncher extends ProcessorLauncherGrpc.ProcessorLauncherImplBase {

    private final WorkerService workerService;
    private final JobStatusService jobStatusService;
    private final JobEnvironmentService jobEnvironmentService;
    private final ServiceDataService serviceDataService;

    public ProcessorLauncher(WorkerService workerService,
                             JobStatusService jobStatusService,
                             ServiceDataService serviceDataService,
                             JobEnvironmentService jobEnvironmentService) {
        // TODO Distribute by configuring and connecting to these services via RPC
        this.workerService = workerService;
        this.jobStatusService = jobStatusService;
        this.serviceDataService = serviceDataService;
        this.jobEnvironmentService = jobEnvironmentService;
    }

    @Override
    public void launchProcessor(ProcessorParams request, StreamObserver<ProcessorResponse> responseObserver) {
        Multimap<String, String> inputs = GrpcUtil.paramsListToMap(request.getInputsList());

        // TODO Check coins and estimated cost

        JobEnvironment jobEnvironment;
        try {
            // Create the workspace, including the job config file
            jobEnvironment = jobEnvironmentService.createEnvironment(request.getJobId(), inputs);
        } catch (IOException e) {
            LOG.error("Failed to create working environment for job {}", request.getJobId());
            throw new ServiceExecutionException(e);
        }

        FtepJob job = FtepJob.builder()
                .jobId(request.getJobId())
                .userId(request.getUserId())
                .serviceId(request.getServiceId())
                .status(JobStatus.CREATED)
                .workingDir(jobEnvironment.getWorkingDir())
                .inputDir(jobEnvironment.getInputDir())
                .outputDir(jobEnvironment.getOutputDir())
                .build();

        ApiEntity<ResourceJob> apiJob = null;

        try {
            apiJob = jobStatusService.create(job.getJobId(), job.getUserId(), job.getServiceId());

            // Get a worker
            Worker worker = workerService.getWorker();

            // Set up input data
            LOG.info("Downloading input data for {}", job.getJobId());
            apiJob.getResource().setStep(JobStep.DATA_FETCH.getText());
            apiJob.getResource().setStatus(JobStatus.RUNNING.name());
            jobStatusService.update(apiJob);

            worker.prepareInputs(inputs, job.getInputDir());

            // Launch the processor
            apiJob.getResource().setStep(JobStep.PROCESSING.getText());
            jobStatusService.update(apiJob);
            String dockerImageTag = serviceDataService.getImageFor(job.getServiceId());
            LOG.info("Launching docker image {} for {}", dockerImageTag, job.getJobId());

            String containerId = worker.launchDockerContainer(DockerLaunchConfig.builder()
                    .image(dockerImageTag)
                    .volume("/nobody/workDir")
                    .volume(job.getWorkingDir().getParent().toString())
                    .bind(job.getWorkingDir().toAbsolutePath().toString() + ":" + "/nobody/workDir")
                    .bind(job.getWorkingDir().getParent().toString() + ":" + job.getWorkingDir().getParent().toString())
                    .defaultLogging(true)
                    .build());

            // TODO Stream logs from docker container to WPS (or direct to web client)

            LOG.info("Processor launched: (job {}) {}", job.getJobId(), job.getServiceId());

            // Register timeout callback and wait for exit
            int exitCode = worker.waitForContainerExit(containerId);

            if (exitCode != 0) {
                throw new ServiceExecutionException("Docker container returned with exit code " + exitCode);
            }

            apiJob.getResource().setStep(JobStep.OUTPUT_LIST.getText());
            jobStatusService.update(apiJob);
            LOG.info("Application terminated, collecting outputs from {}", job.getOutputDir());
            // TODO Collect outputs and build into response

            apiJob.getResource().setStatus(JobStatus.COMPLETED.name());
            jobStatusService.update(apiJob);

            responseObserver.onNext(ProcessorResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            if (apiJob != null) {
                setJobInError(apiJob);
            }

            LOG.error("Failed to run processor; notifying gRPC client", e);
            responseObserver.onError(new StatusRuntimeException(Status.fromCode(Status.Code.ABORTED).withCause(e)));
        }
    }

    private void setJobInError(ApiEntity<ResourceJob> apiJob) {
        try {
            jobStatusService.update(apiJob);
            apiJob.getResource().setStatus(JobStatus.ERROR.name());
        } catch (Exception e) {
            LOG.error("Unable to set job to ERROR state (swallowing exception)", e);
        }
    }

}
