package com.cgi.eoss.ftep.persistence.service;

import com.cgi.eoss.ftep.model.FtepTermsAcceptance;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.persistence.dao.FtepEntityDao;
import com.cgi.eoss.ftep.persistence.dao.FtepTermsAcceptanceDao;
import com.querydsl.core.types.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.cgi.eoss.ftep.model.QFtepTermsAcceptance.ftepTermsAcceptance;

@Service
@Transactional(readOnly = true)
public class JpaFtepTermsAcceptanceDataService extends AbstractJpaDataService<FtepTermsAcceptance> implements FtepTermsAcceptanceDataService {

    private final FtepTermsAcceptanceDao ftepTermsAcceptanceDao;

    @Autowired
    public JpaFtepTermsAcceptanceDataService(FtepTermsAcceptanceDao ftepTermsAcceptanceDao) {
        this.ftepTermsAcceptanceDao = ftepTermsAcceptanceDao;
    }

    @Override
    FtepEntityDao<FtepTermsAcceptance> getDao() {
        return ftepTermsAcceptanceDao;
    }

    @Override
    Predicate getUniquePredicate(FtepTermsAcceptance entity) {
        return ftepTermsAcceptance.owner.eq(entity.getOwner()).and(ftepTermsAcceptance.acceptanceTime.eq(entity.getAcceptanceTime()));
    }

    @Override
    public List<FtepTermsAcceptance> findByOwner(User user) {
        return ftepTermsAcceptanceDao.findByOwner(user);
    }
}
