package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.model.JobConfig;
import com.cgi.eoss.ftep.model.QJobConfig;
import com.cgi.eoss.ftep.model.QUser;
import com.cgi.eoss.ftep.persistence.dao.JobConfigDao;
import com.cgi.eoss.ftep.persistence.service.JobConfigDataService;
import com.cgi.eoss.ftep.security.FtepSecurityService;
import com.querydsl.core.types.dsl.NumberPath;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class JobConfigsApiCustomImpl extends BaseRepositoryApiImpl<JobConfig> implements JobConfigsApiCustom {

    private final FtepSecurityService securityService;
    private final JobConfigDao dao;
    private final JobConfigDataService dataService;

    public JobConfigsApiCustomImpl(FtepSecurityService securityService, JobConfigDao dao, JobConfigDataService dataService) {
        super(JobConfig.class);
        this.securityService = securityService;
        this.dao = dao;
        this.dataService = dataService;
    }

    @Override
    public <S extends JobConfig> S save(S entity) {
        if (entity.getOwner() == null) {
            getSecurityService().updateOwnerWithCurrentUser(entity);
        }
        return (S) dataService.save(entity);
    }

    @Override
    NumberPath<Long> getIdPath() {
        return QJobConfig.jobConfig.id;
    }

    @Override
    QUser getOwnerPath() {
        return QJobConfig.jobConfig.owner;
    }

    @Override
    Class<JobConfig> getEntityClass() {
        return JobConfig.class;
    }

}
