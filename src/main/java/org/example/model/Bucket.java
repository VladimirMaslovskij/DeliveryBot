package org.example.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.*;

@Entity(name="bucket_table")
public class Bucket {
    @Id
    Long id;
    String goodsId;
    Float fullPrice;
    String additionalGoods;
    public void deleteGood(String id) {
        String[] str = goodsId.split("/");
        List<String> list = new ArrayList<>(Arrays.asList(str));
        list.remove(id);
        StringBuilder builder = new StringBuilder();
        for (String good:list) {
            builder.append(good + "/");
        }
        this.goodsId = String.valueOf(builder);
    }
    public void deleteAdditionalGood(String id, String delPrice) {
        String goodAd[] = this.additionalGoods.split("/");
        List<String> addList = new ArrayList<>(Arrays.asList(goodAd));
        List<String> goodIdList = new ArrayList<>(Arrays.asList(this.goodsId.split("/")));
        addList.remove(id + ":" + delPrice);
        goodIdList.remove(id);
        this.fullPrice = this.fullPrice - Float.valueOf(delPrice);
        StringBuilder newAdditionalGoods = new StringBuilder();
        StringBuilder newGoodsId = new StringBuilder();
        for (String params:addList) {
            newAdditionalGoods.append(params + "/");
        }
        for (String goods:goodIdList) {
            newGoodsId.append(goods + "/");
        }
        this.goodsId = String.valueOf(newGoodsId);
        this.additionalGoods = String.valueOf(newAdditionalGoods);
    }
    public void deletePrice(Float price) {
        this.fullPrice = this.fullPrice - price;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public Float getFullPrice() {
        return fullPrice;
    }

    public void setFullPrice(Float fullPrice) {
        this.fullPrice = fullPrice;
    }

    public String getAdditionalGoods() {
        return additionalGoods;
    }

    public void setAdditionalGoods(String additionalGoods) {
        this.additionalGoods = additionalGoods;
    }
}
