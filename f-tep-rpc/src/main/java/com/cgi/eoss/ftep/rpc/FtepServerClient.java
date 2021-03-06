package com.cgi.eoss.ftep.rpc;

import com.cgi.eoss.ftep.rpc.catalogue.CatalogueServiceGrpc;

public class FtepServerClient extends GrpcClient {

    public FtepServerClient(DiscoveryClientResolverFactory discoveryClientResolverFactory, String ftepServerServiceId) {
        super(discoveryClientResolverFactory, ftepServerServiceId);
    }

    public ServiceContextFilesServiceGrpc.ServiceContextFilesServiceBlockingStub serviceContextFilesServiceBlockingStub() {
        return ServiceContextFilesServiceGrpc.newBlockingStub(getChannel());
    }

    public CredentialsServiceGrpc.CredentialsServiceBlockingStub credentialsServiceBlockingStub() {
        return CredentialsServiceGrpc.newBlockingStub(getChannel());
    }

    public CatalogueServiceGrpc.CatalogueServiceBlockingStub catalogueServiceBlockingStub() {
        return CatalogueServiceGrpc.newBlockingStub(getChannel());
    }

    public CatalogueServiceGrpc.CatalogueServiceStub catalogueServiceStub() {
        return CatalogueServiceGrpc.newStub(getChannel());
    }

}
