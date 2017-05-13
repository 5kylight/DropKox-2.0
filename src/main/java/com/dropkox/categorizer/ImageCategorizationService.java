package com.dropkox.categorizer;

import clarifai2.api.ClarifaiClient;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.input.image.ClarifaiImage;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import com.dropkox.core.NoLabelsAssignedException;
import com.sun.istack.internal.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ImageCategorizationService {

    @NotNull
    private ClarifaiClient clarifaiClient;

    public Map<String, Float> getImageLabels(String URL) throws NoLabelsAssignedException {
        return Optional.ofNullable(URL)
                .map(this::retrieveLabels)
                .filter(map -> !map.isEmpty())
                .orElseThrow(NoLabelsAssignedException::new);
    }

    private Map<String, Float> retrieveLabels(String URL) {
        List<ClarifaiOutput<Concept>> predictionResults = clarifaiClient.getDefaultModels()
                .generalModel()
                .predict()
                .withInputs(ClarifaiInput.forImage(ClarifaiImage.of(URL)))
                .executeSync()
                .get();

        final Map<String, Float> labels = new HashMap<>();

        Optional.ofNullable(predictionResults)
                .map(list -> list.get(0))
                .map(ClarifaiOutput::data)
                .orElseGet(LinkedList::new)
                .stream()
                .filter(Objects::nonNull)
                .filter(this::isConceptNameNotNull)
                .forEach(concept -> labels.put(concept.name(), concept.value()));

        return labels;
    }

    private boolean isConceptNameNotNull(Concept concept) {
        return concept.name() != null;
    }
}
