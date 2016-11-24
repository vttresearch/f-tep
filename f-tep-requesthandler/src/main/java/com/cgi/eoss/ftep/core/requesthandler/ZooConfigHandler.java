package com.cgi.eoss.ftep.core.requesthandler;

import lombok.extern.slf4j.Slf4j;
import org.zoo.project.ZooConstants;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

@Slf4j
public class ZooConfigHandler {

    private HashMap<String, String> mainZooConf;
    private HashMap<String, String> ftepZooConf;
    private HashMap<String, String> lEnvZooConf;
    private HashMap<String, String> sEnvZooConf;
    private HashMap<String, String> rEnvZooConf;

    private File workingDirParent;
    // TODO remove the workerVM property after the implementation of Resource
    // Provisioner
    private String workerVM;

    public ZooConfigHandler(HashMap<String, HashMap<String, String>> _zooConf) {
        mainZooConf = _zooConf.get(ZooConstants.ZOO_MAIN_CFG_MAP);
        ftepZooConf = _zooConf.get(ZooConstants.ZOO_FTEP_CFG_MAP);
        lEnvZooConf = _zooConf.get(ZooConstants.ZOO_LENV_CFG_MAP);
        sEnvZooConf = _zooConf.get(ZooConstants.ZOO_SENV_CFG_MAP);
        rEnvZooConf = _zooConf.get(ZooConstants.ZOO_RENV_CFG_MAP);
    }

    public File getDataDownloadDir() {
        String workingDirLocation = ftepZooConf.get(ZooConstants.ZOO_FTEP_DATA_DOWNLOAD_DIR_PARAM);
        if (null != workingDirLocation) {
            workingDirParent = new File(workingDirLocation);
        } else {
            LOG.error("Missing property " + ZooConstants.ZOO_FTEP_DATA_DOWNLOAD_DIR_PARAM
                    + " in the F-TEP configuration");
        }
        return workingDirParent;
    }

    public File getLog4jPropFile() {
        File log4jPropFile = null;
        String log4jPropertyFile = ftepZooConf.get(ZooConstants.ZOO_FTEP_LOG4J_FILENAME_PARAM);
        if (null != log4jPropertyFile) {
            log4jPropFile = new File(log4jPropertyFile);
        } else {
            LOG.error("Missing property " + ZooConstants.ZOO_FTEP_LOG4J_FILENAME_PARAM
                    + " in the F-TEP configuration");
        }
        return log4jPropFile;
    }

    public String getDownloadToolPath() {
        String downloadToolPath = ftepZooConf.get(ZooConstants.ZOO_FTEP_DOWNLOAD_TOOL_PATH_PARAM);
        if (null == downloadToolPath) {
            LOG.error("Missing/Invalid property " + ZooConstants.ZOO_FTEP_DOWNLOAD_TOOL_PATH_PARAM
                    + " in the F-TEP configuration");
        }
        return downloadToolPath;
    }

    public String getWorkerVM() {
        String workerVmIpAddr = ftepZooConf.get(ZooConstants.ZOO_FTEP_WORKER_VM_IP_ADDR_PARAM);
        if (null != workerVmIpAddr) {
            return workerVmIpAddr;
        } else {
            LOG.error("Missing property " + ZooConstants.ZOO_FTEP_WORKER_VM_IP_ADDR_PARAM
                    + " in the F-TEP configuration");
        }
        return "localhost:2376";
    }

    public String getJobID() {
        String zooJobID = lEnvZooConf.get(ZooConstants.ZOO_LENV_USID_PARAM);
        if (null != zooJobID) {
            return zooJobID;
        } else {
            LOG.error("Cannot find WPS server JobID. Creating a new one.");
        }
        return generateUniqueIdentifier();
    }

    public String getServiceName() {
        String serviceName = lEnvZooConf.get(ZooConstants.ZOO_LENV_IDENTIFIER_PARAM);
        if (null != serviceName) {
            return serviceName;
        } else {
            LOG.error("Cannot find WPS service name.");
        }
        return "";
    }

    public String getUserID() {
        String ssoUserID = rEnvZooConf.get(ZooConstants.ZOO_RENV_SSO_USERID_PARAM);
        if (null != ssoUserID) {
            return ssoUserID;
        } else {
            LOG.error("Cannot find EO SSO User ID");
        }
        return "";
    }

    public void setMessage(String message) {

        String messageVal = lEnvZooConf.get(ZooConstants.ZOO_LENV_MESSAGE_PARAM);
        if (null != messageVal) {
            LOG.info("setting message in lenv map to :" + messageVal);
            lEnvZooConf.put(ZooConstants.ZOO_LENV_MESSAGE_PARAM, message);
        } else
            LOG.error("Lenv messageKey is NULL");
    }

    public File getCacheDir() {
        File cacheDir = null;
        String cacheDirPath = mainZooConf.get(ZooConstants.ZOO_MAIN_CACHE_DIR_PARAM);
        if (null != cacheDirPath) {
            cacheDir = new File(cacheDirPath);
        } else {
            LOG.error("Missing property " + ZooConstants.ZOO_MAIN_CACHE_DIR_PARAM
                    + " in the F-TEP configuration");
        }
        return cacheDir;
    }

    private String generateUniqueIdentifier() {
        String[] ids = UUID.randomUUID().toString().split("-");
        return ids[0];
    }


}