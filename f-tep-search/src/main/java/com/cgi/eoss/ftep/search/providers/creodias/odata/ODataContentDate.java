package com.cgi.eoss.ftep.search.providers.creodias.odata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ODataContentDate {
    @JsonProperty("Start")
    private String start;
    @JsonProperty("End")
    private String end;
}
