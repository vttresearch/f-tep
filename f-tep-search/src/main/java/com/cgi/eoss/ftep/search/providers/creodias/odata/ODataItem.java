package com.cgi.eoss.ftep.search.providers.creodias.odata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;

/**
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ODataItem {
    @JsonProperty("@odata.mediaContentType")
    private String mediaContentType;
    @JsonProperty("Id")
    private String id;
    @JsonProperty("Name")
    private String name;
    @JsonProperty("ContentType")
    private String contentType;
    @JsonProperty("ContentLength")
    private long contentLength;
    @JsonProperty("OriginDate")
    private String originDate;
    @JsonProperty("PublicationDate")
    private String publicationDate;
    @JsonProperty("ModificationDate")
    private String modificationDate;
    @JsonProperty("Online")
    private boolean online ;
    @JsonProperty("EvictionDate")
    private String evictionDate;
    @JsonProperty("S3Path")
    private String s3Path;
//    @JsonProperty("Checksum")
//    private String checksum;
    @JsonProperty("ContentDate")
    private ODataContentDate contentDate;
    @JsonProperty("Footprint")
    private String footprint;
    @JsonProperty("Attributes")
    private List<ODataAttribute> attributes;
    
    public Feature toFeature() {
        Feature f = new Feature();
        f.setId(id);
        f.setProperty("mediaContentType", mediaContentType);
        f.setProperty("name", name);
        f.setProperty("contentType", contentType);
        f.setProperty("contentLength", contentLength);
        f.setProperty("originDate", originDate);
        f.setProperty("publicationDate", publicationDate);
        f.setProperty("modificationDate", modificationDate);
        f.setProperty("online", online);
        f.setProperty("evictionDate", evictionDate);
        f.setProperty("s3Path", s3Path);
        f.setProperty("contentDateStart", contentDate.getStart());
        f.setProperty("contentDateEnd", contentDate.getEnd());
        for (ODataAttribute a: attributes) {
            f.setProperty(a.getName(), a.getValue());
        }
        if (footprint.startsWith("geography'SRID=4326;POLYGON ((")) {
            String coords = footprint.substring("geography'SRID=4326;POLYGON (".length());
            // Remove tailing )'
            coords = coords.substring(0, coords.length()-2);
            // Now it should be a list of rings (x1 y1, x2 y2, ... x1 y1),(...)
            int listStart = 1;
            int listEnd;
            Polygon p = new Polygon();
            int ring = 1;
            while ((listEnd = coords.indexOf(")", listStart)) > 0) {
                List<LngLatAlt> points = new ArrayList<>();
                
                String coordList = coords.substring(listStart, listEnd);
                String[] coordPairs = coordList.split(",");
                
                for (String pair:coordPairs) {
                    String[] xy = pair.trim().split("\\s+");
                    points.add(new LngLatAlt(Double.valueOf(xy[0]), Double.valueOf(xy[1])));
                }
                
                if (ring == 1) {
                    p.setExteriorRing(points);
                } else {
                    p.addInteriorRing(points);
                }
                
                listStart = listEnd + 1;
                ring += 1;
            }
            f.setGeometry(p);
        }
        return f;
    }
}
