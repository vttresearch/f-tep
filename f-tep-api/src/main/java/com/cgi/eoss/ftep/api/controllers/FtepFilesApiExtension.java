package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.catalogue.CatalogueService;
import com.cgi.eoss.ftep.catalogue.CatalogueUri;
import com.cgi.eoss.ftep.costing.CostingService;
import com.cgi.eoss.ftep.model.FtepFile;
import com.cgi.eoss.ftep.model.FtepFileExternalReferences;
import com.cgi.eoss.ftep.model.Subscription;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.model.internal.ReferenceDataMetadata;
import com.cgi.eoss.ftep.model.internal.UploadableFileType;
import com.cgi.eoss.ftep.persistence.service.FtepFileDataService;
import com.cgi.eoss.ftep.persistence.service.SubscriptionDataService;
import com.cgi.eoss.ftep.security.FtepSecurityService;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>A {@link RepositoryRestController} for interacting with {@link com.cgi.eoss.ftep.model.FtepFile}s. Extends the
 * simple repository-type {@link FtepFilesApi}.</p>
 */
@RestController
@BasePathAwareController
@RequestMapping("/ftepFiles")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class FtepFilesApiExtension {

    private final FtepSecurityService ftepSecurityService;
    private final FtepFileDataService ftepFileDataService;
    private final CatalogueService catalogueService;
    private final CostingService costingService;
    private final SubscriptionDataService subscriptionDataService;

    @PostMapping("/externalProduct")
    @ResponseBody
    public ResponseEntity saveExternalProductMetadata(@RequestBody Feature geoJsonFeature) throws Exception {
        FtepFile ftepFile = Optional
                .ofNullable(ftepFileDataService.getByUri((String) geoJsonFeature.getProperties().get("ftepUrl")))
                .orElseGet(() -> catalogueService.indexExternalProduct(geoJsonFeature));
        return ResponseEntity.created(ftepFile.getUri()).body(new Resource<>(ftepFile));
    }

    @PostMapping("/refData")
    @ResponseBody
    public ResponseEntity saveRefData(@RequestParam("geometry") String geometry, @RequestParam("file") MultipartFile file,
                                      @RequestParam("autoDetectGeometry") boolean autoDetectGeometry, @RequestParam("filetype") UploadableFileType fileType) throws Exception {
        User owner = ftepSecurityService.getCurrentUser();
        if (exceedsStorageQuota(owner)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Storage quota exceeded.");
        }

        String filename = file.getOriginalFilename();

        if (Strings.isNullOrEmpty(filename)) {
            return ResponseEntity.badRequest().body(String.format("Uploaded filename may not be null %s", file));
        }

        URI uri = CatalogueUri.REFERENCE_DATA.build(
                ImmutableMap.of(
                        "ownerId", owner.getId().toString(),
                        "filename", filename));

        if (!Objects.isNull(ftepFileDataService.getByUri(uri))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(String.format("Reference data filename '%s' already exists for user %s", filename, owner.getName()));
        }

        try {
            ReferenceDataMetadata metadata = ReferenceDataMetadata.builder()
                    .owner(owner)
                    .filename(filename)
                    .geometry(geometry)
                    .autoDetectGeometry(autoDetectGeometry)
                    .fileType(fileType)
                    .properties(ImmutableMap.of()) // TODO Collect user-driven metadata properties
                    .build();

            FtepFile ftepFile = catalogueService.ingestReferenceData(metadata, file);
            return ResponseEntity.created(ftepFile.getUri()).body(new Resource<>(ftepFile));
        } catch (Exception e) {
            LOG.error("Could not ingest reference data file {}", filename, e);
            throw e;
        }
    }

    @GetMapping(value = "/{fileId}/dl")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#file, 'read')")
    public void downloadFile(@ModelAttribute("fileId") FtepFile file, HttpServletResponse response) throws IOException {
        User user = ftepSecurityService.getCurrentUser();

        int estimatedCost = costingService.estimateDownloadCost(file);
        if (estimatedCost > user.getWallet().getBalance()) {
            response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
            String message = "Estimated download cost (" + estimatedCost + " coins) exceeds current wallet balance";
            response.getOutputStream().write(message.getBytes());
            response.flushBuffer();
            return;
        }
        // TODO Should estimated cost be "locked" in the wallet?

        Util.serveFileDownload(response, catalogueService.getAsResource(file));

        costingService.chargeForDownload(user.getWallet(), file);
    }

    @GetMapping(value="/{fileId}/checkDelete")
    public FtepFileExternalReferences checkFileExternalReferences(@ModelAttribute("fileId") FtepFile file){
        return catalogueService.getFtepFileReferencesWithType(file);
    }

    private boolean exceedsStorageQuota(User user) {
        LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);
        Optional<Subscription> activeSubscription = subscriptionDataService.findByOwner(user).stream()
                .filter(subscription -> subscription.isActive(currentTime)).findFirst();
        if (activeSubscription.isPresent()) {
            return activeSubscription.get().exceedsStorageQuota();
        }
        return false;
    }

}
