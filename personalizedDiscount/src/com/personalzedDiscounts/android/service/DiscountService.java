package com.personalzedDiscounts.android.service;

import android.util.Log;
import com.personalzedDiscounts.android.domain.Discount;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DiscountService {

    public static final String PERSONALIZED_SERVICE_URL = "http://snoopy.apphb.com/api/discounts?user=%s&product=%s";

    public Discount fetchDiscount(String userName, String productName){
        Discount discount = new Discount("error", "error");
        
        try{
            String response = getDiscount(userName, productName);
            JSONObject jsonObject = new JSONObject(response);
            String off = jsonObject.getString("Off");
            String name = jsonObject.getString("Product");
            discount.setOff(off);
            discount.setProduct(name);
        }
        catch (Exception e){
        	Log.e("discount",e.getMessage());
        }
        return discount;
    }

    private String getDiscount(String user, String product) {
        StringBuilder builder = new StringBuilder();
        HttpClient client = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(String.format(PERSONALIZED_SERVICE_URL, user, product));
        httpGet.setHeader("Content-type","application/json");
        try {
            HttpResponse response = client.execute(httpGet);
            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if (statusCode == 200) {
                HttpEntity entity = response.getEntity();
                InputStream content = entity.getContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                System.out.println(builder.toString());
            } else {
                Log.e(this.getClass().getName(), "fetch failed");
            }
        } catch (Exception e)  {
            e.printStackTrace();
        }
        return builder.toString();
    }
}
