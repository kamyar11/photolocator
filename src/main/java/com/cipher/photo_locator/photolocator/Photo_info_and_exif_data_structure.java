package com.cipher.photo_locator.photolocator;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.widget.Toast;

import java.text.SimpleDateFormat;

import static com.cipher.photo_locator.photolocator.MainActivity.calculateInSampleSize;

/**
 * Created by root on 7/27/18.
 */
//wrote by kamyar haghani
public class Photo_info_and_exif_data_structure {
    public String file_path,date;
    public Bitmap bitmap;
    public Location location=new Location("");
    public boolean is_sellected=false,has_location=false;
    public ExifInterface exifInterface;

    public Photo_info_and_exif_data_structure(String File_address){
        try{
            file_path=File_address;
            exifInterface=new ExifInterface(File_address);
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
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file_path,options);
            options.inSampleSize=calculateInSampleSize(options,150,150);
            options.inJustDecodeBounds = false;
            bitmap=BitmapFactory.decodeFile(file_path,options);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
