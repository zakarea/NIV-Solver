package com.example.inspectiontestjava;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //case 1: putExtras(Intent)

        Intent nested = new Intent();
        Intent wrapper = new Intent();
        wrapper.putExtras(nested);
//        startActivity(wrapper);
        startService(wrapper);
        bindService(wrapper, null, Context.BIND_AUTO_CREATE);

//        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
//                FILL_REQUEST_CODE_HERE, wrapper,
//                /* flags */ PendingIntent.FLAG_IMMUTABLE);

        //case 2: putExtras(Bundle) -> Bundle.putParcelable(String, Intent);
        Intent wrapper2 = new Intent();
        Intent nested2 = new Intent();
        Bundle bundle= new Bundle();
        bundle.putParcelable("N", nested2);
        wrapper2.putExtras(bundle);
        startActivity(wrapper2);
        startService(wrapper2);
        bindService(wrapper2, null, Context.BIND_AUTO_CREATE);

    }
}