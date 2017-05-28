package com.dropkox.categorizer.filemanager;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Builder
@Data
public class CategorizationFolderProperties {
    @NonNull
    private String inputFolder;
    @NonNull
    private String outputFolder;

}
