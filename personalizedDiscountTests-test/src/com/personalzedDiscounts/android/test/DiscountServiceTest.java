package com.personalzedDiscounts.android.test;

import android.util.Log;

import com.personalzedDiscounts.android.domain.Discount;
import com.personalzedDiscounts.android.service.DiscountService;
import junit.framework.Assert;
import junit.framework.TestCase;


public class DiscountServiceTest extends TestCase {

    private DiscountService service;

    public void setUp() throws Exception {
        service = new DiscountService();
    }

    public void testShouldFetchDiscount() throws Exception {
        Discount discount = service.fetchDiscount("product");
        Log.i("Discount 2", discount.getOff());
        Assert.assertEquals(discount.getOff(),"30 %");
    }
}
