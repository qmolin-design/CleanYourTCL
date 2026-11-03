package com.example.tkey;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

// T 鍵 → 開 TARGET。改 SmartTube 時換套件名（manifest 的 queries 也要換）。
public class Main extends Activity {
    static final String TARGET = "com.google.android.youtube.tv";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(TARGET);
        if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); // YouTube 被停用時什麼都不做
        finish();
    }
}
