package wwu.edu.csci412.cp2;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import wwu.edu.csci412.cp2.Retrofit.IMyService;
import wwu.edu.csci412.cp2.Retrofit.RetrofitClient;

/* MapActivity is part of shiblitz's MVC controller. It allows users to create dungeon spawn seeds
 * based off of a number of important variables.
 *
 * 	Temperature: 	The temperature gathered informs the ratio of enemy types from 
 * 			point dense at low temperature to point thin on high temperatures.
 * 			point density refers to the composition of levels. Point dense means
 * 			expensive, powerful units. Point light generations are full of slimes.	
 * 		
 * 	Pressure:	Pressure dictates the size of the room. High pressure, small maps. Low
 * 			pressure, large map. Going higher gets you a longer, more intense 
 * 			generation. Philosophy here is to incentivize harder seeds as
 * 			"more game".:	
 *
 * 	Light:		Light informs the direct difficulty and reward of the map. I'm not sure about
 * 			This one, as it motivates people to be adventuring during low light levels
 * 			and I don't want people to be hurt.i	
 *
 * 	Location:	The location of the player does not usually matter, but since this game	relies
 * 			on the GPS, it's most efficient to just check if the player is within x radius
 * 			of a given peak. Additionally, this lets us web scrape for locations of peaks and
 * 			automatically create them, were we to scale.
 *
 *
 * The class implements both SensorEventListener and LocationListener, signifying that we will be
 * looking at all of the aforementioned variables.
 *
 * Class is currently messy.
 * TODO: Clean and refactor the codebase.
 *
 * FIXME: Apparent bug.
 */
public class MapActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

	
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private static final String tag = "Map Activity";

    // Map variables
    private LocationManager lm;
    String provider;
    private MapView map;

    // Sensor variables
    private SensorManager sensorManager;
    private final ThreadLocal<Sensor> light = new ThreadLocal<>();
    private final ThreadLocal<Sensor> pressure = new ThreadLocal<>();
    private final ThreadLocal<Sensor> temperature = new ThreadLocal<>();

    // Local computation variables
    private double player_latitude;
    private double player_longitude;
    private float lightVal;
    private float pressureVal;
    private float tempVal;

    CompositeDisposable compositeDisposable = new CompositeDisposable();
    IMyService iMyService;
    Gson gson;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Log.d(tag, "Entering onCreate");

        gson = new Gson();

        //Init Services
        Retrofit retrofitClient = RetrofitClient.getInstance();
        iMyService = retrofitClient.create(IMyService.class);


        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        provider = lm.getBestProvider(new Criteria(), false);

        setContentView(R.layout.activity_mapactivity);
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        if (!checkLocationPermission()) {
            return;
        }

        Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);


        Log.d(tag, "acquiring coords");
        if (location == null) {
            return;
        }

        player_longitude= location.getLongitude();
        player_latitude = location.getLatitude();

        Log.d(tag, "lat=" + player_latitude + "lon=" + player_longitude);

        GeoPoint p = new GeoPoint(player_latitude, player_longitude);

        map.getController().animateTo(p);
        map.getController().setZoom(10.0);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            Log.d(tag, "Registering Sensors");
            light.set(sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT));
            pressure.set(sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE));
            temperature.set(sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE));
        } else {
            Log.d(tag, "Failed to acquire Sensor Manager");
            System.exit(1);
        }
    }



    //Example from git
    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Location Request")
                        .setMessage("Location Request")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MapActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        //Request location updates:
                        if (lm == null || provider ==null) {
                            Log.d(tag, "Null");
                        }
                        lm.requestLocationUpdates(provider, 400, 1, this);
                    }
                } else {
                    goback();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            lm.requestLocationUpdates(provider, 400, 1, this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            lm.removeUpdates(this);
        }
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(tag, "Sensor Event");
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            lightVal = event.values[0];
            Log.d(tag, "light: " + lightVal);
        }
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pressureVal = event.values[0];
            Log.d(tag, "pressure: " + pressureVal);
        }
        if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            tempVal = event.values[0];
            Log.d(tag, "temperature: " + tempVal);
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(tag, "onAccuracyChanged: " + sensor + " accuracy: " + accuracy);
    }

    @Override
    public void onLocationChanged(Location location) {
        player_longitude = location.getLongitude();
        player_latitude = location.getLatitude();
    }
    //TODO
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    public void makeSeed(View view) {
        Log.d(tag, "Light= "+ lightVal + "pressure= "+ pressureVal + "temp= "+ tempVal);

        for (Peak peak : MainActivity.peaks) {
            if (peak.inRange(player_latitude, player_longitude)) {
                MainActivity.seeds.add(peak.getSeed());
                registerSeed(peak.getSeed().getLight(), peak.getSeed().getPressure(), peak.getSeed().getTemperature());
                return;
            }
        }
        MainActivity.seeds.add(new Seed(lightVal, pressureVal, tempVal));
        registerSeed(lightVal, pressureVal, tempVal);
        for (Seed seed : MainActivity.seeds){
            Log.d(tag, "Seed found");
        }
    }
    private void registerSeed(float light, float pressure, float temp) {
        //Turns primitive into json object
        JsonObject userModify = new JsonObject();

        User user = LoginActivity.user;

        JsonObject seed = new JsonObject();
        seed.addProperty("light", light);
        seed.addProperty("pressure", pressure);
        seed.addProperty("temp", temp);

        userModify.addProperty("email", user.getEmail());
        userModify.add("seeds", seed);

        Log.d("json", userModify.toString());

        compositeDisposable.add(iMyService.modifyUser(userModify)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<String>() {
                                   @Override
                                   public void onNext(String res) {
                                   }
                                   @Override
                                   public void onError(Throwable e) {

                                       Toast.makeText(MapActivity.this, ""+e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                                   }
                                   @Override
                                   public void onComplete() {
                                       Toast.makeText(MapActivity.this, "Add seed!", Toast.LENGTH_SHORT).show();

                                   }
                               }
                ));

        compositeDisposable.add(iMyService.getInfo(user.getEmail())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<String>() {
                    @Override
                    public void onNext(String res) {
                        Toast.makeText(MapActivity.this, res, Toast.LENGTH_SHORT);

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                    }
                }
                ));


    }

    //Go to Main Activity
    public void goBack(View v){
        Intent intent = new Intent(this, MenuActivity.class);
        startActivity(intent);
        final MediaPlayer mp = MediaPlayer.create(this, R.raw.click);
        mp.setVolume(1.0f, 1.0f);
        mp.start();
        this.overridePendingTransition(R.anim.godown,
                R.anim.godown2);

    }

    public void goback() {
        this.finish();
    }
}


