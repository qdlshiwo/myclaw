package com.myclaw.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myclaw.ai.provider.ModelProviderRegistry;
import com.myclaw.ai.provider.OpenAiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication(scanBasePackages = "com.myclaw")
public class MyClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyClawApplication.class, args);
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .baseUrl("https://api.openai.com")
            .build();
    }

    @Bean
    public OpenAiProvider openAiProvider(WebClient webClient,
                                          ObjectMapper objectMapper) {
        return new OpenAiProvider(webClient, objectMapper);
    }

    @Bean
    public ModelProviderRegistry modelProviderRegistry(OpenAiProvider openAiProvider) {
        ModelProviderRegistry registry = new ModelProviderRegistry();
        registry.register(openAiProvider);
        return registry;
    }
}
