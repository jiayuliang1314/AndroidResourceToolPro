package com.kite.testplugin;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.better_better_better_better_activity_main);
        findViewById(R.id.abc).setVisibility(View.VISIBLE);
    }
}