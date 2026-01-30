package com.cgi.eoss.ftep.persistence.dao;

import com.cgi.eoss.ftep.model.FtepTermsAcceptance;
import com.cgi.eoss.ftep.model.User;

import java.util.List;

public interface FtepTermsAcceptanceDao extends FtepEntityDao<FtepTermsAcceptance> {
    List<FtepTermsAcceptance> findByOwner(User user);
}
