package com.dropkox.categorizer.filemanager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CategorizationFolderConfig {

    @Value("${classifiable.dir.in}")
    private String inputFolder;

    @Value("${classifiable.dir.out}")
    private String outputFolder;

    @Bean
    public CategorizationFolderProperties categorizationFolderProperties() {
        return CategorizationFolderProperties.builder().inputFolder(inputFolder).outputFolder(outputFolder).build();
    }
}
