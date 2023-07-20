package com.declination;

import android.content.Context;
import com.declination.beans.NameBean;
import com.declination.beans.RootBean;
import com.declination.beans.RuleBean;
import com.declination.enums.Case;
import com.declination.enums.Gender;
import com.declination.enums.NamePart;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Declinator {

    private static final String DEFAULT_PATH_TO_RULES_FILE = "rules.json";
    private static final String MODS_KEEP_IT_ALL_SYMBOL = ".";
    private static final String MODS_REMOVE_LETTER_SYMBOL = "-";

    private RootBean rootRulesBean;

    public GenderCurryedMaker male = new GenderCurryedMaker(Gender.MALE);
    public GenderCurryedMaker female = new GenderCurryedMaker(Gender.FEMALE);
    public GenderCurryedMaker androgynous = new GenderCurryedMaker(Gender.ANDROGYNOUS);


    private Declinator(String pathToRulesFile, Context applicationContext) {
        try (InputStream is = applicationContext.getAssets().open(pathToRulesFile);
            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
            }
            String jsonTxt = sb.toString();
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            rootRulesBean = objectMapper.readValue(jsonTxt, RootBean.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Declinator getInstance(Context applicationContext) {
        return getInstance(DEFAULT_PATH_TO_RULES_FILE, applicationContext);
    }

    public static Declinator getInstance(String pathToRulesFile, Context applicationContext) {
        return new Declinator(pathToRulesFile, applicationContext);
    }

    public String makeNameGenitive(String originalName) {
        String result = make(NamePart.FIRSTNAME, Gender.ANDROGYNOUS, Case.GENITIVE, originalName);
        if (originalName.equals(result)) {
            result = make(NamePart.FIRSTNAME, Gender.MALE, Case.GENITIVE, originalName);
        }
        if (originalName.equals(result)) {
            result = make(NamePart.FIRSTNAME, Gender.FEMALE, Case.GENITIVE, originalName);
        }
        return result;
    }

    public String make(NamePart namePart, Gender gender, Case caseToUse, String originalName) {
        if (rootRulesBean == null) {
            return originalName;
        }
        String result = originalName;
        NameBean nameBean;

        switch (namePart) {
            case FIRSTNAME:
                nameBean = rootRulesBean.getFirstname();
                break;
            case LASTNAME:
                nameBean = rootRulesBean.getLastname();
                break;
            case MIDDLENAME:
                nameBean = rootRulesBean.getMiddlename();
                break;
            default:
                nameBean = rootRulesBean.getMiddlename();
                break;
        }

        RuleBean ruleToUse = null;
        RuleBean exceptionRuleBean = findInRuleBeanList(nameBean.getExceptions(), gender, originalName);
        RuleBean suffixRuleBean = findInRuleBeanList(nameBean.getSuffixes(), gender, originalName);
        if (exceptionRuleBean != null && exceptionRuleBean.getGender().equals(gender.getValue())) {
            ruleToUse = exceptionRuleBean;
        } else if (suffixRuleBean != null && suffixRuleBean.getGender().equals(gender.getValue())) {
            ruleToUse = suffixRuleBean;
        } else {
            ruleToUse = exceptionRuleBean != null ? exceptionRuleBean : suffixRuleBean;
        }

        if (ruleToUse != null) {
            String modToApply = ruleToUse.getMods().get(caseToUse.getValue());
            result = applyModToName(modToApply, originalName);
        }

        return result;
    }

    protected String applyModToName(String modToApply, String name) {
        String result = name;
        if (!modToApply.equals(MODS_KEEP_IT_ALL_SYMBOL)) {
            if (modToApply.contains(MODS_REMOVE_LETTER_SYMBOL)) {
                for (int i = 0; i < modToApply.length(); i++) {
                    if (Character.toString(modToApply.charAt(i)).equals(MODS_REMOVE_LETTER_SYMBOL)) {
                        result = result.substring(0, result.length() - 1);
                    } else {
                        result += modToApply.substring(i);
                        break;
                    }
                }
            } else {
                result = name + modToApply;
            }
        }
        return result;
    }

    protected RuleBean findInRuleBeanList(List<RuleBean> ruleBeanList, Gender gender, String originalName) {
        RuleBean result = null;
        if (ruleBeanList != null) {
            out:
            for(RuleBean ruleBean : ruleBeanList) {
                for (String test : ruleBean.getTest()) {
                    if (originalName.endsWith(test)) {
                        if (ruleBean.getGender().equals(Gender.ANDROGYNOUS.getValue())) {
                            result = ruleBean;
                            break out;
                        } else if ((ruleBean.getGender().equals(gender.getValue()))) {
                            result = ruleBean;
                            break out;
                        }
                    }
                }
            }
        }

        return result;
    }

    protected class GenderCurryedMaker {
        private Gender gender;

        protected GenderCurryedMaker(Gender gender) {
            this.gender = gender;
        }

        public GenderAndNamePartCurryedMaker firstname() {
            return new GenderAndNamePartCurryedMaker(gender, NamePart.FIRSTNAME);
        }

        public GenderAndNamePartCurryedMaker lastname() {
            return new GenderAndNamePartCurryedMaker(gender, NamePart.LASTNAME);
        }

        public GenderAndNamePartCurryedMaker middlename() {
            return new GenderAndNamePartCurryedMaker(gender, NamePart.MIDDLENAME);
        }
    }

    protected class GenderAndNamePartCurryedMaker {
        private NamePart namePart;
        private Gender gender;

        protected GenderAndNamePartCurryedMaker(Gender gender, NamePart namePart) {
            this.gender = gender;
            this.namePart = namePart;
        }

        public String toGenitive(String name) {
            return make(namePart, gender, Case.GENITIVE, name);
        }

        public String toDative(String name) {
            return make(namePart, gender, Case.DATIVE, name);
        }

        public String toAccusative(String name) {
            return make(namePart, gender, Case.ACCUSATIVE, name);
        }

        public String toInstrumental(String name) {
            return make(namePart, gender, Case.INSTRUMENTAL, name);
        }

        public String toPrepositional(String name) {
            return make(namePart, gender, Case.PREPOSITIONAL, name);
        }
    }
}
