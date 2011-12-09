/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.android_scripting.facade;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;

import com.googlecode.android_scripting.BaseApplication;
import com.googlecode.android_scripting.FileUtils;
import com.googlecode.android_scripting.FutureActivityTaskExecutor;
import com.googlecode.android_scripting.Log;
import com.googlecode.android_scripting.future.FutureActivityTask;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcDefault;
import com.googlecode.android_scripting.rpc.RpcParameter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Access Camera functions.
 * 
 */
public class CameraFacade extends RpcReceiver {

  private final Service mService;
  private final Parameters[] mParameters;

  private class BooleanResult {
    boolean mmResult = false;
  }

  public CameraFacade(FacadeManager manager) {
    super(manager);
    mService = manager.getService();
    mParameters = new Parameters[Camera.getNumberOfCameras()];
    for (int cameraId = 0; cameraId < Camera.getNumberOfCameras(); cameraId++) {
      Camera camera = Camera.open(cameraId);
      try {
        mParameters[cameraId] = camera.getParameters();
      } finally {
        camera.release();
      }
    }
  }

  /**
   * Makes the camera preview image show in the same orientation as the display Code from:
   * http://developer.android.com/reference/android/hardware/Camera.html
   */
  public static void setCameraDisplayOrientation(Activity activity, int cameraId,
      android.hardware.Camera camera) {
    android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
    android.hardware.Camera.getCameraInfo(cameraId, info);
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    int degrees = 0;
    switch (rotation) {
    case Surface.ROTATION_0:
      degrees = 0;
      break;
    case Surface.ROTATION_90:
      degrees = 90;
      break;
    case Surface.ROTATION_180:
      degrees = 180;
      break;
    case Surface.ROTATION_270:
      degrees = 270;
      break;
    }

    int result;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      result = (info.orientation + degrees) % 360;
      result = (360 - result) % 360; // compensate the mirror
    } else { // back-facing
      result = (info.orientation - degrees + 360) % 360;
    }
    Log.d(String.format("Rotating camera display orientation by %d degrees", result));
    camera.setDisplayOrientation(result);
  }

  @Rpc(description = "Take a picture and save it to the specified path.", returns = "A map of Booleans autoFocus and takePicture where True indicates success.")
  public Bundle cameraCapturePicture(@RpcParameter(name = "targetPath") final String targetPath,
      @RpcParameter(name = "useAutoFocus") @RpcDefault("true") Boolean useAutoFocus,
      @RpcParameter(name = "cameraId") @RpcDefault("0") Integer cameraId)
      throws InterruptedException {
    final BooleanResult autoFocusResult = new BooleanResult();
    final BooleanResult takePictureResult = new BooleanResult();
    // use default camera if the cameraId specified does not exist
    if (cameraId < 0 || cameraId >= Camera.getNumberOfCameras()) {
      cameraId = 0;
      Log.d("CameraId requested does not exist. Using default camera.");
    }
    CameraInfo info = new android.hardware.Camera.CameraInfo();
    Camera camera = Camera.open(cameraId);
    android.hardware.Camera.getCameraInfo(cameraId, info);
    camera.setParameters(mParameters[cameraId]);

    try {
      FutureActivityTask<SurfaceHolder> previewTask = setPreviewDisplay(camera);
      // Change orientation of preview picture to match display
      setCameraDisplayOrientation(previewTask.getActivity(), cameraId, camera);
      camera.startPreview();
      if (useAutoFocus) {
        autoFocus(autoFocusResult, camera);
      }
      takePicture(new File(targetPath), takePictureResult, camera);
      previewTask.finish();
    } catch (Exception e) {
      Log.e(e);
    } finally {
      camera.release();
    }

    Bundle result = new Bundle();
    result.putBoolean("autoFocus", autoFocusResult.mmResult);
    result.putBoolean("takePicture", takePictureResult.mmResult);
    return result;
  }

  private FutureActivityTask<SurfaceHolder> setPreviewDisplay(Camera camera) throws IOException,
      InterruptedException {
    FutureActivityTask<SurfaceHolder> task = new FutureActivityTask<SurfaceHolder>() {
      @Override
      public void onCreate() {
        super.onCreate();
        final SurfaceView view = new SurfaceView(getActivity());
        getActivity().setContentView(view);
        getActivity().getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        view.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        view.getHolder().addCallback(new Callback() {
          @Override
          public void surfaceDestroyed(SurfaceHolder holder) {
          }

          @Override
          public void surfaceCreated(SurfaceHolder holder) {
            setResult(view.getHolder());
          }

          @Override
          public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
          }
        });
      }
    };
    FutureActivityTaskExecutor taskQueue =
        ((BaseApplication) mService.getApplication()).getTaskExecutor();
    taskQueue.execute(task);
    camera.setPreviewDisplay(task.getResult());
    return task;
  }

  private void takePicture(final File file, final BooleanResult takePictureResult,
      final Camera camera) throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    camera.takePicture(null, null, new PictureCallback() {
      @Override
      public void onPictureTaken(byte[] data, Camera camera) {
        if (!FileUtils.makeDirectories(file.getParentFile(), 0755)) {
          takePictureResult.mmResult = false;
          return;
        }
        try {
          FileOutputStream output = new FileOutputStream(file);
          output.write(data);
          output.close();
          takePictureResult.mmResult = true;
        } catch (FileNotFoundException e) {
          Log.e("Failed to save picture.", e);
          takePictureResult.mmResult = false;
          return;
        } catch (IOException e) {
          Log.e("Failed to save picture.", e);
          takePictureResult.mmResult = false;
          return;
        } finally {
          latch.countDown();
        }
      }
    });
    latch.await();
  }

  private void autoFocus(final BooleanResult result, final Camera camera)
      throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    {
      camera.autoFocus(new AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
          result.mmResult = success;
          latch.countDown();
        }
      });
      latch.await();
    }
  }

  @Override
  public void shutdown() {
    // Nothing to clean up.
  }

  @Rpc(description = "Starts the image capture application to take a picture and saves it to the specified path.")
  public void cameraInteractiveCapturePicture(
      @RpcParameter(name = "targetPath") final String targetPath) {
    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    File file = new File(targetPath);
    intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
    AndroidFacade facade = mManager.getReceiver(AndroidFacade.class);
    facade.startActivityForResult(intent);
  }
}
