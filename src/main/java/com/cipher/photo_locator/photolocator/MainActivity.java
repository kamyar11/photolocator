package com.cipher.photo_locator.photolocator;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private List<String> list=new ArrayList();
    private GoogleMap googleMap;
    private MapView mapView;
    private CountDownTimer countDownTimer;
    private RecyclerView.LayoutManager linearLayoutManager;
    private RecyclerView recyclerView;
    private Marker marker;
    private List<Bitmap> bitmaps=new ArrayList<>();
    private List<Photo_info_and_exif_data_structure> all_photos_list=new ArrayList<>();
    private fileadapter adap;
    private ProgressBar progressBar;
    private AsyncTask asyncTask;

    @TargetApi(23)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.INTERNET",
                "android.permission.RECEIVE_BOOT_COMPLETED"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(Build.VERSION.SDK_INT>22)askPermissions();
        linearLayoutManager=new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false);
        progressBar=(ProgressBar)findViewById(R.id.progressBar2);
        recyclerView=(RecyclerView)findViewById(R.id.images_list);
        recyclerView.setLayoutManager(linearLayoutManager);

        adap=new fileadapter();
        recyclerView.setAdapter(adap);

        mapView=(MapView)findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        try{
            MapsInitializer.initialize(getApplicationContext());
        }catch (Exception e){
            Toast.makeText(getApplicationContext(),e.toString(),Toast.LENGTH_LONG).show();
        }

        asyncTask=new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                load_all_the_pictures_on_folder("/sdcard/");
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressBar.setVisibility(View.GONE);
                if(all_photos_list.size()>0){
                    Toast.makeText(getApplicationContext(),"all photos with geo tags are loaded",Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(getApplicationContext(),"no photos with geo tags found",Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
        countDownTimer=new CountDownTimer(100000000,5000) {
            @Override
            public void onTick(long millisUntilFinished) {
                adap.notifyDataSetChanged();
            }
            @Override
            public void onFinish() {
                adap.notifyDataSetChanged();
            }
        }.start();
        adap.notifyDataSetChanged();
    }
    public class fileadapter extends RecyclerView.Adapter<fileadapter.fileholder> {
        public fileadapter(){
        }
        public class fileholder extends RecyclerView.ViewHolder{
            public ImageView image;
            public fileholder(View view) {
                super(view);
                image=(ImageView)view.findViewById(R.id.image);
            }
        }
        public fileholder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemview= LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.rec, parent, false);
            return new fileholder(itemview);
        }
        @Override
        public void onBindViewHolder(final fileholder holder, final int position) {
            holder.image.setImageBitmap(all_photos_list.get(position).bitmap);
            if(all_photos_list.get(position).is_sellected)holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.sellected_item));
            else holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.not_sellected_item));
            holder.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(marker!=null)marker.remove();
                    set_sellected_photo(position);
                    holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.sellected_item));
                    marker=googleMap.addMarker(new MarkerOptions()
                            .position(new LatLng(all_photos_list.get(position).location.getLatitude(),all_photos_list.get(position).location.getLongitude())));
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(),1000));
                    marker.setTitle(all_photos_list.get(position).date);
                    adap.notifyDataSetChanged();
                }
            });
        }
        @Override
        public int getItemCount() {
            return all_photos_list.size();
        }

        @Override
        public void onViewAttachedToWindow(fileholder holder) {
            super.onViewAttachedToWindow(holder);
        }
    }
    private void set_sellected_photo(int position){
        int i=0;
        while(i<all_photos_list.size()){
            all_photos_list.get(i).is_sellected=false;
            i++;
        }
        all_photos_list.get(position).is_sellected=true;
    }
    private void load_all_the_pictures_on_folder(String Folder_path){
        if(Folder_path.contains(".thumbnails"))return;
        String[] files_list=new File(Folder_path).list();
        int i=0;
        while(i<files_list.length){
            if(new File(Folder_path+"/"+files_list[i]).isFile()){
                if(gettype(new File(Folder_path+"/"+files_list[i])).contains("image")){
                    Photo_info_and_exif_data_structure photo_info_and_exif_data_structure=new Photo_info_and_exif_data_structure(Folder_path+"/"+files_list[i]);
                    if(photo_info_and_exif_data_structure.has_location){
                        all_photos_list.add(photo_info_and_exif_data_structure);
                    }
                }
            }else{
                load_all_the_pictures_on_folder(Folder_path+"/"+files_list[i]);
            }
            i++;
        }
    }
    public static String gettype(File file){
        String type= MimeTypeMap.getSingleton().getMimeTypeFromExtension(get_ext(file.getName()));
        if(type==null)return " ";
        else return type;
    }
    public static String get_ext(String s){
        byte[] b=s.getBytes();
        int l=s.getBytes().length-1;
        while(l>-1){
            if(b[l]=='.'){
                break;
            }
            l--;
        }
        return new String(b,l+1,b.length-l-1).toLowerCase();
    }
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    public void onMapReady(GoogleMap map) {
        map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        googleMap=map;
    }
    protected void onResume() {
        mapView.onResume();
        super.onResume();
    }
    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }
    @Override
    public void onLowMemory() {
        mapView.onLowMemory();
        super.onLowMemory();
    }
    @Override
    protected void onStop() {
        super.onStop();
        if(countDownTimer!=null){
            countDownTimer.cancel();
        }
        if(asyncTask!=null){
            asyncTask.cancel(true);
        }
    }
}
