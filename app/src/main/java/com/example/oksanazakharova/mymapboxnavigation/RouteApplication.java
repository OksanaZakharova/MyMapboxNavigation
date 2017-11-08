package com.example.oksanazakharova.mymapboxnavigation;

import android.app.Application;
import android.content.Context;

import com.mapbox.mapboxsdk.Mapbox;

/**
 * Created by Oksana Zakharova on 08.11.2017.
 */

public class RouteApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Mapbox Access token
        Mapbox.getInstance(getApplicationContext(), "pk.eyJ1IjoieGVuYXpha2hhcm92YSIsImEiOiJjajlrMHhudjUzdm92MnhxeWkxM2k4ZnpmIn0.XakFINzYCcAcTggAkqclUQ");
    }
}
