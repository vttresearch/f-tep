package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.catalogue.CatalogueService;
import com.cgi.eoss.ftep.model.FtepFile;
import com.cgi.eoss.ftep.model.QFtepFile;
import com.cgi.eoss.ftep.model.QUser;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.persistence.dao.FtepFileDao;
import com.cgi.eoss.ftep.security.FtepSecurityService;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.JPQLQuery;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
@Getter
@Component
public class FtepFilesApiCustomImpl extends BaseRepositoryApiImpl<FtepFile> implements FtepFilesApiCustom {

    private final FtepSecurityService securityService;
    private final FtepFileDao dao;
    private final CatalogueService catalogueService;

    public FtepFilesApiCustomImpl(FtepSecurityService securityService, FtepFileDao dao, CatalogueService catalogueService) {
        super(FtepFile.class);
        this.securityService = securityService;
        this.dao = dao;
        this.catalogueService = catalogueService;
    }

    @Override
    NumberPath<Long> getIdPath() {
        return QFtepFile.ftepFile.id;
    }

    @Override
    QUser getOwnerPath() {
        return QFtepFile.ftepFile.owner;
    }

    @Override
    Class<FtepFile> getEntityClass() {
        return FtepFile.class;
    }

    @Override
    public void delete(FtepFile ftepFile) {
        try {
            catalogueService.delete(ftepFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<FtepFile> searchByType(FtepFile.Type type, Pageable pageable) {
        return getFilteredResults(getFilterPredicate(null, type, null, null, null, null, null), pageable);
    }

    @Override
    public Page<FtepFile> searchByFilterOnly(String filter, FtepFile.Type type, Pageable pageable) {
        return getFilteredResults(getFilterPredicate(filter, type, null, null, null, null, null), pageable);
    }

    @Override
    public Page<FtepFile> searchByFilterAndOwner(String filter, FtepFile.Type type, User user, Pageable pageable) {
        return getFilteredResults(getFilterPredicate(filter, type, null, user, null, null, null), pageable);
    }

    @Override
    public Page<FtepFile> searchByFilterAndNotOwner(String filter, FtepFile.Type type, User user, Pageable pageable) {
        return getFilteredResults(getFilterPredicate(filter, type, null, null, user, null, null), pageable);
    }

    @Override
    public Page<FtepFile> searchAll(String keyword, FtepFile.Type type, FtepFile.Type notType, User owner, User notOwner, Long minFilesize, Long maxFilesize, Pageable pageable) {
        Predicate predicate = getFilterPredicate(keyword, type, notType, owner, notOwner, minFilesize, maxFilesize);

        JPQLQuery<FtepFile> query = from(QFtepFile.ftepFile).where(predicate);

        query = getQuerydsl().applyPagination(pageable, query);

        if (getSecurityService().isSuperUser()) {
            return PageableExecutionUtils.getPage(query.fetch(), pageable, query::fetchCount);
        } else {
            Set<Long> visibleIds = getSecurityService().getVisibleObjectIds(getEntityClass(), getDao().findAllIds());
            // Partition the visible ids to subsets < 32767 elements as the DB 
            // interface breaks if there are over 32767 bind variables in a query
            List<Long> visibleIdsList = Lists.newArrayList(visibleIds);
            List<List<Long>> partitionedIds = Lists.partition(visibleIdsList, 30000);

            long offset = 0;
            int filesRequested = 20;
            if (pageable != null) {
                offset = pageable.getOffset();
                filesRequested = pageable.getPageSize();
            }

            // The collected result files, at most filesRequested files
            List<FtepFile> files = new ArrayList<>();
            // The total number of available files
            long totalCount = 0;

            // Run the query for all partitions
            // Sum the number of items in each partition to get the total item count
            //  and collect the requested number of files from the requested offset onwards
            for (List<Long> ids : partitionedIds) {
                JPQLQuery<FtepFile> q = from(QFtepFile.ftepFile).where(predicate);
                q.where(getIdPath().in(ids));
                
                long fetchCount = q.fetchCount();

                if (totalCount + fetchCount > offset && files.size() < filesRequested) {
                    // Add files from the correct offset (i.e. skip the first offset files
                    long start = offset - totalCount;
                    if (start < 0) {
                        start = 0;
                    }
                    q = q.offset(start).limit(filesRequested - files.size());
                    files.addAll(q.fetch());
                }
                totalCount += fetchCount;
            }
            // Total result item count as a final long so that conversion to LongSupplier works
            final long total = totalCount;
            return PageableExecutionUtils.getPage(files, pageable, () -> new Long(total));
        }
    }

    private Predicate getFilterPredicate(String filter, FtepFile.Type type, FtepFile.Type notType, User owner, User notOwner, Long minFilesize, Long maxFilesize) {
        BooleanBuilder builder = new BooleanBuilder();

        if (!Strings.isNullOrEmpty(filter)) {
            builder.and(QFtepFile.ftepFile.filename.containsIgnoreCase(filter)
                    .or(QFtepFile.ftepFile.job.any().config.label.containsIgnoreCase(filter))
            );
        }

        if (type != null) {
            builder.and(QFtepFile.ftepFile.type.eq(type));
        }

        if (notType != null) {
            builder.and(QFtepFile.ftepFile.type.ne(notType));
        }

        if (owner != null) {
            builder.and(QFtepFile.ftepFile.owner.eq(owner));
        }

        if (notOwner != null) {
            builder.and(QFtepFile.ftepFile.owner.ne(notOwner));
        }

        if (minFilesize != null) {
            builder.and(QFtepFile.ftepFile.filesize.goe(minFilesize));
        }

        if (maxFilesize != null) {
            builder.and(QFtepFile.ftepFile.filesize.loe(maxFilesize));
        }

        return builder.getValue();
    }

    @Override
    protected Page<FtepFile> getFilteredResults(Predicate predicate, Pageable pageable) {
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
            List<FtepFile> jobs = new ArrayList<>();
            // The total number of available files
            long totalCount = 0;

            // Run the query for all partitions
            // Sum the number of items in each partition to get the total item count
            //  and collect the requested number of files from the requested offset onwards
            for (List<Long> ids : partitionedIds) {
                JPQLQuery<FtepFile> q = from(QFtepFile.ftepFile).where(predicate);
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
            return PageableExecutionUtils.getPage(jobs, pageable, () -> total);
        }
    }
}
