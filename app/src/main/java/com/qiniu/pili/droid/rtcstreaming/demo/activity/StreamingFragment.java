package com.qiniu.pili.droid.rtcstreaming.demo.activity;

import android.app.ProgressDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONObjectRequestListener;
import com.qiniu.pili.droid.rtcstreaming.RTCMediaStreamingManager;
import com.qiniu.pili.droid.rtcstreaming.demo.R;
import com.qiniu.pili.droid.rtcstreaming.demo.core.StreamUtils;
import com.qiniu.pili.droid.streaming.AVCodecType;
import com.qiniu.pili.droid.streaming.CameraStreamingSetting;
import com.qiniu.pili.droid.streaming.StreamStatusCallback;
import com.qiniu.pili.droid.streaming.StreamingProfile;
import com.qiniu.pili.droid.streaming.StreamingSessionListener;
import com.qiniu.pili.droid.streaming.StreamingState;
import com.qiniu.pili.droid.streaming.StreamingStateChangedListener;
import com.qiniu.pili.droid.streaming.WatermarkSetting;
import com.qiniu.pili.droid.streaming.widget.AspectFrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.List;

/**
 * <p> Created by 宋华 on 2017/8/20.
 */
public class StreamingFragment extends Fragment {

    public static StreamingFragment newInstance() {
        Bundle args = new Bundle();
        StreamingFragment fragment = new StreamingFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private static final String TAG = "CapStreamingActivity";
    private static final int MESSAGE_ID_RECONNECTING = 0x01;
    private TextView mStatusTextView;
    private TextView mStatTextView;
    private Button mControlButton;
    private ImageView mOrientation;
    private Toast mToast = null;
    private ProgressDialog mProgressDialog;
    private RTCMediaStreamingManager mRTCStreamingManager;
    private StreamingProfile mStreamingProfile;
    private boolean mIsActivityPaused = true;
    private boolean mIsPublishStreamStarted = false;
    private boolean mIsInReadyState = false;
    private int mCurrentCamFacingIndex;
    private GLSurfaceView mCameraPreviewFrameView;
    private String mBitrateControl;
    private boolean mIsFullscreen;
    private CameraStreamingSetting cameraStreamingSetting;
    private TextView mTvPlayBack;
    private boolean isStopBrevity;
    private View mContent;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mContent = inflater.inflate(R.layout.fragment_test_streaming, container, false);
        RTCMediaStreamingManager.init(getActivity().getApplicationContext());

        AspectFrameLayout afl = (AspectFrameLayout) mContent.findViewById(R.id.cameraPreview_afl);
        afl.setShowMode(AspectFrameLayout.SHOW_MODE.FULL);
        //  mCameraPreviewFrameView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);
        mCameraPreviewFrameView = new CameraPreviewFrameView(getActivity());
        mCameraPreviewFrameView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        afl.addView(mCameraPreviewFrameView);


        boolean isSwCodec = true;
        mIsFullscreen = false;
        getActivity().setRequestedOrientation(mIsFullscreen ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        boolean isBeautyEnabled = true;
        boolean isWaterMarkEnabled = true;
        boolean isDebugModeEnabled = false;
        boolean isCustomSettingEnabled = false;
        mBitrateControl = "auto";
        mControlButton = (Button) mContent.findViewById(R.id.ControlButton);
        mControlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsPublishStreamStarted) {
                    startPublishStreaming();
                } else {
                    stopPublishStreaming();
                }
            }
        });
        mStatusTextView = (TextView) mContent.findViewById(R.id.StatusTextView);
        mStatTextView = (TextView) mContent.findViewById(R.id.StatTextView);
        mOrientation = (ImageView) mContent.findViewById(R.id.orientation);
        mContent.findViewById(R.id.btn_switch_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentCamFacingIndex = (mCurrentCamFacingIndex + 1) % CameraStreamingSetting.getNumberOfCameras();
                CameraStreamingSetting.CAMERA_FACING_ID facingId;
                if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK.ordinal()) {
                    facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
                } else if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT.ordinal()) {
                    facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
                } else {
                    facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
                }
                Log.i(TAG, "switchCamera:" + facingId);
                mRTCStreamingManager.switchCamera(facingId);
            }
        });
        mOrientation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsFullscreen = !mIsFullscreen;
                if (mIsPublishStreamStarted) {
                    stopPublishStreaming();
                    isStopBrevity = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mStreamingProfile.setEncodingOrientation(mIsFullscreen ? StreamingProfile.ENCODING_ORIENTATION.LAND : StreamingProfile.ENCODING_ORIENTATION.PORT);

                        if (mIsFullscreen) {
                            cameraStreamingSetting.setPreviewAdaptToEncodingSize(false);
                            mStreamingProfile.setPreferredVideoEncodingSize(640, 360, new Point(0, 0), 640, 360);
                            mScreenListener.toFull();
                        } else {
                            cameraStreamingSetting.setPreviewAdaptToEncodingSize(false);
                            mStreamingProfile.setPreferredVideoEncodingSize(360, 202, new Point(0, 216), 360, 640);
                            mScreenListener.toCap();
                        }
                        mRTCStreamingManager.setStreamingProfile(mStreamingProfile);
                        mRTCStreamingManager.notifyActivityOrientationChanged();
                        if (isStopBrevity) {
                            startPublishStreaming();
                            isStopBrevity = false;
                        }
                        mOrientation.setImageResource(mIsFullscreen ? R.mipmap.live_window : R.mipmap.live_fullscreen);
                    }
                }, 500);
            }
        });
        mTvPlayBack = (TextView) mContent.findViewById(R.id.playback);
        mTvPlayBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    createPlayback(3);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        CameraStreamingSetting.CAMERA_FACING_ID facingId = chooseCameraFacingId();
        mCurrentCamFacingIndex = facingId.ordinal();

        /**
         * Step 3: config camera settings
         */
        cameraStreamingSetting = new CameraStreamingSetting();
        cameraStreamingSetting.setCameraFacingId(facingId)
                .setContinuousFocusModeEnabled(true)
                .setRecordingHint(false)
                .setResetTouchFocusDelayInMs(3000)
                .setFocusMode(CameraStreamingSetting.FOCUS_MODE_CONTINUOUS_PICTURE)
                .setCameraPrvSizeLevel(CameraStreamingSetting.PREVIEW_SIZE_LEVEL.MEDIUM)
                .setCameraPrvSizeRatio(CameraStreamingSetting.PREVIEW_SIZE_RATIO.RATIO_16_9)
                .setPreviewAdaptToEncodingSize(false);


        cameraStreamingSetting.setBuiltInFaceBeautyEnabled(true); // Using sdk built in face beauty algorithm
        cameraStreamingSetting.setFaceBeautySetting(new CameraStreamingSetting.FaceBeautySetting(0.8f, 0.8f, 0.6f)); // sdk built in face beauty settings
        cameraStreamingSetting.setVideoFilter(CameraStreamingSetting.VIDEO_FILTER_TYPE.VIDEO_FILTER_BEAUTY); // set the beauty on/off

        AVCodecType codecType = isSwCodec ? AVCodecType.SW_VIDEO_WITH_SW_AUDIO_CODEC : AVCodecType.HW_VIDEO_YUV_AS_INPUT_WITH_HW_AUDIO_CODEC;
        mRTCStreamingManager = new RTCMediaStreamingManager(getActivity().getApplicationContext(), afl, mCameraPreviewFrameView, codecType);
        mRTCStreamingManager.setDebugLoggingEnabled(isDebugModeEnabled);
        mRTCStreamingManager.setStreamStatusCallback(mStreamStatusCallback);
        mRTCStreamingManager.setStreamingStateListener(mStreamingStateChangedListener);
        mRTCStreamingManager.setStreamingSessionListener(mStreamingSessionListener);

        mStreamingProfile = new StreamingProfile();
        mStreamingProfile.setVideoQuality(StreamingProfile.VIDEO_QUALITY_MEDIUM2)
                .setAudioQuality(StreamingProfile.AUDIO_QUALITY_MEDIUM1)
                .setEncoderRCMode(StreamingProfile.EncoderRCModes.BITRATE_PRIORITY)
                .setPreferredVideoEncodingSize(360, 202, new Point(0, 216), 360, 640)
                .setFpsControllerEnable(true)
                .setPictureStreamingResourceId(R.drawable.pause_publish)
                .setSendingBufferProfile(new StreamingProfile.SendingBufferProfile(0.2f, 0.8f, 3.0f, 20 * 1000))
                .setBitrateAdjustMode(
                        mBitrateControl.equals("auto") ? StreamingProfile.BitrateAdjustMode.Auto
                                : (mBitrateControl.equals("manual") ? StreamingProfile.BitrateAdjustMode.Manual
                                : StreamingProfile.BitrateAdjustMode.Disable));

        //Set AVProfile Manually, which will cover `setXXXQuality`
        if (isCustomSettingEnabled) {
            StreamingProfile.AudioProfile aProfile = new StreamingProfile.AudioProfile(44100, 96 * 1024);
            StreamingProfile.VideoProfile vProfile = new StreamingProfile.VideoProfile(15, 800 * 1024, 15 * 2);
            StreamingProfile.AVProfile avProfile = new StreamingProfile.AVProfile(vProfile, aProfile);
            mStreamingProfile.setAVProfile(avProfile);
        }

        if (mIsFullscreen) {
            mStreamingProfile.setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.LAND);
        } else {
            mStreamingProfile.setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.PORT);
        }

        WatermarkSetting watermarksetting = null;
        if (isWaterMarkEnabled) {
            watermarksetting = new WatermarkSetting(getActivity());
            watermarksetting.setResourceId(R.drawable.qiniu_logo)
                    .setSize(WatermarkSetting.WATERMARK_SIZE.MEDIUM)
                    .setAlpha(100)
                    .setCustomPosition(0.5f, 0.5f);
        }
        mRTCStreamingManager.prepare(cameraStreamingSetting, null, watermarksetting, mStreamingProfile);
        mProgressDialog = new ProgressDialog(getActivity());
        return mContent;
    }


    @Override
    public void onResume() {
        super.onResume();
        mIsActivityPaused = false;
        /**
         * Step 10: You must start capture before conference or streaming
         * You will receive `Ready` state callback when capture started success
         */
        mRTCStreamingManager.startCapture();
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsActivityPaused = true;
        /**
         * Step 11: You must stop capture, stop conference, stop streaming when activity paused
         */
        mRTCStreamingManager.stopCapture();
        stopPublishStreaming();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /**
         * Step 12: You must call destroy to release some resources when activity destroyed
         */
        mRTCStreamingManager.destroy();
        /**
         * Step 13: You can also move this to your MainActivity.onDestroy
         */
        RTCMediaStreamingManager.deinit();
    }


    private boolean startPublishStreaming() {

        if (mIsPublishStreamStarted) {
            return true;
        }
        if (!mIsInReadyState) {
            showToast(getString(R.string.stream_state_not_ready), Toast.LENGTH_SHORT);
            return false;
        }
        mProgressDialog.setMessage("正在准备推流... ");
        mProgressDialog.show();
        try {
            createPlayback(4);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return true;
    }


    private boolean startPublishStreamingInternal() {
        try {
            if (StreamUtils.IS_USING_STREAMING_JSON) {
                mStreamingProfile.setStream(new StreamingProfile.Stream(new JSONObject(publishAddr)));
            } else {
                mStreamingProfile.setPublishUrl(publishAddr);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            dismissProgressDialog();
            showToast("无效的推流地址 !", Toast.LENGTH_SHORT);
            return false;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            dismissProgressDialog();
            showToast("无效的推流地址 !", Toast.LENGTH_SHORT);
            return false;
        }

        mRTCStreamingManager.setStreamingProfile(mStreamingProfile);
        if (!mRTCStreamingManager.startStreaming()) {
            dismissProgressDialog();
            showToast(getString(R.string.failed_to_start_streaming), Toast.LENGTH_SHORT);
            return false;
        }
        dismissProgressDialog();
        showToast(getString(R.string.start_streaming), Toast.LENGTH_SHORT);
        updateControlButtonText();
        mIsPublishStreamStarted = true;
        /**
         * Because `startPublishStreaming` need a long time in some weak network
         * So we should check if the activity paused.
         */
        if (mIsActivityPaused) {
            stopPublishStreaming();
        }
        return true;
    }

    private boolean stopPublishStreaming() {
        if (!mIsPublishStreamStarted) {
            return true;
        }
        mRTCStreamingManager.stopStreaming();
        mIsPublishStreamStarted = false;
        showToast(getString(R.string.stop_streaming), Toast.LENGTH_SHORT);
        updateControlButtonText();
        return false;
    }

    private StreamingStateChangedListener mStreamingStateChangedListener = new StreamingStateChangedListener() {
        @Override
        public void onStateChanged(final StreamingState state, Object o) {
            switch (state) {
                case PREPARING:
                    setStatusText(getString(R.string.preparing));
                    Log.d(TAG, "onStateChanged state:" + "preparing");
                    break;
                case READY:
                    mIsInReadyState = true;
                    setStatusText(getString(R.string.ready));
                    Log.d(TAG, "onStateChanged state:" + "ready");
                    break;
                case CONNECTING:
                    Log.d(TAG, "onStateChanged state:" + "connecting");
                    break;
                case STREAMING:
                    setStatusText(getString(R.string.streaming));
                    Log.d(TAG, "onStateChanged state:" + "streaming");
                    break;
                case SHUTDOWN:
                    mIsInReadyState = true;
                    setStatusText(getString(R.string.ready));
                    Log.d(TAG, "onStateChanged state:" + "shutdown");
                    break;
                case UNKNOWN:
                    Log.d(TAG, "onStateChanged state:" + "unknown");
                    break;
                case SENDING_BUFFER_EMPTY:
                    Log.d(TAG, "onStateChanged state:" + "sending buffer empty");
                    break;
                case SENDING_BUFFER_FULL:
                    Log.d(TAG, "onStateChanged state:" + "sending buffer full");
                    break;
                case OPEN_CAMERA_FAIL:
                    Log.d(TAG, "onStateChanged state:" + "open camera failed");
                    showToast(getString(R.string.failed_open_camera), Toast.LENGTH_SHORT);
                    break;
                case AUDIO_RECORDING_FAIL:
                    Log.d(TAG, "onStateChanged state:" + "audio recording failed");
                    showToast(getString(R.string.failed_open_microphone), Toast.LENGTH_SHORT);
                    break;
                case IOERROR:
                    /**
                     * Network-connection is unavailable when `startStreaming`.
                     * You can do reconnecting or just finish the streaming
                     */
                    Log.d(TAG, "onStateChanged state:" + "io error");
                    showToast(getString(R.string.io_error), Toast.LENGTH_SHORT);
                    sendReconnectMessage();
                    // stopPublishStreaming();
                    break;
                case DISCONNECTED:
                    /**
                     * Network-connection is broken after `startStreaming`.
                     * You can do reconnecting in `onRestartStreamingHandled`
                     */
                    Log.d(TAG, "onStateChanged state:" + "disconnected");
                    setStatusText(getString(R.string.disconnected));
                    // we will process this state in `onRestartStreamingHandled`
                    break;
            }
        }
    };

    private StreamingSessionListener mStreamingSessionListener = new StreamingSessionListener() {
        @Override
        public boolean onRecordAudioFailedHandled(int code) {
            return false;
        }

        /**
         * When the network-connection is broken, StreamingState#DISCONNECTED will notified first,
         * and then invoked this method if the environment of restart streaming is ready.
         *
         * @return true means you handled the event; otherwise, given up and then StreamingState#SHUTDOWN
         * will be notified.
         */
        @Override
        public boolean onRestartStreamingHandled(int code) {
            Log.d(TAG, "onRestartStreamingHandled, reconnect ...");
            return mRTCStreamingManager.startStreaming();
        }

        @Override
        public Camera.Size onPreviewSizeSelected(List<Camera.Size> list) {
            for (Camera.Size size : list) {
//                if (size.height >= 480) {
//                    return size;
//                }
                if (size.width == 1280 && size.height == 720) {
                    return size;
                }
            }
            return null;
        }
    };

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what != MESSAGE_ID_RECONNECTING || mIsActivityPaused || !mIsPublishStreamStarted) {
                return;
            }
            if (!StreamUtils.isNetworkAvailable(getActivity())) {
                sendReconnectMessage();
                return;
            }
            Log.d(TAG, "do reconnecting ...");
            mRTCStreamingManager.startStreaming();
        }
    };

    private void sendReconnectMessage() {
        showToast("正在重连...", Toast.LENGTH_SHORT);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_ID_RECONNECTING), 500);
    }


    private void setStatusText(final String status) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setText(status);
            }
        });
    }

    private void updateControlButtonText() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mIsPublishStreamStarted) {
                    mControlButton.setText(getString(R.string.stop_streaming));
                } else {
                    mControlButton.setText(getString(R.string.start_streaming));
                }
            }
        });
    }

    private StreamStatusCallback mStreamStatusCallback = new StreamStatusCallback() {
        @Override
        public void notifyStreamStatusChanged(final StreamingProfile.StreamStatus streamStatus) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String stat = "bitrate: " + streamStatus.totalAVBitrate / 1024 + " kbps"
                            + "\naudio: " + streamStatus.audioFps + " fps"
                            + "\nvideo: " + streamStatus.videoFps + " fps";
                    mStatTextView.setText(stat);
                }
            });
        }
    };

    private void dismissProgressDialog() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog.dismiss();
            }
        });
    }

    private void showToast(final String text, final int duration) {
        if (mIsActivityPaused) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast != null) {
                    mToast.cancel();
                }
                mToast = Toast.makeText(getActivity(), text, duration);
                mToast.show();
            }
        });
    }

    private CameraStreamingSetting.CAMERA_FACING_ID chooseCameraFacingId() {
        if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD)) {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
        } else if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT)) {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
        } else {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
        }
    }


    /***
     * 预发布环境 13260656511   123456   社群 1111
     */
    String publishAddr = "{\"id\":\"z1.gaiay-pro.599b9101a3d5ec50f009f48c\",\"title\":\"599b9101a3d5ec50f009f48c\",\"hub\":\"gaiay-pro\",\"publishKey\":\"926d11c9-da06-4927-ba80-0e0b6b3fb91d\",\"publishSecurity\":\"static\",\"disabled\":false,\"hosts\":{\"publish\":{\"rtmp\":\"pili-publish.live.zm.gaiay.cn\"},\"live\":{\"rtmp\":\"pili-live-rtmp.live.zm.gaiay.cn\",\"http\":\"pili-live-hls.live.zm.gaiay.cn\",\"hls\":\"pili-live-hls.live.zm.gaiay.cn\"},\"playback\":{\"hls\":\"100004v.playback1.z1.pili.qiniucdn.com\",\"http\":\"100004v.playback1.z1.pili.qiniucdn.com\"}},\"createTime\":1503367425243,\"updateTime\":1503367425243,\"liveId\":32835,\"playBackAuth\":0}";

    /**
     * @param type 2、暂停直播；3、结束直播；4、开始推流；16、关闭
     */
    private void createPlayback(final int type) throws JSONException {
        final ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.show();
        JSONObject obj = new JSONObject(publishAddr);
        int liveId = obj.optInt("liveId");
        AndroidNetworking.post("https://t1.m.gaiay.net/api/zm/w/live/control?userId=0aea05151b4e594b7-7e03&appOs=android&token=752ff8869049c9dbc17c80d26133df9a&appVersion=5.9.28")
                .addBodyParameter("id", "" + liveId)
                .addBodyParameter("circleId", "d4d3ea159ca0fec08-7fef")
                .addBodyParameter("type", "" + type)
                .addBodyParameter("userId", "0aea05151b4e594b7-7e03")
                .setTag("test")
                .setPriority(Priority.MEDIUM)
                .build()
                .getAsJSONObject(new JSONObjectRequestListener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        dialog.dismiss();
                        if (response.optInt("code") == 0) {
                            Toast.makeText(getActivity(), "成功！！！", Toast.LENGTH_SHORT).show();

                            if (type == 4) {
                                AsyncTask.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        startPublishStreamingInternal();
                                    }
                                });
                                mTvPlayBack.setText("结束直播");
                                mTvPlayBack.setVisibility(View.VISIBLE);
                            } else if (type == 3) {
                                mTvPlayBack.setText("开始直播");
                                mTvPlayBack.setVisibility(View.VISIBLE);
                                Log.i("PlaybackUrl", "URL = " + response.optJSONObject("result").optString("playBackUrl"));
                            }

                        } else {
                            Toast.makeText(getActivity(), "失败！！！", Toast.LENGTH_SHORT).show();
                            if (type == 4) {
                                mTvPlayBack.setText("开始推流");
                                mTvPlayBack.setVisibility(View.GONE);
                            } else if (type == 3) {
                                mTvPlayBack.setText("结束直播");
                                mTvPlayBack.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onError(ANError error) {
                        Toast.makeText(getActivity(), "Error !!!", Toast.LENGTH_SHORT).show();
                        mTvPlayBack.setText("Error!");
                    }
                });
    }

    private ScreenListener mScreenListener;

    public void setScreenListener(ScreenListener listener) {
        this.mScreenListener = listener;
    }

    public interface ScreenListener {
        void toCap();

        void toFull();
    }
}
