package com.openlocate.android.core;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;

import java.util.List;

final class OpenLocateHelper implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = OpenLocateHelper.class.getSimpleName();
    private static final String LOCATION_DISPATCH_TAG = OpenLocate.class.getCanonicalName() + ".location_dispatch_task";

    private final Context context;

    private OpenLocate.Configuration configuration;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private GcmNetworkManager mNetworkManager;

    public OpenLocateHelper(Context context, OpenLocate.Configuration configuration) {
        this.context = context;
        this.configuration = configuration;

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .build();

        mNetworkManager = GcmNetworkManager.getInstance(context);
    }

    public void startTracking() {
        if (mGoogleApiClient.isConnected() == false && mGoogleApiClient.isConnecting() == false) {
            mGoogleApiClient.connect();
        }
    }

    public void stopTracking() {
        stopLocationCollection();
        mGoogleApiClient.disconnect();
    }

    public void updateConfiguration(OpenLocate.Configuration configuration) {
        this.configuration = configuration;
        if (mGoogleApiClient.isConnected()) {
            stopLocationCollection();
            startLocationCollection();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocationCollection();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "Connection suspended. Error code: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        final String text = "Exception while connecting to Google Play Services";
        Log.w(TAG, text + ": " + connectionResult.getErrorMessage());
    }

    private void startLocationCollection() {
        buildLocationRequest();
        requestLocationUpdates();
        scheduleDispatchLocationService();
    }

    private void stopLocationCollection() {
        removeLocationUpdates();
        unscheduleDispatchLocationService();
    }

    private void buildLocationRequest() {
        long locationUpdateInterval = configuration.getLocationUpdateInterval() * 1000;
        int locationAccuracy = configuration.getLocationAccuracy().getLocationRequestAccuracy();

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(locationUpdateInterval);
        mLocationRequest.setFastestInterval(locationUpdateInterval / 2);
        mLocationRequest.setMaxWaitTime(Math.max(locationUpdateInterval * 2, locationUpdateInterval / 3));
        mLocationRequest.setPriority(locationAccuracy);
    }

    private void requestLocationUpdates() {
        try {
            Log.i(TAG, "Starting OL Updates");
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                    mLocationRequest, getLocationUpdatePendingIntent());
        } catch (SecurityException e) {
            Log.e(TAG, "Could not start OL Updates");
        }
    }

    private void removeLocationUpdates() {
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, getLocationUpdatePendingIntent());
        }
    }

    private PendingIntent getLocationUpdatePendingIntent() {
        Intent intent = new Intent(context, LocationUpdatesBroadcastReceiver.class);
        intent.setAction(LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void scheduleDispatchLocationService() {
        List<OpenLocate.Endpoint> endpoints = configuration.getEndpoints();
        if (endpoints == null || mNetworkManager == null) {
            return;
        }

        Bundle bundle = new Bundle();
        try {
            bundle.putString(Constants.ENDPOINTS_KEY, OpenLocate.Endpoint.toJson(endpoints));
        } catch (JSONException e) {
            e.printStackTrace();
            stopTracking();
        }

        PeriodicTask task = new PeriodicTask.Builder()
                .setExtras(bundle)
                .setService(DispatchLocationService.class)
                .setPeriod(configuration.getTransmissionInterval())
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setRequiresCharging(false)
                .setPersisted(true)
                .setUpdateCurrent(false)
                .setTag(LOCATION_DISPATCH_TAG)
                .build();

        try {
            mNetworkManager.schedule(task);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Google Play Services is not up to date.");
            stopTracking();
        }
    }

    private void unscheduleDispatchLocationService() {
        if (mNetworkManager != null) {
            mNetworkManager.cancelAllTasks(DispatchLocationService.class);
        }
    }

}
