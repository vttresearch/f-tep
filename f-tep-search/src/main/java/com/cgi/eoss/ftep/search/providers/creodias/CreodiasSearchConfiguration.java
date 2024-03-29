package com.cgi.eoss.ftep.search.providers.creodias;

import com.cgi.eoss.ftep.catalogue.external.ExternalProductDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "ftep.search.creodias.enabled", havingValue = "true", matchIfMissing = true)
public class CreodiasSearchConfiguration {

    @Value("${ftep.search.creodias.baseUrl:https://datahub.creodias.eu/resto/}")
    private String baseUrl;
    @Value("${ftep.search.creodias.usableProductsOnly:false}")
    private boolean usableProductsOnly;
    private int downloadTimeout = 180;

    @Bean
    public CreodiasSearchProvider creodiasSearchProvider(OkHttpClient httpClient, ObjectMapper objectMapper, ExternalProductDataService externalProductService) {
        return new CreodiasSearchProvider(0,
                CreodiasSearchProperties.builder()
                        .baseUrl(HttpUrl.parse(baseUrl))
                        .usableProductsOnly(usableProductsOnly)
                        .build(),
                httpClient.newBuilder()
                        .connectTimeout(downloadTimeout, TimeUnit.SECONDS)
                        .readTimeout(downloadTimeout, TimeUnit.SECONDS)
                        .build(),
                objectMapper,
                externalProductService);
    }

}
