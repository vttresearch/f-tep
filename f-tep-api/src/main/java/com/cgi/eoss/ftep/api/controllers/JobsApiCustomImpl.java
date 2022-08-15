package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.model.Job;
import com.cgi.eoss.ftep.model.Job.Status;
import com.cgi.eoss.ftep.model.QJob;
import com.cgi.eoss.ftep.model.QUser;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.persistence.dao.JobDao;
import com.cgi.eoss.ftep.security.FtepSecurityService;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.JPQLQuery;
import java.util.ArrayList;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.data.repository.support.PageableExecutionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Getter
@Component
public class JobsApiCustomImpl extends BaseRepositoryApiImpl<Job> implements JobsApiCustom {

    private final FtepSecurityService securityService;
    private final JobDao dao;

    public JobsApiCustomImpl(FtepSecurityService securityService, JobDao dao) {
        super(Job.class);
        this.securityService = securityService;
        this.dao = dao;
    }

    @Override
    public NumberPath<Long> getIdPath() {
        return QJob.job.id;
    }

    @Override
    public QUser getOwnerPath() {
        return QJob.job.owner;
    }

    @Override
    public Class<Job> getEntityClass() {
        return Job.class;
    }

    public BooleanExpression isNotSubjob() {
        return QJob.job.parentJob.isNull();
    }

    public BooleanExpression isChildOf(Long parentId) {
        return QJob.job.parentJob.id.eq(parentId);
    }

    // --- Filter

    @Override
    public Page<Job> searchByFilterOnly(String filter, Collection<Status> statuses, Pageable pageable) {
        return getFilteredResults(getFilterPredicate(filter, statuses), pageable);
    }

    @Override
    public Page<Job> searchByFilterAndOwner(String filter, Collection<Status> statuses, User user, Pageable pageable) {
        return getFilteredResults(getOwnerPath().eq(user).and(getFilterPredicate(filter, statuses)), pageable);
    }

    @Override
    public Page<Job> searchByFilterAndNotOwner(String filter, Collection<Status> statuses, User user, Pageable pageable) {
        return getFilteredResults(getOwnerPath().ne(user).and(getFilterPredicate(filter, statuses)), pageable);
    }

    // --- Filter / Not Subjob

    @Override
    public Page<Job> searchByFilterAndIsNotSubjob(String filter, Collection<Status> statuses, Pageable pageable) {
        return getFilteredResults(isNotSubjob().and(getFilterPredicate(filter, statuses)), pageable);
    }

    @Override
    public Page<Job> searchByFilterAndIsNotSubjobAndOwner(String filter, Collection<Status> statuses, User user, Pageable pageable) {
        return getFilteredResults(isNotSubjob().and(getOwnerPath().eq(user)).and(getFilterPredicate(filter, statuses)), pageable);
    }

    @Override
    public Page<Job> searchByFilterAndIsNotSubjobAndNotOwner(String filter, Collection<Status> statuses, User user, Pageable pageable) {
        return getFilteredResults(isNotSubjob().and(getOwnerPath().ne(user)).and(getFilterPredicate(filter, statuses)), pageable);
    }

    // --- Filter / Parent

    @Override
    public Page<Job> searchByFilterAndParent(String filter, Collection<Status> statuses, Long parentId, Pageable pageable) {
        return getFilteredResults(getFilterPredicate(filter, statuses, parentId), pageable);
    }

    @Override
    public Page<Job> searchByFilterAndParentAndOwner(String filter, Collection<Status> statuses, Long parentId, User user, Pageable pageable) {
        return getFilteredResults((getOwnerPath().eq(user)).and(getFilterPredicate(filter, statuses, parentId)), pageable);
    }

    @Override
    public Page<Job> searchByFilterAndParentAndNotOwner(String filter, Collection<Status> statuses, Long parentId, User user, Pageable pageable) {
        return getFilteredResults((getOwnerPath().ne(user)).and(getFilterPredicate(filter, statuses, parentId)), pageable);
    }

    // --- Filter Predicate

    public Predicate getFilterPredicate(String filter, Collection<Status> statuses) {
        return getFilterPredicate(filter, statuses, null);
    }

    public Predicate getFilterPredicate(String filter, Collection<Status> statuses, Long parentId) {
        BooleanBuilder builder = new BooleanBuilder(Expressions.asBoolean(true).isTrue());

        if (!Strings.isNullOrEmpty(filter)) {
            builder.and(QJob.job.id.stringValue().contains(filter)
                    .or(QJob.job.config.label.containsIgnoreCase(filter))
                    .or(QJob.job.config.service.name.containsIgnoreCase(filter)));
        }
        if (statuses != null && !statuses.isEmpty()) {
            builder.and(QJob.job.status.in(statuses));
        }
        if (parentId != null) {
            builder.and(isChildOf(parentId));
        }

        return builder.getValue();
    }

    protected Page<Job> getFilteredResults(Predicate predicate, Pageable pageable) {
        if (getSecurityService().isSuperUser()) {
            return getDao().findAll(predicate, pageable);
        } else {
            Set<Long> visibleIds = getSecurityService().getVisibleObjectIds(getEntityClass(), getDao().findAllIds());
            // Partition the visible ids to subsets < 32767 elements as the DB 
            // interface breaks if there are over 32767 bind variables in a query
            List<Long> visibleIdsList = Lists.newArrayList(visibleIds);
            Collections.sort(visibleIdsList, Collections.reverseOrder());
            List<List<Long>> partitionedIds = Lists.partition(visibleIdsList, 30000);

            long offset = 0;
            int filesRequested = 20;
            if (pageable != null) {
                offset = pageable.getOffset();
                filesRequested = pageable.getPageSize();
            }

            // The collected result files, at most filesRequested files
            List<Job> jobs = new ArrayList<>();
            // The total number of available files
            long totalCount = 0;

            // Run the query for all partitions
            // Sum the number of items in each partition to get the total item count
            //  and collect the requested number of files from the requested offset onwards
            for (List<Long> ids : partitionedIds) {
                JPQLQuery<Job> q = from(QJob.job).where(predicate);
                q.where(getIdPath().in(ids));
                q.orderBy(getIdPath().desc());
                
                long fetchCount = q.fetchCount();

                if (totalCount + fetchCount > offset && jobs.size() < filesRequested) {
                    // Add files from the correct offset (i.e. skip the first offset files
                    long start = offset - totalCount;
                    if (start < 0) {
                        start = 0;
                    }
                    q = q.offset(start).limit(filesRequested - jobs.size());
                    jobs.addAll(q.fetch());
                }
                totalCount += fetchCount;
            }
            // Total result item count as a final long so that conversion to LongSupplier works
            final long total = totalCount;
            return PageableExecutionUtils.getPage(jobs, pageable, () -> new Long(total));




//            BooleanExpression isVisible = getIdPath().in(visibleIds);
//            return getDao().findAll(isVisible.and(predicate), pageable);
        }
    }
}
