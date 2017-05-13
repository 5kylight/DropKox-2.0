package com.dropkox.model;

import com.dropkox.synchronizer.Synchronizer;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class KoxFile {

    @NonNull
    private String id;
    @NonNull
    private Synchronizer source;
    @NonNull
    private FileType fileType;
}
