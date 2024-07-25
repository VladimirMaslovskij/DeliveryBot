package org.example.model;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name="category_table")
public class Category {
    @Id
    private long id;
    private String name;
    private boolean additionalPar;
    private String additionAsk;
    private String additionVars;
    private String varsCount;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAdditionalPar() {
        return additionalPar;
    }

    public void setAdditionalPar(boolean additionalPar) {
        this.additionalPar = additionalPar;
    }

    public String getAdditionAsk() {
        return additionAsk;
    }

    public void setAdditionAsk(String additionAsk) {
        this.additionAsk = additionAsk;
    }

    public String getAdditionVars() {
        return additionVars;
    }

    public void setAdditionVars(String additionVars) {
        this.additionVars = additionVars;
    }

    public String getVarsCount() {
        return varsCount;
    }

    public void setVarsCount(String varsCount) {
        this.varsCount = varsCount;
    }
    public String getParametr(int i) {
        String[] arrayPars = this.additionVars.split("/");
        return arrayPars[i];
    }
}
