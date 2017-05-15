package com.dropkox.model;

import com.dropkox.synchronizer.Synchronizer;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Date;

@Data
@Builder
public class KoxFile {

    @NonNull
    private String id;
    @NonNull
    private String path;
    @NonNull
    private String name;
    @NonNull
    private Synchronizer source;
    @NonNull
    private FileType fileType;
    @NonNull
    private Date modificationDate;
}
