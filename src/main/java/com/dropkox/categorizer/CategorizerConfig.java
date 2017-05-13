package com.dropkox.categorizer;

import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import com.sun.istack.internal.NotNull;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CategorizerConfig {

    @NotNull
    @Value("${app.clarifai.client_id}")
    private String clientId;

    @NotNull
    @Value("${app.clarifai.client_secret}")
    private String clientSecret;

    @Bean
    public ClarifaiClient clarifaiClient() {
        return new ClarifaiBuilder(clientId, clientSecret).client(new OkHttpClient()).buildSync();
    }

}
