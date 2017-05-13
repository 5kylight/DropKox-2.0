package com.dropkox.categorizer.suppliers;

import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.input.image.ClarifaiImage;

import java.io.File;
import java.util.function.Supplier;

public class ClarifyInputSupplierFactory {

    public static Supplier<ClarifaiInput> getClarifyInputFor(String URL, UrlType type) {
        switch (type) {
            case LOCAL:
                return () -> ClarifaiInput.forImage(ClarifaiImage.of(new File(URL)));
            case WEB:
            default:
                return () -> ClarifaiInput.forImage(ClarifaiImage.of(URL));
        }
    }
}
