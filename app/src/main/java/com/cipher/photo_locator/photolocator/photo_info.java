package com.cipher.photo_locator.photolocator;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.ExifInterface;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.text.SimpleDateFormat;

import static com.cipher.photo_locator.photolocator.easyFileManager.calculateInSampleSize;

public class photo_info {
    public String file_path,date;
    public Bitmap bitmap;
    public Location location=new Location("");
    public boolean is_sellected=false,has_location=false;
    public ExifInterface exifInterface;

    public photo_info(String File_address){
        file_path=new File(File_address).getPath();
        try{
            exifInterface=new ExifInterface(this.file_path);
            float latlong[]=new float[2];
            if(exifInterface.getLatLong(latlong)){
                has_location=true;
                location.setLatitude(latlong[0]);
                location.setLongitude(latlong[1]);
            }else {
                return;
            }
            location.setTime(new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse(exifInterface.getAttribute(ExifInterface.TAG_DATETIME)).getTime());
            date=exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void load_Image_bitmap(){
        //to improve memory and performance,
        // only load this data when view is getting bound in RecyclerView.Adapter in a non ui thread
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file_path,options);
        options.inSampleSize=calculateInSampleSize(options,150,150);
        options.inJustDecodeBounds = false;
        this.bitmap=BitmapFactory.decodeFile(file_path,options);

    }
    public void set_new_location(LatLng loc){
        //alter location coordinates then call this method
        exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, GPS.convert(loc.latitude));
        exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, GPS.latitudeRef(loc.latitude));
        exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GPS.convert(loc.longitude));
        exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, GPS.longitudeRef(loc.longitude));
        location.setLatitude(loc.latitude);
        location.setLongitude(loc.longitude);
        try{
            exifInterface.saveAttributes();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
