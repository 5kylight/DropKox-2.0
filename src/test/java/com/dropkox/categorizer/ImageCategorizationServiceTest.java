package com.dropkox.categorizer;

import com.dropkox.categorizer.service.CategorizationServiceConfig;
import com.dropkox.categorizer.service.ImageCategorizationService;
import com.dropkox.categorizer.suppliers.UrlType;
import com.dropkox.core.exceptions.NoLabelsAssignedException;
import com.dropkox.model.ImageLabel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

@SpringBootTest(classes = {ImageCategorizationService.class, CategorizationServiceConfig.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class ImageCategorizationServiceTest {

    @Autowired
    private ImageCategorizationService imageCategorizationService;


    @Test
    public void testImageLabeling() {
        try {
            List<ImageLabel> results =
                    imageCategorizationService
                            .getLabelsForImage("https://samples.clarifai.com/metro-north.jpg", UrlType.WEB);

            assertFalse("Method should throw exception in case of no labels.", results.isEmpty());
        } catch (NoLabelsAssignedException e) {
            fail("No labels has been assigned for provided photo URL. " +
                    "Requirements for integration with external service unsatisfied.");
        }

    }

    @Test
    public void testLocalImageLabeling() {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("car.jpeg").getFile());

            List<ImageLabel> results =
                    imageCategorizationService
                            .getLabelsForImage(file.getAbsolutePath(), UrlType.LOCAL);

            assertFalse("Method should throw exception in case of no labels.", results.isEmpty());
        } catch (NoLabelsAssignedException e) {
            fail("No labels has been assigned for provided photo URL. " +
                    "Requirements for integration with external service unsatisfied.");
        }

    }
}