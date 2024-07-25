package org.example.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.ArrayList;
import java.util.List;

@Entity(name="goodTable")
public class Good {
    @Id
    private Long id;
    String name;
    Float price;
    String category;
    String info;
    String additionalPars;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Float getPrice() {
        return price;
    }

    public void setPrice(Float price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAdditionalPars() {
        return additionalPars;
    }

    public void setAdditionalPars(String additionalPars) {
        this.additionalPars = additionalPars;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
    public String getParametr(int i) {
        String[] arrayPars = this.additionalPars.split("/");
        return arrayPars[i];
    }
    public int getParNumber(String param) {
        int result = 0;
        String[] arrayPars = this.additionalPars.split("/");
        ArrayList<String> list = new ArrayList<>(List.of(arrayPars));
        result = list.indexOf(param);
        return result;
    }
}
