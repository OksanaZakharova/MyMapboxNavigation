package com.example.oksanazakharova.mymapboxnavigation;

import android.Manifest;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class RouteActivity extends FragmentActivity{

    private RouteMapboxFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 1);
        }

        if (findViewById(R.id.mapboxFragment) != null) {
            if (savedInstanceState != null) {
                Fragment mFragment = getFragmentManager().findFragmentById(R.id.mapboxFragment);
                fragment = (RouteMapboxFragment) mFragment;
                return;
            }

            fragment = new RouteMapboxFragment();
            fragment.setArguments(getIntent().getExtras());

            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.mapboxFragment, fragment)
                    .addToBackStack(fragment.getTag())
                    .commit();
        }
    }
}

