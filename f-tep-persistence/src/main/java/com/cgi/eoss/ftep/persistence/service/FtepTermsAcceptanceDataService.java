package com.cgi.eoss.ftep.persistence.service;

import com.cgi.eoss.ftep.model.FtepTermsAcceptance;
import com.cgi.eoss.ftep.model.User;

import java.util.List;

public interface FtepTermsAcceptanceDataService extends FtepEntityDataService<FtepTermsAcceptance> {
    List<FtepTermsAcceptance> findByOwner(User user);
}
