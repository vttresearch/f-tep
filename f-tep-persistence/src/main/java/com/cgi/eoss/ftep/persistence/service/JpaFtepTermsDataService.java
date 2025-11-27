package com.cgi.eoss.ftep.persistence.service;

import com.cgi.eoss.ftep.model.FtepTerms;
import com.cgi.eoss.ftep.persistence.dao.FtepEntityDao;
import com.cgi.eoss.ftep.persistence.dao.FtepTermsDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.cgi.eoss.ftep.model.QFtepTerms.ftepterms;

@Service
@Transactional(readOnly = true)
public class JpaFtepTermsDataService extends AbstractJpaDataService<FtepTerms> implements FtepTermsDataService {

    private final FtepTermsDao ftepTermsDao;

    @Autowired
    public JpaFtepTermsDataService(FtepTermsDao ftepTermsDao) {
        this.ftepTermsDao = ftepTermsDao;
    }

    @Override
    FtepEntityDao<FtepTerms> getDao() {
        return ftepTermsDao;
    }

    @Override
    Predicate getUniquePredicate(FtepTerms entity) {
        return ftepTerms.url.eq(entity.getUrl());
    }
}
