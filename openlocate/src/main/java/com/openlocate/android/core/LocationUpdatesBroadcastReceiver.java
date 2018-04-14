/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.openlocate.android.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteFullException;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.location.LocationResult;

import java.util.List;

public class LocationUpdatesBroadcastReceiver extends BroadcastReceiver {

    private final static String TAG = LocationUpdatesBroadcastReceiver.class.getSimpleName();

    static final String ACTION_PROCESS_UPDATES = ".PROCESS_UPDATES";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (action.contains(ACTION_PROCESS_UPDATES)) {

                LocationResult result = LocationResult.extractResult(intent);
                if (result == null) {
                    return;
                }

                List<Location> locations = result.getLocations();
                if (locations == null || locations.isEmpty() == true) {
                    return;
                }

                try {
                    OpenLocate.Configuration configuration = OpenLocate.getInstance().getConfiguration();
                    AdvertisingIdClient.Info advertisingIdInfo = OpenLocate.getInstance().getAdvertisingIdInfo();

                    if (configuration != null && advertisingIdInfo != null) {
                        processLocations(locations, context, configuration, advertisingIdInfo);
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "Could not getInstance() of OL.");
                }
            }
        }
    }

    private void processLocations(List<Location> locations, Context context,
                                  OpenLocate.Configuration configuration,
                                  AdvertisingIdClient.Info advertisingIdInfo) {

        LocationDatabase locationsDatabase = new LocationDatabase(DatabaseHelper.getInstance(context));
        try {
            for (Location location : locations) {

                Log.v(TAG, location.toString());

                OpenLocateLocation olLocation = OpenLocateLocation.from(
                        location,
                        advertisingIdInfo,
                        InformationFieldsFactory.collectInformationFields(context, configuration)
                );

                locationsDatabase.add(olLocation);
            }
        } catch (SQLiteFullException exception) {
            Log.w(TAG, "Database is full. Cannot add data.");
        } finally {
            locationsDatabase.close();
        }
    }
}
