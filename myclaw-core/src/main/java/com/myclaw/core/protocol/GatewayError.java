package com.myclaw.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayError {

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    public static GatewayError of(String code, String message) {
        return GatewayError.builder().code(code).message(message).build();
    }
}
