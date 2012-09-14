package com.personalzedDiscounts.android.domain;

public class Discount {
    String off;
    String product;

    public Discount(String off, String product) {
        this.off = off;
        this.product = product;
    }

    public String getOff() {
        return off;
    }

    public void setOff(String off) {
        this.off = off;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }
}
