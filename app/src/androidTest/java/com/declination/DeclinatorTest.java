package com.declination;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.declination.beans.RuleBean;
import com.declination.enums.Case;
import com.declination.enums.Gender;
import com.declination.enums.NamePart;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class DeclinatorTest {

    private Declinator maker;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        maker = Declinator.getInstance(context);
    }

    @Test
    public void testApplyModToName() {
        assertEquals(maker.applyModToName("--", "test"), "te");
        assertEquals(maker.applyModToName("--st", "test"), "test");
        assertEquals(maker.applyModToName("st", "test"), "testst");
        assertEquals(maker.applyModToName(".", "test"), "test");
        assertEquals(maker.applyModToName("", "test"), "test");

    }

    @Test
    public void testFindModInRuleBeanList() {
        List<RuleBean> ruleBeanList = Arrays.asList(
                new RuleBean(Gender.MALE.getValue(), Arrays.asList("--11", "--12", "--13", "--14", "--15"), Arrays.asList("one")),
                new RuleBean(Gender.FEMALE.getValue(), Arrays.asList("--21", "--22", "--23", "--24", "--25"), Arrays.asList("two")),
                new RuleBean(Gender.ANDROGYNOUS.getValue(), Arrays.asList("--31", "--32", "--33", "--34", "--35"), Arrays.asList("three"))
        );

        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.MALE, "testone").getMods().get(Case.GENITIVE.getValue()), "--11");
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.FEMALE, "testone"), null);
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.ANDROGYNOUS, "testone"), null);
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.MALE, "testone").getMods().get(Case.DATIVE.getValue()), "--12");
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.MALE, "teston"), null);

        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.FEMALE, "testtwo").getMods().get(Case.GENITIVE.getValue()), "--21");
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.MALE, "testtwo"), null);
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.ANDROGYNOUS, "testone"), null);
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.FEMALE, "testtwo").getMods().get(Case.DATIVE.getValue()), "--22");
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.FEMALE, "testtw"), null);

        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.MALE, "testthree").getMods().get(Case.GENITIVE.getValue()), "--31");
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.FEMALE, "testthree").getMods().get(Case.GENITIVE.getValue()), "--31");
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.ANDROGYNOUS, "testone"), null);
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.MALE, "testthree").getMods().get(Case.DATIVE.getValue()), "--32");
        assertEquals(maker.findInRuleBeanList(ruleBeanList, Gender.MALE, "testtw"), null);

    }

    @Test
    public void testMake() {
        assertEquals(maker.make(NamePart.FIRSTNAME, Gender.MALE, Case.GENITIVE, "Ринат"), "Рината");
        assertEquals(maker.make(NamePart.FIRSTNAME, Gender.MALE, Case.DATIVE, "Ринат"), "Ринату");
        assertEquals(maker.make(NamePart.FIRSTNAME, Gender.MALE, Case.ACCUSATIVE, "Ринат"), "Рината");
        assertEquals(maker.make(NamePart.FIRSTNAME, Gender.MALE, Case.INSTRUMENTAL, "Ринат"), "Ринатом");
        assertEquals(maker.make(NamePart.FIRSTNAME, Gender.MALE, Case.PREPOSITIONAL, "Ринат"), "Ринате");

        assertEquals(maker.make(NamePart.LASTNAME, Gender.MALE, Case.GENITIVE, "Мулюков"), "Мулюкова");
        assertEquals(maker.make(NamePart.LASTNAME, Gender.MALE, Case.DATIVE, "Мулюков"), "Мулюкову");
        assertEquals(maker.make(NamePart.LASTNAME, Gender.MALE, Case.ACCUSATIVE, "Мулюков"), "Мулюкова");
        assertEquals(maker.make(NamePart.LASTNAME, Gender.MALE, Case.INSTRUMENTAL, "Мулюков"), "Мулюковым");
        assertEquals(maker.make(NamePart.LASTNAME, Gender.MALE, Case.PREPOSITIONAL, "Мулюков"), "Мулюкове");

        assertEquals(maker.make(NamePart.MIDDLENAME, Gender.MALE, Case.GENITIVE, "Рашитович"), "Рашитовича");
        assertEquals(maker.make(NamePart.MIDDLENAME, Gender.MALE, Case.DATIVE, "Рашитович"), "Рашитовичу");
        assertEquals(maker.make(NamePart.MIDDLENAME, Gender.MALE, Case.ACCUSATIVE, "Рашитович"), "Рашитовича");
        assertEquals(maker.make(NamePart.MIDDLENAME, Gender.MALE, Case.INSTRUMENTAL, "Рашитович"), "Рашитовичем");
        assertEquals(maker.make(NamePart.MIDDLENAME, Gender.MALE, Case.PREPOSITIONAL, "Рашитович"), "Рашитовиче");

    }

    @Test
    public void testMake2() {
        assertEquals(maker.male.firstname().toGenitive("Ринат"), "Рината");
        assertEquals(maker.male.firstname().toDative("Ринат"), "Ринату");
        assertEquals(maker.male.firstname().toAccusative("Ринат"), "Рината");
        assertEquals(maker.male.firstname().toInstrumental("Ринат"), "Ринатом");
        assertEquals(maker.male.firstname().toPrepositional("Ринат"), "Ринате");

        assertEquals(maker.male.lastname().toGenitive("Мулюков"), "Мулюкова");
        assertEquals(maker.male.lastname().toDative("Мулюков"), "Мулюкову");
        assertEquals(maker.male.lastname().toAccusative("Мулюков"), "Мулюкова");
        assertEquals(maker.male.lastname().toInstrumental("Мулюков"), "Мулюковым");
        assertEquals(maker.male.lastname().toPrepositional("Мулюков"), "Мулюкове");

        assertEquals(maker.male.middlename().toGenitive("Рашитович"), "Рашитовича");
        assertEquals(maker.male.middlename().toDative("Рашитович"), "Рашитовичу");
        assertEquals(maker.male.middlename().toAccusative("Рашитович"), "Рашитовича");
        assertEquals(maker.male.middlename().toInstrumental("Рашитович"), "Рашитовичем");
        assertEquals(maker.male.middlename().toPrepositional("Рашитович"), "Рашитовиче");

    }

    @Test
    public void test_Maria() {
        assertEquals(maker.female.firstname().toGenitive("Мария"), "Марии");
        assertEquals(maker.female.firstname().toDative("Мария"), "Марии");
        assertEquals(maker.female.firstname().toAccusative("Мария"), "Марию");
        assertEquals(maker.female.firstname().toInstrumental("Мария"), "Марией");
        assertEquals(maker.female.firstname().toPrepositional("Мария"), "Марии");
    }

    @Test
    public void test_genitive() {
        assertEquals(maker.female.firstname().toGenitive("Бьянка"), "Бьянки");
        assertEquals(maker.male.lastname().toGenitive("Левобережный"), "Левобережного");
        assertEquals(maker.male.firstname().toGenitive("Никита"), "Никиты");
        assertEquals(maker.male.firstname().toGenitive("Дима"), "Димы");
        assertEquals(maker.female.firstname().toGenitive("Ольга"), "Ольги");
        assertEquals(maker.female.lastname().toGenitive("Маковецкая"), "Маковецкой");
    }

    @Test
    public void test_my_names() {
        assertEquals("Васи", maker.makeNameGenitive("Вася"));
    }
}