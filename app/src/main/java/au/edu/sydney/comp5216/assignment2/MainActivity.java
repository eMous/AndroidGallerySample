package au.edu.sydney.comp5216.assignment2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.GridView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

import au.edu.sydney.comp5216.assignment2.Tasks.ThreadPoolManager;
import au.edu.sydney.comp5216.assignment2.Tasks.MessageUtil;
import au.edu.sydney.comp5216.assignment2.Tasks.UiThreadCallback;

import static au.edu.sydney.comp5216.assignment2.Tasks.MessageUtil.MESSAGE_BODY;

/**
 * MainActivity.java
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
public class MainActivity extends AppCompatActivity implements UiThreadCallback {
    /**
     * Permission group mark
     */
    static final int PERMISSION_READ_WRITE_CAMERA = 1;

    // Firebase relevant stuff
    /**
     * Firebase authentication instance
     */
    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    String mFirebaseEmail = "daydayhappy@fakemail.com";
    String mFirebasePassword = "i_am_Password";

    /**
     * Handler to handle message for updating ui from other thread
     */
    UiHandler mUiHandler;

    /**
     * Wrapped executor which maintains a thread pool to do async jobs
     */
    ThreadPoolManager mCustomThreadPoolManager;

    /**
     * Whether should alert the synchronization result, true only after user explicitly click sync
     */
    boolean mCheckDifferenceAlert = false;

    /**
     * Core class to do write/read locally and download/upload from/to firebase
     */
    PhotoOperator mPhotoOperator;

    // UI relevant stuff
    /**
     * GridView adapter to manage each cell display information
     */
    private PhotoGridViewAdapter mPhotoGridViewAdapter;
    GridView mGridView;
    FloatingActionButton mOpenCameraBtn;
    FloatingActionButton mSyncBtn;
    FloatingActionButton mRemoveAllBtn;

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permission first
        requestPermissions(
                new String[]{Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_READ_WRITE_CAMERA);


        // Set this to a static object, so each one can find this
        CommonModel.getInstance().setMainActivity(this);

//        FirebaseFirestore.setLoggingEnabled(true);

        // Firestore register and login
        mAuth = FirebaseAuth.getInstance();
        firebaseRegisterAndLogin(mFirebaseEmail, mFirebasePassword);

        // UI relevant stuff initiation
        uiInitiation();

        // Multi-thread request and response stuff
        mUiHandler = new UiHandler(Looper.myLooper());
        mCustomThreadPoolManager = ThreadPoolManager.getInstance();

        // File operation relevant
        mPhotoOperator = new PhotoOperator(getContentResolver());
    }

    /**
     * UI initiation job
     */
    public void uiInitiation() {
        mPhotoGridViewAdapter = new PhotoGridViewAdapter(this, PhotoModel.getInstance());

        mGridView = findViewById(R.id.grid);
        mGridView.setAdapter(mPhotoGridViewAdapter);

        mOpenCameraBtn = findViewById(R.id.floating_photo_view_button);
        mOpenCameraBtn.setOnClickListener(view -> {
            // start camera activity
            Intent intent = new Intent(this, CameraActivity.class);
            startActivity(intent);
        });

        mSyncBtn = findViewById(R.id.sync_button);
        mSyncBtn.setOnClickListener(view -> {
            mCustomThreadPoolManager.addCallable(new Callable() {
                @Override
                public Object call() throws Exception {
                    mCheckDifferenceAlert = true;
                    mPhotoOperator.sync();
                    return null;
                }
            });
        });

        mRemoveAllBtn = findViewById(R.id.delete_button);
        mRemoveAllBtn.setOnClickListener(view -> {
            for (PhotoModel.Photo eachPhoto : PhotoModel.getInstance().mPhotos) {
                new File(eachPhoto.filePath).delete();
            }
            PhotoModel.getInstance().mPhotos = new ArrayList<>();
            MainActivity.this.publishToUiThread(
                    MessageUtil.createMessage(MessageUtil.MESSAGE_ID_UPDATE_GRID_VIEW, ""));
            MainActivity.this.publishToUiThread(
                    MessageUtil.createMessage(MessageUtil.MESSAGE_ID_UPDATE_THUMBNAIL, ""));

            mCustomThreadPoolManager.addCallable(new Callable() {
                @Override
                public Object call() throws Exception {

                    return null;
                }
            });
        });
    }

    @Override
    /**
     * Called from work or main thread to ask UiHandler of main thread to handle UI updating message
     */
    public void publishToUiThread(Message message) {
        mUiHandler.sendMessage(message);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    /**
     * Permission result callback
     */
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // If user doesn't grant permission, ask him reopen app or manually give permission
        // Or the dialog will not disappear and the application is not workable
        if (requestCode == PERMISSION_READ_WRITE_CAMERA) {
            if (Arrays.stream(grantResults).filter(i -> i != 0).count() == 0) {
                onPermissionGranted();
            } else {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setMessage(
                                "Please, maybe manually, enable Data Write & Read and Camera permissions to use " +
                                "the " +
                                "app.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).create();

                CountDownTimer looper = new CountDownTimer(Long.MAX_VALUE, 100) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        if (!dialog.isShowing()) {
                            dialog.show();
                        }
                    }

                    @Override
                    public void onFinish() {

                    }
                };
                looper.start();
            }
        }
    }

    /**
     * Firebase register and login jobs
     */
    private void firebaseRegisterAndLogin(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this,
                task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("TAG", "createUserWithEmail:success");
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("TAG", "createUserWithEmail:failure", task.getException());
                    }
                });

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("TAG", "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("TAG", "signInWithEmail:failure", task.getException());
                    }
                });
    }


    /**
     * Permission granted callback
     */
    private void onPermissionGranted() {
        readAllLocalPhotoAndCheckDifference();
    }

    /**
     * Ask PhotoOperator to read all local photos, then sync the files from/to firebase async.
     */
    private void readAllLocalPhotoAndCheckDifference() {
        mPhotoOperator.readAllAsync();
    }

    // UI handler class, declared as static so it doesn't have implicit
    // reference to activity context. This helps to avoid memory leak.
    private static class UiHandler extends Handler {

        public UiHandler(Looper looper) {
            super(looper);
        }

        // This method will run on UI thread
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                // Our communication protocol for passing a string to the UI thread
                // Update GridView
                case MessageUtil.MESSAGE_ID_UPDATE_GRID_VIEW:
                    CommonModel.getInstance().getMainActivity().mPhotoGridViewAdapter.notifyDataSetChanged();
                    break;
                // Update Thumbnail in camera activity
                case MessageUtil.MESSAGE_ID_UPDATE_THUMBNAIL:
                    if (CommonModel.getInstance().getCameraActivity() != null) {
                        CommonModel.getInstance().getCameraActivity().updateThumbnail();
                    }
                    break;
                // Show sync result as a dialog
                case MessageUtil.MESSAGE_SHOW_SYNC_RESULT:
                    if (CommonModel.getInstance().getMainActivity().mCheckDifferenceAlert) {
                        AlertDialog dialog = new AlertDialog.Builder(CommonModel.getInstance().getMainActivity())
                                .setMessage(msg.getData().getString(MESSAGE_BODY))
                                .setPositiveButton("OK", (dialog1, which) -> {
                                }).create();
                        dialog.show();
                    }
                    CommonModel.getInstance().getMainActivity().mCheckDifferenceAlert = false;
                    break;
                default:
                    break;
            }
        }
    }

    public PhotoOperator getPhotoOperator() {
        return mPhotoOperator;
    }
}