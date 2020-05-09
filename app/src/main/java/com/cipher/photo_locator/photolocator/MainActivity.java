package com.cipher.photo_locator.photolocator;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap googleMap;
    private photo_info currently_selected_photo_exif_data;
    private boolean program_exited=false, is_input_location_mode =false,is_in_view_image_on_maps_mode=false,is_in_photo_selection_mode=true,app_is_still_looking_for_images_with_geo_tags=true;
    private MapView mapView;
    private CountDownTimer countDownTimer;
    private RecyclerView.LayoutManager linearLayoutManager;
    private RecyclerView recyclerView;
    private Marker marker;
    private List<Bitmap> bitmaps=new ArrayList<>();
    private List<photo_info> all_photos_list=new ArrayList<>();
    private Auto_finder_adapter adap;
    private ProgressBar progressBar;
    private LinearLayout file_manager_linear_container;
    private ImageView edit_location_button_imageview;
    private Thread background_images_loader_thread;
    private Button save_new_location;
    private int selected_image_offset=-1;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST=0;
    private static int Count_of_images_to_be_loaded_for_future_use=40;
    private Bundle savedInstanceState;
    private View permission_warning;
    private easyFileManager easyFileManager_instance;
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
        setContentView(R.layout.main);
        permission_warning=findViewById(R.id.permission_warrning);
        findViewById(R.id.ask_permissions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handle_permission();
            }
        });
        mapView=(MapView)findViewById(R.id.map_disp);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        if(Build.VERSION.SDK_INT>22)handle_permission();
        else init_activity();
/*        The app does very limited actions which are dependent on read/write access to storage permission;
         So we continue the rest of the app after the permissions have been grated by user;*/
    }
    public void init_activity(){
        //we have permission now
        file_manager_linear_container =findViewById(R.id.Filemanager_lin_cont);
        permission_warning.setVisibility(View.GONE);
        linearLayoutManager=new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false);
        progressBar=(ProgressBar)findViewById(R.id.progressBar2);
        recyclerView=(RecyclerView)findViewById(R.id.list_recy_view);
        recyclerView.setLayoutManager(linearLayoutManager);
        save_new_location=(Button)findViewById(R.id.update_location);
        edit_location_button_imageview=(ImageView)findViewById(R.id.edit_loc);

        adap=new Auto_finder_adapter();
        recyclerView.setAdapter(adap);
        try{
            MapsInitializer.initialize(getApplicationContext());
        }catch (Exception e){
            Toast.makeText(getApplicationContext(),e.toString(),Toast.LENGTH_LONG).show();
        }
        easyFileManager_instance =new easyFileManager(this, "/sdcard", false, easyFileManager.MODE_SINGLE_FILE_SELECTION, new File_item_evens_listener() {
            @Override
            public void File_item_onLongClickListen(File file) {
            }

            @Override
            public void File_item_onClickListen(File file) {
                currently_selected_photo_exif_data=new photo_info(file.getPath());
                if(currently_selected_photo_exif_data.has_location)
                    goto_image_view_on_map_mode();
                else Toast.makeText(getApplicationContext(),"This photo does not contain location data.",Toast.LENGTH_LONG).show();
            }
        });
        easyFileManager_instance.allowed_file_extentions_list.add("jpg");
        easyFileManager_instance.allowed_file_extentions_list.add("tiff");
        easyFileManager_instance.allowed_file_extentions_list.add("jpeg");
        easyFileManager_instance.allowed_file_extentions_list.add("JPG");
        easyFileManager_instance.allowed_file_extentions_list.add("TIFF");
        easyFileManager_instance.allowed_file_extentions_list.add("JPEG");

        FragmentTransaction ft=getSupportFragmentManager().beginTransaction();
        ft.add(file_manager_linear_container.getId(), easyFileManager_instance);
        ft.commit();

        handle_major_ui_click_events();

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
    }

    public void handle_major_ui_click_events(){
        save_new_location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(googleMap==null)return;
                currently_selected_photo_exif_data.set_new_location(marker.getPosition());
                adap.update_dataset_item();
                Toast.makeText(getApplicationContext(),"new location successfully saved",Toast.LENGTH_LONG).show();
                goto_image_view_on_map_mode();
            }
        });
        edit_location_button_imageview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(googleMap==null)return;
                goto_input_location_mode();
            }
        });

    }
    public class Auto_finder_adapter extends RecyclerView.Adapter<Auto_finder_adapter.fileholder> {
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
        public void update_dataset_item(){
            int i=0;
            while(i<all_photos_list.size()){
                if(all_photos_list.get(i).file_path.equals(currently_selected_photo_exif_data.file_path)){
                    all_photos_list.remove(i);
                    all_photos_list.add(i,currently_selected_photo_exif_data);
                }
                i++;
            }
        }
        public stoppable_runnable future_photos_loading_runnable;
        public Auto_finder_adapter(){

        }

        public class fileholder extends RecyclerView.ViewHolder{
            public ImageView image;
            public ProgressBar image_loading_progress_bar;
            public fileholder(View view) {
                super(view);
                image_loading_progress_bar=(ProgressBar)view.findViewById(R.id.img_loading_probar);
                image=(ImageView)view.findViewById(R.id.img_disp);
            }
        }
        public fileholder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemview= LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerlist_item, parent, false);
            return new fileholder(itemview);
        }

        @Override
        public void onBindViewHolder(final fileholder holder, final int position) {
            if(linearLayoutManager.getChildAt(0)!=null)adap.last_bound_view_position=(recyclerView.getChildAdapterPosition(linearLayoutManager.getChildAt(0))+recyclerView.getChildAdapterPosition(linearLayoutManager.getChildAt(linearLayoutManager.getChildCount()-1)))/2;

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
                                    try {
                                        if (temp != adap.last_bound_view_position) {
                                            i = temp = adap.last_bound_view_position;
                                        }
                                        if (i >= 0 && i < all_photos_list.size()) {
                                            if ((i < temp - MainActivity.Count_of_images_to_be_loaded_for_future_use) ||
                                                    (i > temp + MainActivity.Count_of_images_to_be_loaded_for_future_use)) {
                                                if (all_photos_list.get(i) != null)
                                                    all_photos_list.get(i).bitmap = null;
                                            }
                                        }
                                        i++;
                                        if (i >= all_photos_list.size()) i = 0;
                                    }catch (IndexOutOfBoundsException e){

                                    }catch (NullPointerException e){}
                                }
                            }
                        };
                        new Thread(bitmap_nulling_runnable).start();
                        while(!program_exited){
                            temp_position=last_bound_view_position;
                            i=0;
                            try {
                                while (i <= MainActivity.Count_of_images_to_be_loaded_for_future_use && !program_exited && temp_position == adap.last_bound_view_position) {
                                    if (i + temp_position < all_photos_list.size()) {
                                        if (all_photos_list.get(temp_position + i) != null)
                                            if (all_photos_list.get(temp_position + i).bitmap == null)
                                                all_photos_list.get(temp_position + i).load_Image_bitmap();
                                    }
                                    if (-i + temp_position >= 0) {
                                        if (all_photos_list.get(temp_position - i) != null)
                                            if (all_photos_list.get(temp_position - i).bitmap == null)
                                                all_photos_list.get(temp_position - i).load_Image_bitmap();
                                    }
                                    i++;
                                }
                            }catch (IndexOutOfBoundsException e){
//
                            }catch (NullPointerException e){}
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

//            if(all_photos_list.get(position).is_sellected)holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.sellected_item));
//            else holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.not_sellected_item));
            holder.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v)
                {
//                    select_image(holder,position);
                    currently_selected_photo_exif_data=all_photos_list.get(position);
                    goto_image_view_on_map_mode();
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
//        private void select_image(fileholder holder,int position){
//            selected_image_offset=position;
//            if(marker!=null)marker.remove();
//            set_sellected_photo(position);
//            holder.image.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.sellected_item));
//            marker=googleMap.addMarker(new MarkerOptions()
//                    .position(new LatLng(all_photos_list.get(position).location.getLatitude(),all_photos_list.get(position).location.getLongitude())));
//            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(),1000));
//            marker.setTitle(all_photos_list.get(position).date);
//            adap.notifyDataSetChanged();
//        }
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
                    photo_info photo_info_and_exif_data_structure=new photo_info(Folder_path+"/"+files_list[i]);
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
    public void goto_image_selection_mode(){
        is_in_photo_selection_mode=true;
        is_in_view_image_on_maps_mode=false;
        is_input_location_mode=false;
        file_manager_linear_container.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.VISIBLE);
        save_new_location.setVisibility(View.GONE);
        edit_location_button_imageview.setVisibility(View.GONE);
        if(app_is_still_looking_for_images_with_geo_tags)progressBar.setVisibility(View.VISIBLE);
        mapView.setVisibility(View.GONE);
    }
    public void goto_image_view_on_map_mode(){
        is_in_photo_selection_mode=false;
        is_in_view_image_on_maps_mode=true;
        is_input_location_mode=false;
        file_manager_linear_container.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        mapView.setVisibility(View.VISIBLE);
        save_new_location.setVisibility(View.GONE);
        if(googleMap!=null)edit_location_button_imageview.setVisibility(View.VISIBLE);
        if(marker!=null)marker.remove();
        if(googleMap!=null) {
            marker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(currently_selected_photo_exif_data.location.getLatitude(), currently_selected_photo_exif_data.location.getLongitude())));
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 1000));
            marker.setTitle(currently_selected_photo_exif_data.date);
        }
    }
    public void goto_input_location_mode(){
        is_in_photo_selection_mode=false;
        is_in_view_image_on_maps_mode=false;
        is_input_location_mode=true;
        file_manager_linear_container.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        mapView.setVisibility(View.VISIBLE);
        edit_location_button_imageview.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        save_new_location.setVisibility(View.VISIBLE);
        is_input_location_mode =true;
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
                if(is_input_location_mode){
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
                if(is_in_photo_selection_mode){
                    if(easyFileManager_instance.current_directory_path.equals("/sdcard/"))
                        return super.onKeyDown(keyCode, event);
                    easyFileManager_instance.nav_to_previous_directory();
                }
                if(is_in_view_image_on_maps_mode){
                    goto_image_selection_mode();
                    return false;
                }
                if(is_input_location_mode){
                    goto_image_view_on_map_mode();
                    return false;
                }
                return false;
            }
            catch(IllegalStateException e){
                e.printStackTrace();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

}
