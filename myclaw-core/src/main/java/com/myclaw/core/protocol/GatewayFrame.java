package com.myclaw.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayFrame {

    @JsonProperty("type")
    private String type;

    // req / res fields
    @JsonProperty("id")
    private String id;

    @JsonProperty("method")
    private String method;

    @JsonProperty("params")
    private JsonNode params;

    @JsonProperty("ok")
    private Boolean ok;

    @JsonProperty("payload")
    private JsonNode payload;

    @JsonProperty("error")
    private GatewayError error;

    // event fields
    @JsonProperty("event")
    private String event;

    @JsonProperty("seq")
    private Long seq;

    public boolean isRequest() {
        return "req".equals(type);
    }

    public boolean isResponse() {
        return "res".equals(type);
    }

    public boolean isEvent() {
        return "event".equals(type);
    }
}
