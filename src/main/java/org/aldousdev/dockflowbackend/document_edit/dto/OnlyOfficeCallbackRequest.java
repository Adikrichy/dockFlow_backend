package org.aldousdev.dockflowbackend.document_edit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyOfficeCallbackRequest {

    private Integer status;

    private String url;

    @JsonProperty("changesurl")
    private String changesUrl;

    private String key;
}
