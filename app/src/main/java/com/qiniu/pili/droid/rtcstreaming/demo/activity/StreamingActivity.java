package com.qiniu.pili.droid.rtcstreaming.demo.activity;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.qiniu.pili.droid.rtcstreaming.demo.R;

/**
 * <p> Created by 宋华 on 2017/8/20.
 */
public class StreamingActivity extends FragmentActivity {
    private FrameLayout mLayoutContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streaming);
        mLayoutContent = (FrameLayout) findViewById(R.id.layout_content);
        StreamingFragment streamingFragment = StreamingFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.layout_content, streamingFragment).commit();
        streamingFragment.setScreenListener(new StreamingFragment.ScreenListener() {
            @Override
            public void toCap() {
                mLayoutContent.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 607));
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }

            @Override
            public void toFull() {
                mLayoutContent.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        });
    }
}
