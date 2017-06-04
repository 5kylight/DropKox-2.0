package com.dropkox.categorizer.service;

import clarifai2.api.ClarifaiClient;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import com.dropkox.categorizer.suppliers.UrlType;
import com.dropkox.core.exceptions.NoLabelsAssignedException;
import com.dropkox.model.ImageLabel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.dropkox.categorizer.suppliers.ClarifyInputSupplierFactory.getClarifyInputFor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ImageCategorizationService {

    @NonNull
    private ClarifaiClient clarifaiClient;

    /**
     * Method returns labels (provided by external service) for the requested image.
     *
     * @param URL URL of the image
     * @param urlType type of url (web for internet URLs, local for file paths)
     * @return map of assigned labels and their certainty
     * @throws NoLabelsAssignedException when no labels could be assigned for the image
     */
    public List<ImageLabel> getLabelsForImage(String URL, UrlType urlType) throws NoLabelsAssignedException {
        Supplier<ClarifaiInput> inputSupplier = getClarifyInputFor(URL, urlType);
        return getLabelsOrThrowException(inputSupplier);
    }

    private List<ImageLabel> getLabelsOrThrowException(Supplier<ClarifaiInput> imageSupplier) throws NoLabelsAssignedException {
        return Optional.ofNullable(imageSupplier)
                .map(this::labelImageWithExternalAPI)
                .filter(map -> !map.isEmpty())
                .orElseThrow(NoLabelsAssignedException::new);
    }

    private List<ImageLabel> labelImageWithExternalAPI(Supplier<ClarifaiInput> clarifaiInputSupplier) {
        List<ClarifaiOutput<Concept>> predictionResults = new LinkedList<>();
        try {
             predictionResults.addAll(clarifaiClient.getDefaultModels()
                    .generalModel()
                    .predict()
                    .withInputs(clarifaiInputSupplier.get())
                    .executeSync()
                    .get());
        } catch (Exception e ) {
            e.printStackTrace();
        }
        return Optional.of(predictionResults)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .filter(Objects::nonNull)
                .map(ClarifaiOutput::data)
                .orElseGet(LinkedList::new)
                .stream()
                .filter(Objects::nonNull)
                .filter(this::isConceptNameNotNull)
                .map(c -> ImageLabel.builder().name(c.name()).probability(c.value()).build())
                .collect(Collectors.toList());
    }

    private boolean isConceptNameNotNull(Concept concept) {
        return concept.name() != null;
    }
}
