package com.dropkox.model;

import com.dropkox.synchronizer.ISynchronizer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Date;

@AllArgsConstructor
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
    private ISynchronizer source;
    @NonNull
    private FileType fileType;
    private Date modificationDate;
}
