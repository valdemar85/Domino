package com.declination.beans;

import java.util.List;

public class NameBean {
    /**
     * Исключения
     */
    private List<RuleBean> exceptions;
    /**
     * Правила
     */
    private List<RuleBean> suffixes;

    public List<RuleBean> getExceptions() {
        return exceptions;
    }

    public NameBean() {
    }

    public NameBean(List<RuleBean> exceptions, List<RuleBean> suffixes) {
        this.exceptions = exceptions;
        this.suffixes = suffixes;
    }

    public void setExceptions(List<RuleBean> exceptions) {
        this.exceptions = exceptions;
    }

    public List<RuleBean> getSuffixes() {
        return suffixes;
    }

    public void setSuffixes(List<RuleBean> suffixes) {
        this.suffixes = suffixes;
    }
}
