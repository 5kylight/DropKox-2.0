package com.dropkox.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Builder
@Data
public class ImageLabel {

    @NonNull
    private String name;
    @NonNull
    private Float probability;
}
