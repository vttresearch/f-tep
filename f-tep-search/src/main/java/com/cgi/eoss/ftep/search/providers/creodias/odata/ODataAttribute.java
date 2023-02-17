package com.cgi.eoss.ftep.search.providers.creodias.odata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ODataAttribute {
    @JsonProperty("@odata.type")
    private String type;
    @JsonProperty("Name")
    private String name;
    @JsonProperty("Value")
    private String value;
    @JsonProperty("ValueType")
    private String valueType;
}
