package com.dropkox.categorizer.service;


import com.dropkox.model.ImageLabel;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CategoryRetriever {

    /**
     * If the category does not exist, best category name is returned. Otherwise the category with the highest probability coefficient is returned.
     * @param existingCategories existing names of categories
     * @param labels labels to be searched for best option
     * @return category name
     */
    public String getCategoryName(List<String> existingCategories, List<ImageLabel> labels) {
        lowerCaseLabels(labels);

        List<ImageLabel> repeated = getLabelsMatchingExistingCategories(existingCategories, labels);

        return getTopCategoryName(repeated.isEmpty() ? labels: repeated);
    }

    private void lowerCaseLabels(List<ImageLabel> labels) {
        labels.stream().forEach(label -> label.setName(label.getName().toLowerCase()));
    }

    private List<ImageLabel> getLabelsMatchingExistingCategories(List<String> existingCategories, List<ImageLabel> labels) {
        Predicate<ImageLabel> categoryAlreadyExists = getDoesAlreadyExistPredicate(existingCategories);

        return labels.stream().filter(categoryAlreadyExists).collect(Collectors.toList());
    }

    private String getTopCategoryName(List<ImageLabel> labels) {
        return labels.stream().max(this::compareLabelsByProbability).map(ImageLabel::getName).orElseThrow(IllegalStateException::new);
    }

    private Predicate<ImageLabel> getDoesAlreadyExistPredicate(List<String> existingCategories) {
        return label -> existingCategories.stream().anyMatch(label.getName()::equals);
    }

    private int compareLabelsByProbability(ImageLabel o1, ImageLabel o2) {
        return o1.getProbability().compareTo(o2.getProbability());
    }
}
