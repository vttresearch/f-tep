package com.cgi.eoss.ftep.orchestrator.service;

import com.cgi.eoss.ftep.catalogue.CatalogueService;
import com.cgi.eoss.ftep.logging.Logging;
import com.cgi.eoss.ftep.model.FtepFile;
import com.cgi.eoss.ftep.model.Job;
import com.cgi.eoss.ftep.model.internal.OutputFileMetadata;
import com.cgi.eoss.ftep.model.internal.OutputProductMetadata;
import com.cgi.eoss.ftep.persistence.service.JobDataService;
import com.cgi.eoss.ftep.search.api.SearchFacade;
import com.google.common.collect.Sets;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.CloseableThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;

@Service
@Log4j2
public class FtepFileRegistrar {

    private final JobDataService jobDataService;
    private final SearchFacade searchFacade;
    private final CatalogueService catalogueService;

    @Autowired
    public FtepFileRegistrar(JobDataService jobDataService, SearchFacade searchFacade, CatalogueService catalogueService) {
        this.jobDataService = jobDataService;
        this.searchFacade = searchFacade;
        this.catalogueService = catalogueService;
    }

    public void registerAndCheckInputs(Job job) {
        Set<FtepFile> inputFiles = registerInputFiles(job);

        Optional<URI> anyUnreadableUri = inputFiles.stream()
                .map(FtepFile::getUri)
                .filter(uri -> !catalogueService.canUserRead(job.getOwner(), uri))
                .peek(uri -> {
                    try (CloseableThreadContext.Instance userCtc = Logging.userLoggingContext()) {
                        LOG.error("User does not have read access to input: {}", uri);
                    }
                })
                .findAny();
        if (anyUnreadableUri.isPresent()) {
            throw new ServiceExecutionException("User does not have read access to all requested inputs");
        }
    }

    public Set<FtepFile> registerInputFiles(Job job) {
        try {
            List<FtepFile> inputFiles = job.getConfig().getInputs().entries().stream()
                    .map(Map.Entry::getValue)
                    .filter(FtepFileRegistrar::isValidUri) // Find inputs which look like URIs
                    .flatMap(e -> Arrays.stream(StringUtils.split(e, ',')).map(URI::create)) // Split multi-valued URI inputs
                    .flatMap(searchFacade::findProducts) // Register each URI to an FtepFile (or an empty stream if it couldn't be done)
                    .distinct()
                    .collect(toList());

            // If the FtepFile has no size, we should force a reload to see if we can get it
            inputFiles.replaceAll(f -> {
                if (f.getFilesize() == null || f.getFilesize().equals(0L)) {
                    return searchFacade.searchForAndCreateSatelliteProductReference(f.getUri())
                            .orElse(f);
                } else {
                    return f;
                }
            });

            Set<FtepFile> ftepFiles = Sets.newHashSet(inputFiles);
            job.getConfig().setInputFiles(ftepFiles);
            jobDataService.updateJobConfig(job);
            return ftepFiles;
        } catch (Exception e) {
            LOG.warn("Failed to update job {} configuration files", job.getId(), e);
            return Collections.emptySet();
        }
    }

    public Path prepareNewOutputProduct(OutputProductMetadata outputProduct, String filename) throws IOException {
        return catalogueService.provisionNewOutputProduct(outputProduct, filename);
    }

    private static boolean isValidUri(String test) {
        try {
            return URI.create(test).getScheme() != null;
        } catch (Exception unused) {
            return false;
        }
    }

    public FtepFile registerOutput(OutputFileMetadata outputFileMetadata, Path outputPath) {
        try {
            try (CloseableThreadContext.Instance ctc = Logging.userLoggingContext()) {
                LOG.info("Ingesting output file to F-TEP catalogue: {}", outputFileMetadata.getOutputProductMetadata().getOutputId());
            }
            return catalogueService.ingestOutputProduct(outputFileMetadata, outputPath);
        } catch (IOException e) {
            throw new ServiceExecutionException("Failed to ingest Output Product", e);
        }
    }
}