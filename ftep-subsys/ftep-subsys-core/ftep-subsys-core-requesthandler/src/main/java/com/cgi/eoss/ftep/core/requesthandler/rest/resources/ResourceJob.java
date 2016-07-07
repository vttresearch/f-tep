
package com.cgi.eoss.ftep.core.requesthandler.rest.resources;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Links;
import com.github.jasminb.jsonapi.annotations.Type;

@Type("job")
public class ResourceJob {

  @Id
  private String id;

  // Attributes in HTTP request
  private String jobId;
  private String userId;

  // Attributes in req. and resp.
  private String inputs;
  private String outputs;
  private String guiEndpoint;

  // Attributes in resp.
  private String status;

  @Links
  private com.github.jasminb.jsonapi.Links links;



  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getInputs() {
    return inputs;
  }

  public void setInputs(String inputs) {
    this.inputs = inputs;
  }

  public String getOutputs() {
    return outputs;
  }

  public void setOutputs(String outputs) {
    this.outputs = outputs;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getGuiEndpoint() {
    return guiEndpoint;
  }

  public void setGuiEndpoint(String guiEndpoint) {
    this.guiEndpoint = guiEndpoint;
  }

}
