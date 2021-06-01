package com.grupposts.trasporti.features.rfid.models;

import java.text.SimpleDateFormat;

public class Conversion {

    public static int hexToDecimal(String s) {
        String digits = "0123456789ABCDEF";
        s = s.toUpperCase();
        int val = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digits.indexOf(c);
            val = 16*val + d;
        }
        return val;
    }

    public static String hexToTime(String s) {

        int decimal = hexToDecimal(s);

        // - 2 hours
        java.util.Date date=new java.util.Date((long)decimal*1000 -3600*1000*2);
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        String strDate = formatter.format(date);

        return strDate;
    }

    //formula from the manual
    public static String ConvertHexToTemperature(String s) {

        int decimal = hexToDecimal(s);
        System.out.println(decimal);
        if(decimal>=0 && decimal<=2240){
            return String.valueOf((double)decimal/(double)32);
        }
        else {
            return String.valueOf((double)(decimal-8192)/(double)32);
        }



    }
}
