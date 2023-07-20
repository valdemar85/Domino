package com.declination.beans;

public class RootBean {
    /**
     * Фамилия
     */
    private NameBean lastname;
    /**
     * Имя
     */
    private NameBean firstname;
    /**
     * Отчество
     */
    private NameBean middlename;

    public RootBean() {
    }

    public RootBean(NameBean lastname, NameBean firstname, NameBean middlename) {
        this.lastname = lastname;
        this.firstname = firstname;
        this.middlename = middlename;
    }

    public NameBean getLastname() {
        return lastname;
    }

    public void setLastname(NameBean lastname) {
        this.lastname = lastname;
    }

    public NameBean getFirstname() {
        return firstname;
    }

    public void setFirstname(NameBean firstname) {
        this.firstname = firstname;
    }

    public NameBean getMiddlename() {
        return middlename;
    }

    public void setMiddlename(NameBean middlename) {
        this.middlename = middlename;
    }
}
