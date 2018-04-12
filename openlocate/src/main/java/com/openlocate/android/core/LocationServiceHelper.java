/*
 * Copyright (c) 2017 OpenLocate
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.openlocate.android.core;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;

import java.util.ArrayList;

final class LocationServiceHelper {

    private final static int FOREGROUND_SERVICE_TAG = 1001;

    private final static String TAG = LocationService.class.getSimpleName();
    private final static String LOCATION_DISPATCH_TAG = LocationService.class.getCanonicalName() + ".location_dispatch_task";

    private GoogleApiClient googleApiClient;
    private GcmNetworkManager networkManager;
    private AlarmManager alarmManager;

    private long locationRequestIntervalInSecs = Constants.DEFAULT_LOCATION_INTERVAL_SEC;
    private long transmissionIntervalInSecs = Constants.DEFAULT_TRANSMISSION_INTERVAL_SEC;
    private LocationAccuracy accuracy = Constants.DEFAULT_LOCATION_ACCURACY;

    private LocationDataSource locations;
    private LocationServiceHelper.LocationListener locationListener;

    private ArrayList<OpenLocate.Endpoint> endpoints;

    private AdvertisingIdClient.Info advertisingInfo = new AdvertisingIdClient.Info("", true);

    private Context context;
    private OpenLocate.Configuration configuration;

    private Intent restartServiceIntent;

    LocationServiceHelper(Context context) {
        this.context = context;
    }

    void onCreate() {
        locations = new LocationDatabase(DatabaseHelper.getInstance(context));
        networkManager = GcmNetworkManager.getInstance(context);
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        registerForLocalBroadcastEvents();
        setServiceStatusOnStart();

        Log.d(TAG, "Inside onCreate of Location service Helper");
    }

    private void setServiceStatusOnStart() {
        SharedPreferenceUtils.getInstance(context).setValue(Constants.SERVICE_STATUS, true);
    }

    private void setServiceStatusOnStop() {
        SharedPreferenceUtils.getInstance(context).setValue(Constants.SERVICE_STATUS, false);
    }

    void onDestroy() {
        unschedulePeriodicTasks();
        stopLocationUpdates();

        networkManager = null;
        locations = null;

        setServiceStatusOnStop();

        if (context.getClass().isInstance(LocationService.class)) {
            ((LocationService) context).stopForeground(true);
        }

        Log.d(TAG, "Inside onDestroy of Location service Helper");
    }

    boolean onStartCommand(Intent intent) {

        boolean success = setValues(intent);

        if (success == false) {
            unschedulePeriodicTasks();
            stopLocationUpdates();
            return false;
        }

        restartServiceIntent = intent;

        if (googleApiClient == null ||
                googleApiClient.isConnected() == false && googleApiClient.isConnecting() == false) {
            connectGoogleClient();
        }

        /* Starting the service as foreground service for Android Oreo.
         * If the service is not foreground, service will be killed when the app is killed.
         */
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground();
        }

        return true;
    }

    private BroadcastReceiver locationIntervalChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setLocationRequestIntervalInSecs(intent);
            resetLocationRequest();
        }
    };

    private BroadcastReceiver transmissionIntervalChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setTransmissionIntervalInSecs(intent);
            resetTransmissionInterval();
        }
    };

    private BroadcastReceiver locationAccuracyChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setLocationAccuracy(intent);
            resetLocationRequest();
        }
    };

    private void registerForLocalBroadcastEvents() {
        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(
                locationIntervalChangedReceiver,
                new IntentFilter(Constants.LOCATION_INTERVAL_CHANGED));

        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(
                transmissionIntervalChangedReceiver,
                new IntentFilter(Constants.TRANSMISSION_INTERVAL_CHANGED));

        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(
                locationAccuracyChangedReceiver,
                new IntentFilter(Constants.LOCATION_ACCURACY_CHANGED));
    }

    @SuppressWarnings("unchecked")
    private boolean setValues(Intent intent) {

        OpenLocate.Configuration configuration = intent.getExtras().getParcelable(Constants.INTENT_CONFIGURATION);
        ArrayList<OpenLocate.Endpoint> endpoints = intent.getParcelableArrayListExtra(Constants.ENDPOINTS_KEY);
        String adId = intent.getStringExtra(Constants.ADVERTISING_ID_KEY);

        if (configuration == null || endpoints == null || adId == null) {
            return false;
        }

        this.configuration = configuration;
        this.endpoints = endpoints;
        this.advertisingInfo = new AdvertisingIdClient.Info(adId,
                intent.getBooleanExtra(Constants.LIMITED_AD_TRACKING_ENABLED_KEY, false)
        );

        setLocationRequestIntervalInSecs(intent);
        setTransmissionIntervalInSecs(intent);
        setLocationAccuracy(intent);

        if (configuration == null || endpoints == null || advertisingInfo == null) {
            return false;
        }

        return true;
    }

    private void setLocationRequestIntervalInSecs(Intent intent) {
        locationRequestIntervalInSecs = intent.getLongExtra(Constants.LOCATION_INTERVAL_KEY, Constants.DEFAULT_LOCATION_INTERVAL_SEC);
    }

    private void setTransmissionIntervalInSecs(Intent intent) {
        transmissionIntervalInSecs = intent.getLongExtra(Constants.TRANSMISSION_INTERVAL_KEY, Constants.DEFAULT_TRANSMISSION_INTERVAL_SEC);
    }

    private void setLocationAccuracy(Intent intent) {
        LocationAccuracy accuracy = (LocationAccuracy) intent.getSerializableExtra(Constants.LOCATION_ACCURACY_KEY);
        if (accuracy != null) {
            this.accuracy = accuracy;
        }
    }

    private void connectGoogleClient() {
        Log.e(TAG, "Google Api Client: Connecting ");
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(new ConnectionCallbacks())
                    .addOnConnectionFailedListener(new ConnectionFailedListener())
                    .addApi(LocationServices.API)
                    .build();
        }

        if (!googleApiClient.isConnected()) {
            googleApiClient.connect();
        }
    }

    private LocationRequest getLocationRequest() {
        LocationRequest request = new LocationRequest();

        request.setPriority(accuracy.getLocationRequestAccuracy());
        request.setInterval(locationRequestIntervalInSecs * 1000);
        request.setFastestInterval(Constants.DEFAULT_FAST_LOCATION_INTERVAL_SEC * 1000);

        return request;
    }

    private void startLocationUpdates() {
        try {
            LocationRequest request = getLocationRequest();
            locationListener = new LocationListener();
            FusedLocationProviderApi fusedLocationApi = LocationServices.FusedLocationApi;
            if (fusedLocationApi != null) {
                fusedLocationApi.requestLocationUpdates(googleApiClient, request, locationListener);
                schedulePeriodicTasks();
            }
        } catch (SecurityException e) {
            locationListener = null;
            Log.e(TAG, e.getMessage());
        }
    }

    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "TASK REMOVED");

        unschedulePeriodicTasks();
    }

    private void stopLocationUpdates() {
        if (googleApiClient != null && googleApiClient.isConnected() && locationListener != null) {
            FusedLocationProviderApi fusedLocationApi = LocationServices.FusedLocationApi;
            if (fusedLocationApi != null) {
                fusedLocationApi.removeLocationUpdates(googleApiClient, locationListener);
                googleApiClient.disconnect();
            }
        }
        locationListener = null;
    }

    private void schedulePeriodicTasks() {
        scheduleDispatchLocationService();
    }

    private void scheduleDispatchLocationService() {

        if (endpoints == null) {
            return;
        }

        if (networkManager == null) {
            Log.w(TAG, "Network Manager is null");
            return;
        }

        Bundle bundle = new Bundle();
        try {
            bundle.putString(Constants.ENDPOINTS_KEY, OpenLocate.Endpoint.toJson(endpoints));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        PeriodicTask task = new PeriodicTask.Builder()
                .setExtras(bundle)
                .setService(DispatchLocationService.class)
                .setPeriod(transmissionIntervalInSecs)
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setRequiresCharging(false)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .setTag(LOCATION_DISPATCH_TAG)
                .build();

        try {
            networkManager.schedule(task);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Google Play Services is not up to date.");
            stopLocationUpdates();
        }
    }

    private void unschedulePeriodicTasks() {
        if (networkManager != null) {
            try {
                networkManager.cancelAllTasks(DispatchLocationService.class);
            } catch (IllegalArgumentException e) {
            }
        }
    }

    // Google Api Client Connection Callback
    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "Google Api Client Connection Connected. Starting Location updates");
            startLocationUpdates();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.e(TAG, "Google Api Client Connection Suspended : " + i);
        }
    }

    // Connection Failed Listener Class
    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e(TAG, "Google API Client failed to connect. Result = " + connectionResult.getErrorMessage());
        }
    }

    // OpenLocateLocation Listener class
    private class LocationListener implements com.google.android.gms.location.LocationListener {

        @Override
        public void onLocationChanged(Location location) {

            if (configuration == null || locations == null ||
                    context == null || advertisingInfo == null) {
                return;
            }

            Log.v(TAG, location.toString());

            try {
                locations.add(
                        OpenLocateLocation.from(
                                location,
                                advertisingInfo,
                                InformationFieldsFactory.collectInformationFields(context, configuration)
                        )
                );
            } catch (SQLiteFullException exception) {
                Log.w(TAG, "Database is full. Cannot add data.");
            }

            Log.v(TAG, "COUNT - " + locations.size());
        }
    }

    @RequiresApi(26)
    private void startForeground() {
        Notification notification = new Notification.Builder(context).build();
        ((LocationService) context).startForeground(FOREGROUND_SERVICE_TAG, notification);
    }

    private void resetLocationRequest() {
        stopLocationUpdates();
        startLocationUpdates();
    }

    private void resetTransmissionInterval() {
        unschedulePeriodicTasks();
        schedulePeriodicTasks();
    }

    long getLocationRequestIntervalInSecs() {
        return locationRequestIntervalInSecs;
    }

    long getTransmissionIntervalInSecs() {
        return transmissionIntervalInSecs;
    }

    LocationAccuracy getAccuracy() {
        return accuracy;
    }

    LocationDataSource getLocations() {
        return locations;
    }

    public void setEndpoints(ArrayList<OpenLocate.Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    AdvertisingIdClient.Info getAdvertisingInfo() {
        return advertisingInfo;
    }

    GoogleApiClient getGoogleApiClient() {
        return googleApiClient;
    }

    GcmNetworkManager getNetworkManager() {
        return networkManager;
    }

    LocationListener getLocationListener() {
        return locationListener;
    }
}
