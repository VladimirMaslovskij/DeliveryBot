package org.example.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.ArrayList;
import java.util.List;

@Entity(name="CloudTable")
public class Cloud {
    @Id
    private Long id;
    String categories;
    String admins;
    int catCount;
    int goodCoutn;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAdmins() {
        return admins;
    }

    public void setAdmins(String admins) {
        this.admins = admins;
    }

    public int getCatCount() {
        return catCount;
    }

    public void setCatCount(int catCount) {
        this.catCount = catCount;
    }

    public int getGoodCoutn() {
        return goodCoutn;
    }

    public void setGoodCoutn(int goodCoutn) {
        this.goodCoutn = goodCoutn;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }
    public List<String> getAdminIds() {
        List<String> adminIds = new ArrayList<>();
        try {
            adminIds = List.of(this.admins.split("/"));
        } catch (NullPointerException e) {
            System.out.println(e);
        }
        return adminIds;
    }
}
