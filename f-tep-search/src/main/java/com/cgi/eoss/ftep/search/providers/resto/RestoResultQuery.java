package com.cgi.eoss.ftep.search.providers.resto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Duration;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestoResultQuery {
    @JsonProperty("originalFilters")
    private Map<String, Object> originalFilters;
    @JsonProperty("appliedFilters")
    private Map<String, Object> appliedFilters;
    @JsonProperty("processingTime")
    private Duration processingTime;
}