package com.geekyan.controller;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;

import org.junit.Test;

public class TranslateControllerTest {

    @Test
    public void detectsEnglishWordInputs() {
        assertEquals("EN_WORD_TO_ZH", TranslateController.detectEnglishQueryType("apple"));
        assertEquals("EN_WORD_TO_ZH", TranslateController.detectEnglishQueryType("well-known"));
    }

    @Test
    public void detectsEnglishPhraseInputs() {
        assertEquals("EN_PHRASE_TO_ZH", TranslateController.detectEnglishQueryType("in the direction of"));
        assertEquals("EN_PHRASE_TO_ZH", TranslateController.detectEnglishQueryType("take off"));
        assertEquals("EN_PHRASE_TO_ZH", TranslateController.detectEnglishQueryType("northwest wind"));
    }

    @Test
    public void detectsEnglishSentenceInputs() {
        assertEquals("EN_SENTENCE_TO_ZH", TranslateController.detectEnglishQueryType("look forward to hearing from you"));
        assertEquals("EN_SENTENCE_TO_ZH", TranslateController.detectEnglishQueryType("in the direction of the wind that blows from the northwest"));
        assertEquals("EN_SENTENCE_TO_ZH", TranslateController.detectEnglishQueryType("Where does the wind come from?"));
        assertEquals("EN_SENTENCE_TO_ZH", TranslateController.detectEnglishQueryType("I wonder which way the wind is blowing"));
        assertEquals("EN_SENTENCE_TO_ZH", TranslateController.detectEnglishQueryType("successfully applied to financial fraud detection"));
    }

    @Test
    public void detectsAllSixQueryTypes() throws Exception {
        TranslateController controller = new TranslateController();
        Method detectQueryType = TranslateController.class.getDeclaredMethod("detectQueryType", String.class);
        detectQueryType.setAccessible(true);

        assertEquals("EN_WORD_TO_ZH", detectQueryType.invoke(controller, "apple"));
        assertEquals("EN_PHRASE_TO_ZH", detectQueryType.invoke(controller, "northwest wind"));
        assertEquals("EN_SENTENCE_TO_ZH", detectQueryType.invoke(controller, "Where does the wind come from?"));
        assertEquals("ZH_WORD_TO_EN", detectQueryType.invoke(controller, "苹果"));
        assertEquals("ZH_PHRASE_TO_EN", detectQueryType.invoke(controller, "西北风"));
        assertEquals("ZH_SENTENCE_TO_EN", detectQueryType.invoke(controller, "风从哪里来？"));
    }
}
