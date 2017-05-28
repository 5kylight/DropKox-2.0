package com.dropkox.model;

import com.dropkox.synchronizer.ISynchronizer;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@RequiredArgsConstructor
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
    @NonNull
    private Date modificationDate;
}
