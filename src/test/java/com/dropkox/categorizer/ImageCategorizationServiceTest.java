package com.dropkox.categorizer;

import com.dropkox.core.NoLabelsAssignedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.fail;

@SpringBootTest(classes = {ImageCategorizationService.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class ImageCategorizationServiceTest {

    @Autowired
    private ImageCategorizationService imageCategorizationService;


    @Test
    public void test() {
        try {
            imageCategorizationService.getImageLabels("https://samples.clarifai.com/metro-north.jpg");
        } catch (NoLabelsAssignedException e) {
            fail("No labels has been assigned for provided photo URL.");
        }
    }
}