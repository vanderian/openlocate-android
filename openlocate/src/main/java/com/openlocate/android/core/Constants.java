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

final class Constants {

    // Default Location constants
    static final long DEFAULT_LOCATION_INTERVAL_SEC = 5 * 60;
    static final long DEFAULT_FAST_LOCATION_INTERVAL_SEC = 1 * 60;
    static final long DEFAULT_TRANSMISSION_INTERVAL_SEC = 3 * 60 * 60;
    static final long SERVICE_CHECK_INTERVAL_MSEC = 5 * 60 * 1000;
    static final LocationAccuracy DEFAULT_LOCATION_ACCURACY = LocationAccuracy.MEDIUM;

    // Location service intent keys
    static final String ENDPOINTS_KEY = "endpoints";
    static final String SERVICE_STATUS = "service_status";
    static final String TRACKING_STATUS = "tracking_status";

    static final String LOCATION_ACCURACY_KEY = "location_accuracy";
    static final String LOCATION_INTERVAL_KEY = "location_interval";
    static final String TRANSMISSION_INTERVAL_KEY = "transmission_interval";
    static final String INTENT_CONFIGURATION = "intent_configuration";

    static final String ADVERTISING_ID_KEY = "advertising_id";
    static final String LIMITED_AD_TRACKING_ENABLED_KEY = "limited_ad_tracking_enabled";

    static final int DEFAULT_PORT = -1;

    // Local broadcast manager event names
    static final String LOCATION_INTERVAL_CHANGED = Constants.class.getCanonicalName() + ".LocationIntervalChanged";
    static final String LOCATION_ACCURACY_CHANGED = Constants.class.getCanonicalName() + ".LocationAccuracyChanged";
    static final String TRANSMISSION_INTERVAL_CHANGED = Constants.class.getCanonicalName() + ".TransmissionIntervalChanged";

    public static final String OPENLOCATE = "com.openlocate.android";
    public static final String IS_SERVICE_STARTED = "is_service_running";
}
