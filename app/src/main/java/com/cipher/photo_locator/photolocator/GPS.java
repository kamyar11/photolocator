package com.cipher.photo_locator.photolocator;

/**
 * Created by root on 9/27/18.
 */
public class GPS {
    private static StringBuilder sb = new StringBuilder(20);

    public static String latitudeRef(double latitude) {
        return latitude<0.0d?"S":"N";
    }

    public static String longitudeRef(double longitude) {
        return longitude<0.0d?"W":"E";
    }

    synchronized public static final String convert(double latitude) {
        latitude=Math.abs(latitude);
        int degree = (int) latitude;
        latitude *= 60;
        latitude -= (degree * 60.0d);
        int minute = (int) latitude;
        latitude *= 60;
        latitude -= (minute * 60.0d);
        int second = (int) (latitude*1000.0d);
        sb.setLength(0);
        sb.append(degree);
        sb.append("/1,");
        sb.append(minute);
        sb.append("/1,");
        sb.append(second);
        sb.append("/1000,");
        return sb.toString();
    }
}
