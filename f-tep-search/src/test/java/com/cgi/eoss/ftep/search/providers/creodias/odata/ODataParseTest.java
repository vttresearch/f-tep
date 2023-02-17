package com.cgi.eoss.ftep.search.providers.creodias.odata;

import com.cgi.eoss.ftep.search.SearchConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ODataParseTest {

    private ObjectMapper objectMapper;

    String RESULT_STRING = "{\"@odata.context\":\"$metadata#Products(Attributes())\",\"@odata.count\":1,\"value\":[{\"@odata.mediaContentType\":\"application/octet-stream\",\"Id\":\"0c104c0f-0508-5f5d-9997-1b61ea3b20d6\",\"Name\":\"S2B_MSIL2A_20230209T055009_N0509_R048_T43SBB_20230209T084058.SAFE\",\"ContentType\":\"application/octet-stream\",\"ContentLength\":87761903,\"OriginDate\":\"2023-02-09T13:09:25.554Z\",\"PublicationDate\":\"2023-02-09T14:11:35.218Z\",\"ModificationDate\":\"2023-02-10T13:20:10.068Z\",\"Online\":true,\"EvictionDate\":\"\",\"S3Path\":\"/eodata/Sentinel-2/MSI/L2A/2023/02/09/S2B_MSIL2A_20230209T055009_N0509_R048_T43SBB_20230209T084058.SAFE\",\"Checksum\":[],\"ContentDate\":{\"Start\":\"2023-02-09T05:50:09.024Z\",\"End\":\"2023-02-09T05:50:09.024Z\"},\"Footprint\":\"geography'SRID=4326;POLYGON ((72.5924464494253 36.932372271486, 72.8639749541582 36.9386673115036, 72.83944991974 37.7956097636394, 72.8228889274477 37.7391519348119, 72.7813102068292 37.5915424381416, 72.739450374598 37.4440654236102, 72.6982025725122 37.2964008085188, 72.654574799707 37.1494305008013, 72.6100455848104 37.0026613724592, 72.5924464494253 36.932372271486))'\",\"Attributes\":[{\"@odata.type\":\"#OData.CSC.DoubleAttribute\",\"Name\":\"cloudCover\",\"Value\":98.799485,\"ValueType\":\"Double\"},{\"@odata.type\":\"#OData.CSC.IntegerAttribute\",\"Name\":\"orbitNumber\",\"Value\":30962,\"ValueType\":\"Integer\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"orbitDirection\",\"Value\":\"DESCENDING\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.DateTimeOffsetAttribute\",\"Name\":\"processingDate\",\"Value\":\"2023-02-09T08:40:58+00:00\",\"ValueType\":\"DateTimeOffset\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"productGroupId\",\"Value\":\"GS2B_20230209T055009_030962_N05.09\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"processingLevel\",\"Value\":\"S2MSI2A\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"processorVersion\",\"Value\":\"05.09\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"granuleidentifier\",\"Value\":\"S2B_OPER_MSI_L2A_TL_2BPS_20230209T084058_A030962_T43SBB_N05.09\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"platformShortName\",\"Value\":\"SENTINEL-2\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"datastripidentifier\",\"Value\":\"S2B_OPER_MSI_L2A_DS_2BPS_20230209T084058_S20230209T055524_N05.09\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"instrumentShortName\",\"Value\":\"MSI\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.IntegerAttribute\",\"Name\":\"relativeOrbitNumber\",\"Value\":48,\"ValueType\":\"Integer\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"platformSerialIdentifier\",\"Value\":\"B\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.StringAttribute\",\"Name\":\"productType\",\"Value\":\"S2MSI2A\",\"ValueType\":\"String\"},{\"@odata.type\":\"#OData.CSC.DateTimeOffsetAttribute\",\"Name\":\"beginningDateTime\",\"Value\":\"2023-02-09T05:50:09.024Z\",\"ValueType\":\"DateTimeOffset\"},{\"@odata.type\":\"#OData.CSC.DateTimeOffsetAttribute\",\"Name\":\"endingDateTime\",\"Value\":\"2023-02-09T05:50:09.024Z\",\"ValueType\":\"DateTimeOffset\"}]}]}";
    
    @Before
    public void setUp() {
        SearchConfig springContext = new SearchConfig();
        objectMapper = springContext.objectMapper();
    }
    
    @Test
    public void testParse() throws Exception {
        ODataResult odata = objectMapper.readValue(RESULT_STRING, ODataResult.class);
        System.out.println(odata);
        System.out.println(odata.getValues().get(0).toFeature());
        assertThat(odata.getCount(), is(1L));
    }
}
