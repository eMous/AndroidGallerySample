package au.edu.sydney.comp5216.assignment2;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;


import android.app.Activity;
import android.graphics.Bitmap.CompressFormat;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.provider.MediaStore.Images;
import android.view.ContextThemeWrapper;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnClickListener;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;

/**
 * CameraActivity.java
 * <p>
 * It is the main activity of the Camera&Gallery Android Application.
 * This class contains permission stuff to acquire external write and read permission,
 * firebase relevant stuff to register and login firebase user to do authentication,
 * UI stuff handler to response ui message from other thread(created and called by ThreadPoolManager)
 * and update UI.
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class CameraActivity extends Activity implements Callback, Camera.PreviewCallback
        , OnClickListener {

    // UI relevant stuff
    /**
     * surfaceView
     */
    SurfaceView mSurfaceView;
    SurfaceHolder mSurfaceHolder;
    Button mFlipCameraBtn;
    Button mCaptureImageBtn;
    FloatingActionButton mBackBtn;
    SquareImageView mThumbnailSquareImageView;

    /**
     * Camera object
     */
    Camera mCamera;


    // Camera configuration
    /**
     * Camera id, front or back
     */
    int mCameraId;
    /**
     * Always true, becaus I don't have an android phone
     */
    boolean mInEmulator;
    /**
     * Using emulator, always failed to using native capture method of camera
     * no matter version 1 or version 2, so use frame data as the photo,
     * the variable is a flag whether to store the frame data as a photo
     */
    boolean mCaptureBtnClicked;
    /**
     * Rotation default is orientation
     */
    int mRotation;

    /**
     * Stored photo path, when user click the left bottom thumbnail, it shows
     * Set this variable to make sure user get what he see when click the left bottom
     * instead of always get the latest photo, because latest photo may get refreshed
     * when downloading from firebase
     */
    String mThumbnailFilePath;

    /**
     * Request to invoke a photo preview activity,
     * CameraActivity needs the result whether user wants go back to main activity
     * or simply wants to finish the preview activity
     */
    static final int REQUEST_PREVIEW_CAPTURED_PHOTO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        CommonModel.getInstance().setCameraActivity(this);


        // By default use the back camera
        mCameraId = CameraInfo.CAMERA_FACING_BACK;

        mInEmulator = true;
        mCaptureBtnClicked = false;

        // UI stuff initiation
        mFlipCameraBtn = findViewById(R.id.flipCamera);
        mCaptureImageBtn = findViewById(R.id.captureImage);
        mSurfaceView = findViewById(R.id.surfaceView);
        mBackBtn = findViewById(R.id.floating_back_button);
        mThumbnailSquareImageView = findViewById(R.id.thumbnail);
        mSurfaceHolder = mSurfaceView.getHolder();

        mSurfaceHolder.addCallback(this);
        mFlipCameraBtn.setOnClickListener(this);
        mCaptureImageBtn.setOnClickListener(this);
        mBackBtn.setOnClickListener(this);
        mThumbnailSquareImageView.setOnClickListener(this);

        // Always turn on screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // If there are both front and back camera, show flip button
        if (Camera.getNumberOfCameras() > 1) {
            mFlipCameraBtn.setVisibility(View.VISIBLE);
        }

        // Initiate thumbnail
        updateThumbnail();
    }

    @Override
    /**
     * When surface is created, open the camera
     */
    public void surfaceCreated(SurfaceHolder holder) {
        if (!openCamera(CameraInfo.CAMERA_FACING_BACK)) {
            showAlertCameraDialog();
        }
    }

    /**
     * Get camera, setup camera, start preview
     * @param cameraId front or back (1 or 0)
     * @return whether open is successful
     */
    private boolean openCamera(int cameraId) {
        boolean result = false;
        mCameraId = cameraId;
        // release previous one
        releaseCamera();
        try {
            mCamera = Camera.open(mCameraId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mCamera != null) {
            try {
                setUpCamera(mCamera);
                mCamera.setErrorCallback((error, camera) -> {

                });
                mCamera.setPreviewDisplay(mSurfaceHolder);
                // Capture function is set here
                mCamera.setPreviewCallback(this);
                // Lively preview starts
                mCamera.startPreview();
                result = true;
            } catch (IOException e) {
                e.printStackTrace();
                result = false;
                releaseCamera();
            }
        }
        return result;
    }

    /**
     * Set up the camera display default rotation;
     * @return whether open is successful
     */
    private void setUpCamera(Camera c) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);

        mRotation = getWindowManager().getDefaultDisplay().getRotation();
        int degree = 0;
        switch (mRotation) {
            case Surface.ROTATION_0:
                degree = 0;
                break;
            case Surface.ROTATION_90:
                degree = 90;
                break;
            case Surface.ROTATION_180:
                degree = 180;
                break;
            case Surface.ROTATION_270:
                degree = 270;
                break;

            default:
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            // frontFacing
            mRotation = (info.orientation + degree) % 330;
            mRotation = (360 - mRotation) % 360;
        } else {
            // Back-facing
            mRotation = (info.orientation - degree + 360) % 360;
        }
        c.setDisplayOrientation(mRotation);
        Parameters params = c.getParameters();
        params.setRotation(mRotation);
    }

    /**
     * Clear relevant data, and call camera's release
     */
    private void releaseCamera() {
        try {
            if (mCamera != null) {
                mCamera.setPreviewCallback(null);
                mCamera.setErrorCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("error", e.toString());
            mCamera = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    /**
     * A union function responds to each view's click event
     * @param view each view
     */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.flipCamera:
                flipCamera();
                break;
            case R.id.captureImage:
                takeImage();
                break;
            case R.id.floating_back_button:
                finish();
                break;
            case R.id.thumbnail:
                invokePreviewCapturedPhotoActivity(mThumbnailFilePath);
                break;
            default:
                break;
        }
    }
    /**
     * Start a PreviewCapturedPhotoActivity
     * @param filePath file path of the photo which is going to be previewed
     */
    public void invokePreviewCapturedPhotoActivity(String filePath) {
        Intent intent = new Intent(this, PreviewCapturedPhotoActivity.class);
        intent.putExtra(PreviewCapturedPhotoActivity.INTENT_PARAM_FILE_PATH, filePath);
        startActivityForResult(intent, REQUEST_PREVIEW_CAPTURED_PHOTO);
    }

    @Override
    /**
     * Activity result callback
     * @param requestCode the request code to start the activity where result from
     * @param resultCode whether result is ok or sth else
     * @param data extra data from the activity
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            // If user click go back to main activity, when CameraActivity receive the message,
            // it should also finish
            case REQUEST_PREVIEW_CAPTURED_PHOTO:
                if (data.hasExtra(PreviewCapturedPhotoActivity.INTENT_PARAM_RETURN_TO_MAIN)) {
                    if (data.getBooleanExtra(
                            PreviewCapturedPhotoActivity.INTENT_PARAM_RETURN_TO_MAIN,
                            false)) {
                        finish();
                    }
                }
                break;
        }
    }
    /**
     * Native take picture function(not test due to lack of android device)
     * and set trigger to store the preview data
     */
    private void takeImage() {
        if (mInEmulator) {
            mCaptureBtnClicked = true;
        } else {
            // below is not tested, and will never be called
            mCamera.takePicture(null, null, new PictureCallback() {

                private File imageFile;

                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    try {
                        // convert byte array into bitmap
                        Bitmap loadedImage = null;
                        Bitmap rotatedBitmap = null;
                        loadedImage = BitmapFactory.decodeByteArray(data, 0,
                                data.length);

                        // rotate Image
                        Matrix rotateMatrix = new Matrix();
                        rotateMatrix.postRotate(mRotation);
                        rotatedBitmap = Bitmap.createBitmap(loadedImage, 0, 0,
                                loadedImage.getWidth(), loadedImage.getHeight(),
                                rotateMatrix, false);
                        String state = Environment.getExternalStorageState();
                        File folder = null;
                        if (state.contains(Environment.MEDIA_MOUNTED)) {
                            folder = new File(Environment
                                    .getExternalStorageDirectory() + "/Demo");
                        } else {
                            folder = new File(Environment
                                    .getExternalStorageDirectory() + "/Demo");
                        }

                        boolean success = true;
                        if (!folder.exists()) {
                            success = folder.mkdirs();
                        }
                        if (success) {
                            java.util.Date date = new java.util.Date();
                            imageFile = new File(folder.getAbsolutePath()
                                    + File.separator
                                    + new Date().getTime()
                                    + "Image.jpg");

                            imageFile.createNewFile();
                        } else {
                            Toast.makeText(getBaseContext(), "Image Not saved",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        ByteArrayOutputStream ostream = new ByteArrayOutputStream();

                        // save image into gallery
                        rotatedBitmap.compress(CompressFormat.JPEG, 100, ostream);

                        FileOutputStream fout = new FileOutputStream(imageFile);
                        fout.write(ostream.toByteArray());
                        fout.close();
                        ContentValues values = new ContentValues();

                        values.put(Images.Media.DATE_TAKEN,
                                System.currentTimeMillis());
                        values.put(Images.Media.MIME_TYPE, "image/jpeg");
                        values.put(MediaStore.MediaColumns.DATA,
                                imageFile.getAbsolutePath());

                        CameraActivity.this.getContentResolver().insert(
                                Images.Media.EXTERNAL_CONTENT_URI, values);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }

    /**
     * Open another camera
     */
    private void flipCamera() {
        int id = (mCameraId == CameraInfo.CAMERA_FACING_BACK ? CameraInfo.CAMERA_FACING_FRONT
                : CameraInfo.CAMERA_FACING_BACK);
        if (!openCamera(id)) {
            showAlertCameraDialog();
        }
    }

    /**
     * Show error stuff
     */
    private void showAlertCameraDialog() {
        AlertDialog.Builder dialog = createAlert(CameraActivity.this,
                "Camera info", "error to open camera");
        dialog.setNegativeButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });

        dialog.show();
    }

    /**
     * Show error stuff
     */
    private AlertDialog.Builder createAlert(Context context, String title, String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(
                new ContextThemeWrapper(context,
                        android.R.style.Theme_Holo_Light_Dialog));
        if (title != null)
            dialog.setTitle(title);
        else
            dialog.setTitle("Information");
        dialog.setMessage(message);
        dialog.setCancelable(false);
        return dialog;
    }


    @Override
    /**
     * Actual take photo function
     * @param data frame data
     * @param camera camera object
     */
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mInEmulator) {
            if (mCaptureBtnClicked) {
                mCaptureBtnClicked = false;
                Parameters parameters = camera.getParameters();
                int imageFormat = parameters.getPreviewFormat();
                int w = parameters.getPreviewSize().width;
                int h = parameters.getPreviewSize().height;

                Rect rect = new Rect(0, 0, w, h);
                YuvImage yuvImg = new YuvImage(data, imageFormat, w, h, null);
                try {
                    ByteArrayOutputStream outPutstream = new ByteArrayOutputStream();
                    yuvImg.compressToJpeg(rect, 100, outPutstream);
                    // get raw bitmap from YuvImage
                    Bitmap rawBitmap = BitmapFactory.decodeByteArray(outPutstream.toByteArray(), 0,
                            outPutstream.size());
                    // rotate Image
                    Matrix rotateMatrix = new Matrix();
                    if (mCameraId == CameraInfo.CAMERA_FACING_FRONT) {
                        rotateMatrix.postScale(-1, 1);   //镜像水平翻转
                        rotateMatrix.postRotate(90);
                    } else {
                        rotateMatrix.postRotate(mRotation);
                    }
                    // get rotated bitmap
                    Bitmap rotatedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0,
                            rawBitmap.getWidth(), rawBitmap.getHeight(),
                            rotateMatrix, false);
                    PhotoOperator photoOperator =
                            CommonModel.getInstance().getMainActivity().getPhotoOperator();

                    // Call photoOperator's write function
                    photoOperator.writeAsync(System.currentTimeMillis() +
                                    PhotoOperator.JPG_EXTENSION, rotatedBitmap);

                } catch (Exception e) {
                    Log.e("TAG", "onPreviewFrame: Fail" + e.getLocalizedMessage());
                }
            }
        }
    }

    /**
     * Update thumbnail UI
     */
    public void updateThumbnail() {
        PhotoModel.Photo latestPhoto = PhotoModel.getInstance().getLatestPhoto();
        if (latestPhoto != null) {
            Picasso.get().load(new File(latestPhoto.filePath)).into(mThumbnailSquareImageView);
            mThumbnailFilePath = latestPhoto.filePath;
        }
    }
}
