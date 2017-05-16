package com.cgi.eoss.ftep.model.projections;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.config.Projection;

import com.cgi.eoss.ftep.api.security.FtepPermission;
import com.cgi.eoss.ftep.model.Job;
import com.cgi.eoss.ftep.model.JobConfig;
import com.google.common.collect.Multimap;

/**
 * <p>Comprehensive representation of a Job entity, including outputs and jobConfig, for embedding in
 * REST responses.</p>
 */
@Projection(name = "detailedJob", types = Job.class)
public interface DetailedJob extends EmbeddedId {
	String getExtId();
    ShortUser getOwner();
    Job.Status getStatus();
    String getGuiUrl();
    String getStage();
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
    @Value("#{target.config.service.name}")
    String getServiceName();
    @Value("#{@ftepSecurityService.getCurrentPermission(target.class, target.id)}")
    FtepPermission getAccessLevel();
    Multimap<String, String> getOutputs();
    JobConfig getConfig();
}