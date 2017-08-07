/*
* Copyright 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


package com.example.android.screencapture;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ViewAnimator;

import com.example.android.common.activities.SampleActivityBase;
import com.example.android.common.logger.Log;
import com.example.android.common.logger.LogWrapper;
import com.example.android.common.logger.MessageOnlyLogFilter;

import com.six15.hud.HudResponsePacket;
import com.six15.hud.UsbService;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * A simple launcher activity containing a summary sample description, sample log and a custom
 * {@link android.support.v4.app.Fragment} which can display a view.
 * <p>
 * For devices with displays with a width of 720dp or greater, the sample log is always visible,
 * on other devices it's visibility is controlled by an item on the Action Bar.
 */
public class MainActivity extends SampleActivityBase {

    public static final String TAG = "MainActivity";

    // Whether the Log Fragment is currently shown
    private boolean mCaptureOn;

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private int mScreenDensity;

    private int mResultCode;
    private Intent mResultData;

    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private SurfaceView mSurfaceView;

    private static final int FRAMERATE = 5;
    private int mWidth = 640;
    private int mHeight = 400;

    private ImageReader imageReader;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private boolean bHudConnected = false;
    private Handler mHandler;
    private UsbService usbService = null;
    private static boolean bRenderframe = true;

    /*
     * This function handles repsonse packets from the UsbService.
    */
    public void responsePacketHandler(HudResponsePacket resp) {
        switch (resp.getCommand()){
            case HC_STATUS:
                android.util.Log.i(TAG, "Response HC_STATUS");
                break;
            case HC_VERSIONS:
                android.util.Log.i(TAG, "Response HC_VERSIONS");
                break;
            case HC_DEV_INFO:
                android.util.Log.i(TAG, "Response HC_DEV_INFO");
                break;
            case HC_DEV_SETTINGS:
                android.util.Log.i(TAG, "Response HC_DEV_SETTINGS");
                break;
            case HC_MODE_UPD:
                android.util.Log.i(TAG, "Response HC_MODE_UPD");
                break;
            case HC_MODE_SRST:
                android.util.Log.i(TAG, "Response HC_MODE_SRST");
                break;
            case HC_DISP_SIZE:
                android.util.Log.i(TAG, "Response HC_DISP_SIZE");
                break;
            case HC_DISP_BRT:
                android.util.Log.i(TAG, "Response HC_DISP_BRT");
                break;
            case HC_DISP_ON:
                android.util.Log.i(TAG, "Response HC_DISP_ON");
                break;
            case HC_DISP_INFO:
                android.util.Log.i(TAG, "Response HC_DISP_INFO");
                break;
            case HC_CFG_SPEN:
                android.util.Log.i(TAG, "Response HC_CFG_SPEN");
                break;
            case HC_CFG_SPDEL:
                android.util.Log.i(TAG, "Response HC_CFG_SPDEL");
                break;
            case HC_CFG_SPIMAGE:
                android.util.Log.i(TAG, "Response HC_CFG_SPIMAGE");
                break;
            case HC_CFG_RESET:
                android.util.Log.i(TAG, "Response HC_CFG_RESET");
                break;
            case HC_DEV_METRICS:
                android.util.Log.i(TAG, "Response HC_DEV_METRICS");
                break;
            case HC_HEART_BEAT:
                Toast.makeText(this, "Ping Response Received", Toast.LENGTH_LONG).show();
                break;
            default:
                android.util.Log.i(TAG, "Response not handled");
                break;
        }

    }

    /*
 * Notifications from UsbService will be received here.
 */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    //Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    usbConnectDisplay(true);
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    usbConnectDisplay(false);
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    //Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    usbConnectDisplay(false);
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    //Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    usbConnectDisplay(false);
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    usbConnectDisplay(false);
                    break;
            }
        }
    };

    private void usbConnectDisplay(boolean enabled) {
        if (enabled) {
            android.util.Log.i(TAG, "Darwin HUD Connected");
            bHudConnected = true;
        } else {
            android.util.Log.i(TAG, "Darwin HUD Disconnected");
            bHudConnected = false;
        }
    }

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler); /* ui thread handler needed by service */
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
            mResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
        }

        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart(){
        super.onStart();

        setFilters();
        startService(UsbService.class, usbConnection, null);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mMediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    protected void onStop(){
        super.onStop();
    }
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenCapture();
        tearDownMediaProjection();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResultData != null) {
            outState.putInt(STATE_RESULT_CODE, mResultCode);
            outState.putParcelable(STATE_RESULT_DATA, mResultData);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem logToggle = menu.findItem(R.id.menu_toggle_cap);
        logToggle.setVisible(findViewById(R.id.sample_output) instanceof ViewAnimator);
        logToggle.setTitle(mCaptureOn ? R.string.sample_cap_stop : R.string.sample_cap_on);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_toggle_cap:
                mCaptureOn = !mCaptureOn;
                ViewAnimator output = (ViewAnimator) findViewById(R.id.sample_output);
                if (mCaptureOn && mVirtualDisplay == null) {
                    startScreenCapture();
                } else {
                    stopScreenCapture();
                }

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Create a chain of targets that will receive log data */
    @Override
    public void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);

        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);

        Log.i(TAG, "Ready");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                Toast.makeText(this, R.string.user_cancelled, Toast.LENGTH_SHORT).show();
                return;
            }

            Log.i(TAG, "Starting screen capture");
            mResultCode = resultCode;
            mResultData = data;
            setUpMediaProjection();
            setUpVirtualDisplay();
        }
    }

    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void startScreenCapture() {


        if (mMediaProjection != null) {
            setUpVirtualDisplay();
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setUpVirtualDisplay();
        } else {
            Log.i(TAG, "Requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);
        }
    }

    private static int count = 0;
    public ImageReader.OnImageAvailableListener hudImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            ByteBuffer buffer;
            Image image = reader.acquireLatestImage();
            if (image == null)
                return;

            final Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
            buffer = image.getPlanes()[0].getBuffer();
            bmp.copyPixelsFromBuffer(buffer);

            if(bRenderframe) {
                if (usbService != null && bHudConnected) {
                    usbService.sendImageToHud(bmp);
                }
            }

/*
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mImageView.setImageBitmap(bmp);
                }
            });
*/
            image.close();
            if(count == 0 && bRenderframe) {
                bRenderframe = !bRenderframe;
                count++;
            }else{
                if(count >= 3 ){
                    bRenderframe = true;
                    count = 0;
                }else{
                    count++;
                }
            }
        }
    };

    private void setUpVirtualDisplay() {
        Log.i(TAG, "Setting up a VirtualDisplay: " +
                mWidth + "x" + mHeight +
                " (RGBX 8888)");

        imageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBX_8888, FRAMERATE);
        Surface readerSurface = imageReader.getSurface();

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("SIX-15 HUD",
                mWidth, mHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                readerSurface, null, null);

        imageReader.setOnImageAvailableListener(hudImageListener, mBackgroundHandler);
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }

}
