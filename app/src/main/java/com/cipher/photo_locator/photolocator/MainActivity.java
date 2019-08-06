package com.cipher.photo_locator.photolocator;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
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
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private List<String> list=new ArrayList();
    private GoogleMap googleMap;
    private boolean program_exited=false,is_in_edit_mode=false,app_is_still_looking_for_images_with_geo_tags=true;
    private MapView mapView;
    private CountDownTimer countDownTimer;
    private RecyclerView.LayoutManager linearLayoutManager;
    private RecyclerView recyclerView;
    private Marker marker;
    private List<Bitmap> bitmaps=new ArrayList<>();
    private List<Photo_info_and_exif_data_structure> all_photos_list=new ArrayList<>();
    private fileadapter adap;
    private ProgressBar progressBar;
    private ImageView edit_location_button_imageview;
    private Thread background_images_loader_thread;
    private Button save_new_location;
    private int selected_image_offset=-1;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST=0;
    private static int Count_of_images_to_be_loaded_for_future_use=40;
    private Bundle savedInstanceState;
    private View permission_warning;

    @TargetApi(23)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.INTERNET"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }
    @TargetApi(23)
    private void handle_permission(){
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?

            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                //it was just denied, let the user decide
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST);
                //it was denied permanently
            }
        } else {
            init_activity();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.savedInstanceState=savedInstanceState;
        setContentView(R.layout.activity_main);
        permission_warning=findViewById(R.id.permission_warrning);
        findViewById(R.id.ask_permissions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handle_permission();
            }
        });
        if(Build.VERSION.SDK_INT>22)handle_permission();

        mapView=(MapView)findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

/*        The app does very limited actions which are dependent on read/write access to storage permission;
         So we continue the rest of the app after the permissions have been grated by user;*/
    }
    public void init_activity(){
        //we have permission now
        permission_warning.setVisibility(View.GONE);
        linearLayoutManager=new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false);
        progressBar=(ProgressBar)findViewById(R.id.progressBar2);
        recyclerView=(RecyclerView)findViewById(R.id.images_list);
        recyclerView.setLayoutManager(linearLayoutManager);
        save_new_location=(Button)findViewById(R.id.save_new_location);
        edit_location_button_imageview=(ImageView)findViewById(R.id.edit_location_imageview);

        adap=new fileadapter();
        recyclerView.setAdapter(adap);
        try{
            MapsInitializer.initialize(getApplicationContext());
        }catch (Exception e){
            Toast.makeText(getApplicationContext(),e.toString(),Toast.LENGTH_LONG).show();
        }
        save_new_location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                is_in_edit_mode=false;
                all_photos_list.get(selected_image_offset).set_new_location(marker.getPosition());
                Toast.makeText(getApplicationContext(),"new location successfully saved",Toast.LENGTH_LONG).show();
                recyclerView.setVisibility(View.VISIBLE);
                edit_location_button_imageview.setVisibility(View.VISIBLE);
                if(app_is_still_looking_for_images_with_geo_tags)progressBar.setVisibility(View.VISIBLE);
                save_new_location.setVisibility(View.GONE);
                adap.notifyDataSetChanged();
            }
        });

        background_images_loader_thread =new Thread(new Runnable() {
            @Override
            public void run() {
                load_all_the_pictures_on_folder("/sdcard/");
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        if(all_photos_list.size()==0){
                            Toast.makeText(getApplicationContext(),"no photos with geo tags found",Toast.LENGTH_LONG).show();
                        }
                        adap.notifyDataSetChanged();
                        app_is_still_looking_for_images_with_geo_tags=false;
                    }
                });
//                countDownTimer.cancel();
            }
        });
        background_images_loader_thread.start();
        countDownTimer=new CountDownTimer(Long.MAX_VALUE,1200) {
            @Override
            public void onTick(long millisUntilFinished) {
                adap.notifyDataSetChanged();
            }
            @Override
            public void onFinish() {
                adap.notifyDataSetChanged();
                countDownTimer.start();
            }
        }.start();
        adap.notifyDataSetChanged();
        edit_location_button_imageview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input_location();
                edit_location_button_imageview.setVisibility(View.GONE);
            }
        });
    }
    public class fileadapter extends RecyclerView.Adapter<fileadapter.fileholder> {
        private int last_bound_view_position;
        private class stoppable_runnable implements Runnable {
            public boolean stop=false;
            public void stop_runnable(){
                this.stop=true;
            }
            @Override
            public void run() {

            }
        }
        public stoppable_runnable future_photos_loading_runnable;
        public fileadapter(){

        }

        public class fileholder extends RecyclerView.ViewHolder{
            public ImageView image;
            public ProgressBar image_loading_progress_bar;
            public fileholder(View view) {
                super(view);
                image_loading_progress_bar=(ProgressBar)view.findViewById(R.id.image_loading_progressbar);
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
            adap.last_bound_view_position=position;

            if(adap.future_photos_loading_runnable==null){
                this.future_photos_loading_runnable=new stoppable_runnable() {
                    @Override
                    public void run() {

                        //load some of the next and previous images in memory; so we don't
                        //have to wait for them to load and
                        // null the rest of photo references so memory is less wasted;
                        int i,temp_position=-1;
                        stoppable_runnable bitmap_nulling_runnable=new stoppable_runnable(){
                            @Override
                            public void run() {
                                int i=0,temp=adap.last_bound_view_position;
                                while(!this.stop&&!program_exited){
                                    if(temp!=adap.last_bound_view_position){
                                        i=temp=adap.last_bound_view_position;
                                    }
                                    if(i>=0&&i<all_photos_list.size()){
                                        if((i<temp-MainActivity.Count_of_images_to_be_loaded_for_future_use)||
                                                (i>temp+MainActivity.Count_of_images_to_be_loaded_for_future_use)){
                                            if(all_photos_list.get(i)!=null)
                                                all_photos_list.get(i).bitmap=null;
                                        }
                                    }
                                    i++;
                                    if(i>=all_photos_list.size())i=0;
                                }
                            }
                        };
                        new Thread(bitmap_nulling_runnable).start();
                        while(!program_exited){
                            temp_position=last_bound_view_position;
                            i=0;
                            while(i<=MainActivity.Count_of_images_to_be_loaded_for_future_use&&!program_exited&&temp_position==adap.last_bound_view_position){
                                if(i+temp_position<all_photos_list.size()){
                                    if(all_photos_list.get(temp_position+i).bitmap==null)
                                        all_photos_list.get(temp_position+i).load_image_bitmap();
                                }
                                if(-i+temp_position>=0){
                                    if(all_photos_list.get(temp_position-i).bitmap==null)
                                        all_photos_list.get(temp_position-i).load_image_bitmap();
                                }
                                i++;
                            }
                        }
                        bitmap_nulling_runnable.stop_runnable();
                    }
                };
                new Thread(adap.future_photos_loading_runnable).start();
            }
            holder.image.setImageResource(0);
            holder.image_loading_progress_bar.setVisibility(View.VISIBLE);
            if(all_photos_list.get(position).bitmap!=null){
                holder.image_loading_progress_bar.setVisibility(View.GONE);
                holder.image.setImageBitmap(all_photos_list.get(position).bitmap);
            }

            if(all_photos_list.get(position).is_sellected)holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.sellected_item));
            else holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.not_sellected_item));
            holder.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v)
                {
                    select_image(holder,position);
                    edit_location_button_imageview.setVisibility(View.VISIBLE);
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
//            Toast.makeText(getApplicationContext(), String.valueOf(linearLayoutManager.getchi), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onViewDetachedFromWindow(fileholder holder) {
            super.onViewDetachedFromWindow(holder);
        }
        private void select_image(fileholder holder,int position){
            selected_image_offset=position;
            if(marker!=null)marker.remove();
            set_sellected_photo(position);
            holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.sellected_item));
            marker=googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(all_photos_list.get(position).location.getLatitude(),all_photos_list.get(position).location.getLongitude())));
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(),1000));
            marker.setTitle(all_photos_list.get(position).date);
            adap.notifyDataSetChanged();
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
    private void load_all_the_pictures_on_folder(String Folder_path){//ran in another non ui thread so make it stoppable
        if(Folder_path.contains(".thumbnails")||Folder_path.contains("cache"))return;
        String[] files_list=new File(Folder_path).list();
        int i=0;
        while(i<files_list.length&&!program_exited){
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
    public void input_location(){
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        save_new_location.setVisibility(View.VISIBLE);
        is_in_edit_mode=true;
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
            if(b[l]=='.') {
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
        googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if(is_in_edit_mode){
                    marker.setPosition(latLng);
                }
            }
        });
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
        program_exited=true;
        if(countDownTimer!=null){
            countDownTimer.cancel();
        }
    }
    @Override
    public void onLowMemory() {
        mapView.onLowMemory();
        super.onLowMemory();
    }
    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST){
            if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                init_activity();
            }else{
                //show an explanation and have user decide;
                if(permission_warning.getVisibility()==View.GONE)permission_warning.setVisibility(View.VISIBLE);
            }
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            try{
                if(!is_in_edit_mode)return super.onKeyDown(keyCode, event);
                is_in_edit_mode=false;
                edit_location_button_imageview.setVisibility(View.VISIBLE);
                marker.remove();
                marker=googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(all_photos_list.get(selected_image_offset).location.getLatitude(),all_photos_list.get(selected_image_offset).location.getLongitude())));
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(),1000));
                recyclerView.setVisibility(View.VISIBLE);
                if(app_is_still_looking_for_images_with_geo_tags)progressBar.setVisibility(View.VISIBLE);
                save_new_location.setVisibility(View.GONE);
                adap.notifyDataSetChanged();
                return false;
            }
            catch(IllegalStateException e){
                e.printStackTrace();
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
