package com.dropkox.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Builder
@Data
public class FileEvent {

    @NonNull
    private KoxFile koxFile;
    @NonNull
    private Long timestamp;
    @NonNull
    private EventType eventType;

}
