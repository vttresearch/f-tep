package com.cgi.eoss.ftep.search.providers.creodias.odata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ODataResult {
    @JsonProperty("@odata.context")
    private String context;
    @JsonProperty("@odata.count")
    private long count;
    @JsonProperty("value")
    private List<ODataItem> values;
    
}
