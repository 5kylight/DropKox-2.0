package com.dropkox.categorizer;

import static com.dropkox.categorizer.suppliers.ClarifyInputSupplierFactory.getClarifyInputFor;
import clarifai2.api.ClarifaiClient;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import com.dropkox.categorizer.suppliers.UrlType;
import com.dropkox.core.exceptions.NoLabelsAssignedException;
import com.sun.istack.internal.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ImageCategorizationService {

    @NotNull
    private ClarifaiClient clarifaiClient;

    /**
     * Method returns labels (provided by external service) for the requested image.
     *
     * @param URL URL of the image
     * @param urlType type of url (web for internet URLs, local for file paths)
     * @return map of assigned labels and their certainty
     * @throws NoLabelsAssignedException when no labels could be assigned for the image
     */
    public Map<String, Float> getLabelsForImage(String URL, UrlType urlType) throws NoLabelsAssignedException {
        Supplier<ClarifaiInput> inputSupplier = getClarifyInputFor(URL, urlType);
        return getLabelsOrThrowException(inputSupplier);
    }

    private Map<String, Float> getLabelsOrThrowException(Supplier<ClarifaiInput> imageSupplier) throws NoLabelsAssignedException {
        return Optional.ofNullable(imageSupplier)
                .map(this::labelImageWithExternalAPI)
                .filter(map -> !map.isEmpty())
                .orElseThrow(NoLabelsAssignedException::new);
    }

    private Map<String, Float> labelImageWithExternalAPI(Supplier<ClarifaiInput> clarifaiInputSupplier) {
        List<ClarifaiOutput<Concept>> predictionResults = clarifaiClient.getDefaultModels()
                .generalModel()
                .predict()
                .withInputs(clarifaiInputSupplier.get())
                .executeSync()
                .get();

        final Map<String, Float> labels = new HashMap<>();

        Optional.of(predictionResults)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .filter(Objects::nonNull)
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
