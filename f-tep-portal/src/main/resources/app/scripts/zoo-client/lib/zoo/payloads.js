if (!!!templates) var templates = {};
templates["payload_DescribeProcess"] = new Hogan.Template({code: function (c,p,i) { var t=this;t.b(i=i||"");t.b("<DescribeProcess xmlns=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 ../wpsDescribeProcess_request.xsd\" service=\"WPS\" version=\"1.0.0\" language=\"");t.b(t.v(t.f("language",c,p,0)));t.b("\">");t.b("\n" + i);if(t.s(t.f("identifiers",c,p,1),c,p,0,357,399,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("  <ows:Identifier>");t.b(t.v(t.d(".",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);});c.pop();}t.b("</DescribeProcess>");return t.fl(); },partials: {}, subs: {  }});
templates["payload_Execute"] = new Hogan.Template({code: function (c,p,i) { var t=this;t.b(i=i||"");t.b("<wps:Execute service=\"WPS\" version=\"1.0.0\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0");t.b("\n" + i);t.b("../wpsExecute_request.xsd\" language=\"");t.b(t.v(t.f("language",c,p,0)));t.b("\">");t.b("\n" + i);t.b("  <!-- template-version: 0.21 -->");t.b("\n" + i);t.b("	<ows:Identifier>");t.b(t.v(t.f("Identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("	<wps:DataInputs>");t.b("\n" + i);if(t.s(t.f("DataInputs",c,p,1),c,p,0,449,2147,"{{ }}")){t.rs(c,p,function(c,p,t){if(t.s(t.f("is_literal",c,p,1),c,p,0,465,674,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("		<wps:Input>");t.b("\n" + i);t.b("			<ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("			<wps:Data>");t.b("\n" + i);t.b("				<wps:LiteralData");if(t.s(t.f("dataType",c,p,1),c,p,0,578,602,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" dataType=\"");t.b(t.v(t.f("dataType",c,p,0)));t.b("\"");});c.pop();}t.b(">");t.b(t.v(t.f("value",c,p,0)));t.b("</wps:LiteralData>");t.b("\n" + i);t.b("			</wps:Data>");t.b("\n" + i);t.b("		</wps:Input>");t.b("\n" + i);});c.pop();}if(t.s(t.f("is_bbox",c,p,1),c,p,0,702,1045,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("		<wps:Input>");t.b("\n" + i);t.b("			<ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("			<wps:Data>");t.b("\n" + i);t.b("				<wps:BoundingBoxData ows:crs=\"");t.b(t.v(t.f("crs",c,p,0)));t.b("\" ows:dimensions=\"");t.b(t.v(t.f("dimension",c,p,0)));t.b("\">");t.b("\n" + i);t.b("            <ows:LowerCorner>");t.b(t.v(t.f("lowerCorner",c,p,0)));t.b("</ows:LowerCorner>");t.b("\n" + i);t.b("            <ows:UpperCorner>");t.b(t.v(t.f("upperCorner",c,p,0)));t.b("</ows:UpperCorner>");t.b("\n" + i);t.b("         </wps:BoundingBoxData>");t.b("\n" + i);t.b("			</wps:Data>");t.b("\n" + i);t.b("		</wps:Input>");t.b("\n" + i);});c.pop();}if(t.s(t.f("is_complex",c,p,1),c,p,0,1073,2131,"{{ }}")){t.rs(c,p,function(c,p,t){if(t.s(t.f("is_reference",c,p,1),c,p,0,1091,1682,"{{ }}")){t.rs(c,p,function(c,p,t){if(t.s(t.f("is_get",c,p,1),c,p,0,1103,1367,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("		<wps:Input>");t.b("\n" + i);t.b("			<ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("			<wps:Reference xlink:href=\"");t.b(t.v(t.f("href",c,p,0)));t.b("\"");if(t.s(t.f("schema",c,p,1),c,p,0,1219,1238,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" schema=\"");t.b(t.v(t.f("shema",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("mimeType",c,p,1),c,p,0,1262,1286,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" mimeType=\"");t.b(t.v(t.f("mimeType",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("encoding",c,p,1),c,p,0,1312,1336,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" encoding=\"");t.b(t.v(t.f("encoding",c,p,0)));t.b("\"");});c.pop();}t.b("/>");t.b("\n" + i);t.b("		</wps:Input>");t.b("\n" + i);});c.pop();}if(t.s(t.f("is_post",c,p,1),c,p,0,1391,1669,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("		<wps:Input>");t.b("\n" + i);t.b("			<ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("			<wps:Reference xlink:href=\"");t.b(t.v(t.f("href",c,p,0)));t.b("\" method=\"");t.b(t.v(t.f("method",c,p,0)));t.b("\">");t.b("\n" + i);if(t.s(t.f("headers",c,p,1),c,p,0,1530,1583,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("			  <wps:Header key=\"");t.b(t.v(t.f("key",c,p,0)));t.b("\" value=\"");t.b(t.v(t.f("value",c,p,0)));t.b("\" />");t.b("\n" + i);});c.pop();}t.b("			  <wps:Body>");t.b(t.t(t.f("value",c,p,0)));t.b("</wps:Body>");t.b("\n" + i);t.b("			</wps:Reference>");t.b("\n" + i);t.b("		</wps:Input>");t.b("\n" + i);});c.pop();}});c.pop();}if(!t.s(t.f("is_reference",c,p,1),c,p,1,0,0,"")){t.b("		<wps:Input>");t.b("\n" + i);t.b("      <ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("      <wps:Data>");t.b("\n" + i);t.b("        <wps:ComplexData");if(t.s(t.f("schema",c,p,1),c,p,0,1838,1857,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" schema=\"");t.b(t.v(t.f("shema",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("mimeType",c,p,1),c,p,0,1881,1905,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" mimeType=\"");t.b(t.v(t.f("mimeType",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("encoding",c,p,1),c,p,0,1931,1955,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" encoding=\"");t.b(t.v(t.f("encoding",c,p,0)));t.b("\"");});c.pop();}t.b(">");if(t.s(t.f("is_XML",c,p,1),c,p,0,1980,1994,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("\n" + i);t.b("	 ");t.b(t.t(t.f("value",c,p,0)));});c.pop();}if(!t.s(t.f("is_XML",c,p,1),c,p,1,0,0,"")){t.b("<![CDATA[");t.b(t.t(t.f("value",c,p,0)));t.b("]]>");};t.b("\n" + i);t.b("        </wps:ComplexData>");t.b("\n" + i);t.b("      </wps:Data>");t.b("\n" + i);t.b("    </wps:Input>");t.b("\n" + i);};});c.pop();}});c.pop();}t.b("	</wps:DataInputs>	");t.b("\n" + i);t.b("	<wps:ResponseForm>");t.b("\n" + i);if(t.s(t.f("RawDataOutput",c,p,1),c,p,0,2221,2383,"{{ }}")){t.rs(c,p,function(c,p,t){if(t.s(t.f("DataOutputs",c,p,1),c,p,0,2238,2366,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("    <wps:RawDataOutput mimeType=\"");t.b(t.v(t.f("mimeType",c,p,0)));t.b("\">");t.b("\n" + i);t.b("      <ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("    </wps:RawDataOutput>");t.b("\n" + i);});c.pop();}});c.pop();}if(!t.s(t.f("RawDataOutput",c,p,1),c,p,1,0,0,"")){t.b("    <wps:ResponseDocument");if(t.s(t.f("storeExecuteResponse",c,p,1),c,p,0,2471,2519,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" storeExecuteResponse=\"");t.b(t.v(t.f("storeExecuteResponse",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("lineage",c,p,1),c,p,0,2556,2578,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" lineage=\"");t.b(t.v(t.f("lineage",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("status",c,p,1),c,p,0,2601,2621,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" status=\"");t.b(t.v(t.f("status",c,p,0)));t.b("\"");});c.pop();}t.b(">");t.b("\n" + i);if(t.s(t.f("DataOutputs",c,p,1),c,p,0,2650,3189,"{{ }}")){t.rs(c,p,function(c,p,t){if(t.s(t.f("is_literal",c,p,1),c,p,0,2666,2842,"{{ }}")){t.rs(c,p,function(c,p,t){t.b("      <wps:Output");if(t.s(t.f("dataType",c,p,1),c,p,0,2697,2721,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" dataType=\"");t.b(t.v(t.f("dataType",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("uom",c,p,1),c,p,0,2742,2756,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" uom=\"");t.b(t.v(t.f("uom",c,p,0)));t.b("\"");});c.pop();}t.b(">");t.b("\n" + i);t.b("        <ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("      </wps:Output>");t.b("\n" + i);});c.pop();}if(!t.s(t.f("is_literal",c,p,1),c,p,1,0,0,"")){t.b("      <wps:Output");if(t.s(t.f("asReference",c,p,1),c,p,0,2907,2937,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" asReference=\"");t.b(t.v(t.f("asReference",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("schema",c,p,1),c,p,0,2964,2984,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" schema=\"");t.b(t.v(t.f("schema",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("mimeType",c,p,1),c,p,0,3008,3032,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" mimeType=\"");t.b(t.v(t.f("mimeType",c,p,0)));t.b("\"");});c.pop();}if(t.s(t.f("encoding",c,p,1),c,p,0,3058,3082,"{{ }}")){t.rs(c,p,function(c,p,t){t.b(" encoding=\"");t.b(t.v(t.f("encoding",c,p,0)));t.b("\"");});c.pop();}t.b(">");t.b("\n" + i);t.b("        <ows:Identifier>");t.b(t.v(t.f("identifier",c,p,0)));t.b("</ows:Identifier>");t.b("\n" + i);t.b("      </wps:Output>");t.b("\n" + i);};});c.pop();}t.b("    </wps:ResponseDocument>");t.b("\n" + i);};t.b("  </wps:ResponseForm>	");t.b("\n" + i);t.b("</wps:Execute>");return t.fl(); },partials: {}, subs: {  }});
templates["payload_GetCapabilities"] = new Hogan.Template({code: function (c,p,i) { var t=this;t.b(i=i||"");t.b("<wps:GetCapabilities xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 ../wpsGetCapabilities_request.xsd\" language=\"");t.b(t.v(t.f("language",c,p,0)));t.b("\" service=\"WPS\">");t.b("\n" + i);t.b("    <wps:AcceptVersions>");t.b("\n" + i);t.b("        <ows:Version>1.0.0</ows:Version>");t.b("\n" + i);t.b("    </wps:AcceptVersions>");t.b("\n" + i);t.b("</wps:GetCapabilities>");return t.fl(); },partials: {}, subs: {  }});
