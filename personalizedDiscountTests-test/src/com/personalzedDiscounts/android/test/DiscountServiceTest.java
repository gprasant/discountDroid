package com.personalzedDiscounts.android.test;

import android.test.AndroidTestCase;
import com.personalzedDiscounts.android.domain.Discount;
import com.personalzedDiscounts.android.service.DiscountService;
import junit.framework.Assert;


public class DiscountServiceTest extends AndroidTestCase {

    private DiscountService service;

    public void setUp() throws Exception {
        service = new DiscountService();
    }

    public void testShouldFetchDiscount() throws Exception {
        Discount discount = service.fetchDiscount("user", "product");
        Assert.assertTrue(discount.getOff().trim().matches("\\d{2} %"));
    }
}
