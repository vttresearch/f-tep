package com.cgi.eoss.ftep.api.controllers;

import com.cgi.eoss.ftep.model.FtepFile;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.model.projections.ShortFtepFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.URI;

@RepositoryRestResource(path = "ftepFiles", itemResourceRel = "ftepFile", collectionResourceRel = "ftepFiles", excerptProjection = ShortFtepFile.class)
public interface FtepFilesApi extends BaseRepositoryApi<FtepFile>, FtepFilesApiCustom, PagingAndSortingRepository<FtepFile, Long> {

    @Override
    @PostAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(returnObject, 'read')")
    FtepFile findOne(Long id);

    @Override
    @RestResource(exported = false)
    <S extends FtepFile> Iterable<S> save(Iterable<S> ftepFiles);

    @Override
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or (#ftepFile.id != null and hasPermission(#ftepFile, 'write'))")
    <S extends FtepFile> S save(@P("ftepFile") S ftepFile);

    @Override
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN')")
    void delete(Iterable<? extends FtepFile> ftepFiles);

    @Override
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#ftepFile, 'administration')")
    void delete(@P("ftepFile") FtepFile ftepFile);

    @Override
    @Query("select f from FtepFile f where f.type=type")
    Page<FtepFile> findByType(@Param("type") FtepFile.Type type, Pageable pageable);

    @PostAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(returnObject, 'read')")
    FtepFile findOneByUri(@Param("uri") URI uri);

    @Override
    @Query("select t from FtepFile t where t.owner=user")
    Page<FtepFile> findByOwner(@Param("owner") User user, Pageable pageable);
}
