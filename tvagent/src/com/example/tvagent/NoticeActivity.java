package com.example.tvagent;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;
public class NoticeActivity extends Activity {
    private boolean end;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String kind = getIntent().getStringExtra("kind");
        if (!"warn".equals(kind) && !"end".equals(kind)) { finish(); return; }
        end = "end".equals(kind);
        TextView text = new TextView(this); text.setText(end ? "電視時間已到" : "電視時間剩 5 分鐘");
        text.setTextSize(36); text.setGravity(Gravity.CENTER); setContentView(text);
        new Handler().postDelayed(new Runnable() { public void run() { finish(); } }, end ? 12000 : 5000);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!end) { finish(); return true; }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(event);
    }
}
