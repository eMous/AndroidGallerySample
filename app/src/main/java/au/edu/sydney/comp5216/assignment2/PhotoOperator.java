package au.edu.sydney.comp5216.assignment2;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import au.edu.sydney.comp5216.assignment2.Tasks.MessageUtil;
import au.edu.sydney.comp5216.assignment2.Tasks.ThreadPoolManager;

import static android.content.ContentValues.TAG;

/**
 * PhotoOperator.java
 * <p>
 * Core class to manipulate file-relevant write/read
 * and synchronization stuff of this application
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class PhotoOperator {
    private final ContentResolver mContentResolver;

    // firebase collection names
    static final String FIREBASE_PHOTO_COLLECTION = "as2-photos";
    static final String FIREBASE_PHOTO_NAME_COLLECTION = "as2-photos-name";
    // firebase keys of each document in a corresponding collection
    static final String FIREBASE_PHOTO_NAME_COLUMN = "fileName";
    static final String FIREBASE_BASE64_CONTENT_COLUMN = "base64Content";
    /**
     * Extension
     */
    static final String JPG_EXTENSION = ".jpg";
    /**
     * Max downloading firebase jobs at a same time
     */
    static final int DOWNLOAD_MAX_SAME_TIME = 3;

    /**
     * Thread Lock
     */
    static Integer mCurrentDownload = 0;

    /**
     * External Media content URI
     */
    final Uri mUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    public PhotoOperator(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    /**
     * Write bitmap to local file and automatically upload it to firebase
     *
     * @param fileName file name to write
     * @param bitmap bitmap object to write
     */
    public void writeAsync(String fileName, Bitmap bitmap) {
        ThreadPoolManager.getInstance().addCallable(()->{
            Uri imageUri = null;
            PhotoModel.Photo photoToWrite = null;

            // insert to media store
            ContentResolver resolver = mContentResolver;
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg");
            contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, fileName);
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

            Cursor cursor = resolver.query(imageUri, null
                    , null, null, null);
            cursor.moveToNext();
            int idName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);

            String filePath = cursor.getString(dataColumn);
            String name = cursor.getString(idName);

            // get the photo object
            photoToWrite = PhotoModel.getInstance().buildPhoto(name, bitmap, filePath);

            updateGridViewUI();
            cursor.close();

            try {
                OutputStream fos = mContentResolver.openOutputStream(imageUri);
                boolean flag = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                fos.flush();
                fos.close();
                Log.e("Tag", "Write finish!");
            } catch (FileNotFoundException e) {
                Log.e("Tag", "Error1!");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e("Tag", "Error2!");
                e.printStackTrace();
            }

            PhotoModel photoModel = PhotoModel.getInstance();
            photoModel.mPhotos.add(photoToWrite);
            // make sure the latest photo are displayed down
            photoModel.sort();

            // upload this photo to firebase
            uploadPhotoToFirebase(photoToWrite);

            updateGridViewUI();
            updateThumbnailUI();

            // show the capture preview activity
            CommonModel.getInstance().getCameraActivity().
                    invokePreviewCapturedPhotoActivity(photoToWrite.filePath);
            return null;
        });
    }

    /**
     * Read all local files and sync from/to firebase
     */
    public void readAllAsync() {
        ThreadPoolManager.getInstance().addCallable(new Callable() {
            @Override
            public Object call() throws Exception {
                Cursor cursor = mContentResolver.query(
                        mUri, null, null, null,
                        MediaStore.MediaColumns.DATE_ADDED + " DESC");
                PhotoModel.Photo photo = null;
                ArrayList<PhotoModel.Photo> photoArrayList = new ArrayList<>();
                while (cursor.moveToNext()) {
                    //        File file = null;
                    int idName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                    int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);

                    String filePath = cursor.getString(dataColumn);
                    File file = new File(filePath);
                    if (!file.exists()) {
                        continue;
                    }
                    String name = cursor.getString(idName);

                    Bitmap bitmap = BitmapFactory.decodeFile(filePath);
                    photo = PhotoModel.getInstance().buildPhoto(name, bitmap, filePath);
                    photoArrayList.add(photo);
                }
                cursor.close();


                PhotoModel photoModel = PhotoModel.getInstance();
                for (PhotoModel.Photo eachPhoto :
                        photoArrayList) {
                    if (photoModel.contain(eachPhoto.mName) == null) {
                        photoModel.mPhotos.add(eachPhoto);
                    }
                }
                photoModel.sort();
                updateThumbnailUI();
                updateGridViewUI();
                sync();

                return null;
            }
        });
    }

    /**
     * Get the content URI by a specific file path
     * @param filePath file path of the file stored by MediaStore
     */
    public Uri getPhotoUri(String filePath) {
        Cursor cursor = mContentResolver.query(mUri, null, "_data=?",
                new String[]{filePath},
                null);
        if (cursor.moveToNext()) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int id = cursor.getInt(idColumn);
            cursor.close();
            return Uri.withAppendedPath(mUri, "" + id);
        }
        cursor.close();
        return null;
    }

    /**
     * Get the file path by specific content URI
     * @param uri content style URI provided by content resolver
     */
    public String getFilePath(Uri uri) {
        Cursor cursor = mContentResolver.query(uri, null, null, null,
                null);
        if (cursor.moveToNext()) {
            int filePathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
            String filePath = cursor.getString(filePathColumn);
            cursor.close();
            return filePath;
        }
        cursor.close();
        return null;
    }

    /**
     * Upload specific photo to firebase
     * @param photo photo object
     */
    public void uploadPhotoToFirebase(PhotoModel.Photo photo) {
        CommonModel.getInstance().getMainActivity().mCustomThreadPoolManager.addCallable(() -> {
            photo.onCloud = PhotoModel.Photo.CloudStatus.UPLOADING;
            updateGridViewUI();

            // use batch to save band with and make sure the transaction is complete
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            WriteBatch batch = db.batch();

            DocumentReference photoRef = db.collection(FIREBASE_PHOTO_COLLECTION).document();
            Map<String, Object> photoMap = new HashMap<>();
            String fileName = new File(photo.filePath).getName();
            photoMap.put(FIREBASE_PHOTO_NAME_COLUMN, fileName);
            photoMap.put(FIREBASE_BASE64_CONTENT_COLUMN, bitmapToBase64(photo.mBitmap));

            batch.set(photoRef, photoMap);

            DocumentReference photoNameRef = db.collection(FIREBASE_PHOTO_NAME_COLLECTION).document();

            Map<String, Object> photoNameMap = new HashMap<>();
            photoNameMap.put(FIREBASE_PHOTO_NAME_COLUMN, fileName);
            batch.set(photoNameRef, photoNameMap);

            batch.commit().addOnCompleteListener(task -> {
                // ...
                if (task.isSuccessful()) {
                    photo.onCloud = PhotoModel.Photo.CloudStatus.UPLOADED;
                    updateGridViewUI();
                } else {
                    Log.e("TAG",
                            FIREBASE_PHOTO_COLLECTION
                                    + FIREBASE_PHOTO_NAME_COLLECTION
                                    + "DocumentSnapshot failed");
                }
            });
            return null;
        });
    }

    /**
     * Create a blank photo frame with all its information except image data
     * @param fileName file name to create
     */
    PhotoModel.Photo createBlankPhoto(String fileName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg");
        contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, fileName);
        Uri imageUri = mContentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        try {
            OutputStream fos = mContentResolver.openOutputStream(imageUri);
//            Bitmap bitmap = BitmapFactory.decodeResource(
//            CommonModel.getInstance().getMainActivity().getResources(),
//                    R.drawable.blank);
//            boolean flag = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return PhotoModel.getInstance().buildPhoto(
                new File(fileName).getName(), null, getFilePath(imageUri));
    }

    // UI updating functions
    void updateGridViewUI() {
        CommonModel.getInstance().getMainActivity().publishToUiThread(
                MessageUtil.createMessage(MessageUtil.MESSAGE_ID_UPDATE_GRID_VIEW, ""));
    }

    void updateThumbnailUI() {
        CommonModel.getInstance().getMainActivity().publishToUiThread(
                MessageUtil.createMessage(MessageUtil.MESSAGE_ID_UPDATE_THUMBNAIL, ""));
    }

    /**
     * Synchronize from/to the firebase by checking different files each side
     */
    public void sync() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(FIREBASE_PHOTO_NAME_COLLECTION).get().
                addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        ArrayList<String> namesOnCloud = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            namesOnCloud.add((String) document.getData().get(FIREBASE_PHOTO_NAME_COLUMN));
                        }

                        PhotoModel photoModel = PhotoModel.getInstance();
                        // make sure the ealier photo is firstly downloaded
                        // so the grid view will not updated frequently
                        // and the user will quickly see the image
                        Collections.sort(namesOnCloud);

                        int downFromCloud = 0;

                        // Check cloud have local don't have
                        for (String eachNameOnCloud :
                                namesOnCloud) {
                            PhotoModel.Photo photoContainResult = photoModel.contain(eachNameOnCloud);
                            if (photoContainResult == null) {
                                // Create a frame (empty image)
                                PhotoModel.Photo blankPhotoToDownload = createBlankPhoto(eachNameOnCloud);
                                blankPhotoToDownload.onCloud = PhotoModel.Photo.CloudStatus.DOWNLOADING;
                                PhotoModel.getInstance().mPhotos.add(blankPhotoToDownload);
                                // Fill the frame (download image)
                                DownloadTask downloadTask = new DownloadTask(blankPhotoToDownload);
                                downFromCloud++;
                                CommonModel.getInstance().getMainActivity()
                                        .mCustomThreadPoolManager.addCallable(downloadTask);
                            } else {
                                // Cloud have local have
                                if (photoContainResult.onCloud != PhotoModel.Photo.CloudStatus.DOWNLOADING) {
                                    photoContainResult.onCloud = PhotoModel.Photo.CloudStatus.UPLOADED;
                                }
                            }
                        }
                        int upToCloud = 0;
                        //
                        for (PhotoModel.Photo eachPhotoLocal :
                                photoModel.mPhotos) {
                            if (!namesOnCloud.contains(eachPhotoLocal.mName)) {
                                // Local have cloud don't
                                uploadPhotoToFirebase(eachPhotoLocal);
                                upToCloud++;
                            }
                        }
                        updateGridViewUI();
                        int finalDownFromCloud = downFromCloud;
                        int finalUpToCloud = upToCloud;
                        CommonModel.getInstance().getMainActivity().
                                mCustomThreadPoolManager.addCallable(() -> {
                                    boolean breakFlag = true;
                                    while (breakFlag) {
                                        breakFlag = false;
                                        for (PhotoModel.Photo photo : PhotoModel.getInstance().mPhotos) {
                                            if (photo.onCloud != PhotoModel.Photo.CloudStatus.UPLOADED) {
                                                try {
                                                    Thread.sleep(1000);
                                                    breakFlag = true;
                                                    break;
                                                } catch (InterruptedException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    }
                                    CommonModel.getInstance().getMainActivity().
                                            publishToUiThread(MessageUtil.createMessage(
                                                    MessageUtil.MESSAGE_SHOW_SYNC_RESULT
                                                    , "Sync Success! Downloaded "
                                                            + finalDownFromCloud
                                                            + " photos;"
                                                            + " "
                                                            + "Uploaded "
                                                            + finalUpToCloud
                                                            + " photos."));
                                    return true;
                                });
                    } else {
                        Log.d(TAG, "Error getting documents: ", task.getException());
                    }
                });
    }

    // Util functions
    /**
     * Convert a bitmap to a base64 string
     *
     * @param bitmap bitmap object
     * @return base64-encoded string
     */
    public static String bitmapToBase64(Bitmap bitmap) {

        String result = null;
        ByteArrayOutputStream baos = null;
        try {
            if (bitmap != null) {
                baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

                baos.flush();
                baos.close();

                byte[] bitmapBytes = baos.toByteArray();
                result = Base64.encodeToString(bitmapBytes, Base64.DEFAULT);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.flush();
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Convert a base64 string to a bitmap
     *
     * @param base64 base64-encoded string
     * @return bitmap object
     */
    public static Bitmap base64ToBitmap(String base64) {
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        return bitmap;
    }

    /**
     * DownloadTask.java
     * <p>
     * This class implements Callable and is used to download a photo from firebase
     *
     * @author Huashuai Cai
     * @version 1.0
     * @since 2020-10-10
     */
    class DownloadTask implements Callable {
        private PhotoModel.Photo blankPhotoToDownload;

        public DownloadTask(PhotoModel.Photo blankPhotoToDownload) {
            this.blankPhotoToDownload = blankPhotoToDownload;
        }

        @Override
        public Object call() {
            // make sure there is only 3 simultaneous jobs
            synchronized (mCurrentDownload) {
                while (mCurrentDownload >= DOWNLOAD_MAX_SAME_TIME) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            mCurrentDownload++;
            String fileName = blankPhotoToDownload.mName;
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Log.e("DOWNLOAD BEGIN!!!!!" + fileName, "DOWNLOAD BEGIN!!!!!" + fileName);
            db.collection(FIREBASE_PHOTO_COLLECTION).
                    whereEqualTo(FIREBASE_PHOTO_NAME_COLUMN, fileName).limit(1).get().
                    addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            mCurrentDownload--;
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Bitmap bitmap =
                                        base64ToBitmap((String) document
                                                .getData()
                                                .get(FIREBASE_BASE64_CONTENT_COLUMN));
                                String newFileName = (String) document
                                        .getData()
                                        .get(FIREBASE_PHOTO_NAME_COLUMN);

                                assert newFileName.equals(fileName);

                                try {
                                    Uri imageUri = getPhotoUri(blankPhotoToDownload.filePath);
                                    OutputStream fos = mContentResolver.openOutputStream(imageUri);
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                                    fos.flush();
                                    fos.close();

                                } catch (FileNotFoundException e) {
                                    Log.e("Tag", "Error1!");
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    Log.e("Tag", "Error2!");
                                    e.printStackTrace();
                                }

                                PhotoModel photoModel = PhotoModel.getInstance();
                                PhotoModel.Photo photo = photoModel.getPhotoByFileName(
                                        new File(blankPhotoToDownload.filePath).getName());
                                if (photo != null) {
                                    photo.onCloud = PhotoModel.Photo.CloudStatus.UPLOADED;
                                    photo.mBitmap = bitmap;
                                    photoModel.sort();
                                    updateGridViewUI();
                                    updateThumbnailUI();
                                }
                            }
                        } else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    });
            return true;
        }
    }
}
