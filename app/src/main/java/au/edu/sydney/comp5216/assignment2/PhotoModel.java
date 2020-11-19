package au.edu.sydney.comp5216.assignment2;

import android.graphics.Bitmap;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;

/**
 * PhotoModel.java
 * <p>
 * Model object of the Photos
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class PhotoModel extends ViewModel {
    /**
     * Use the singleton design pattern, there is only ONE GALLERY
     */
    private static PhotoModel mInstance = null;

    ArrayList<Photo> mPhotos;

    static {
        mInstance = new PhotoModel();
    }

    public static PhotoModel getInstance() {
        return mInstance;
    }

    private PhotoModel() {
        mPhotos = new ArrayList();
    }

    /**
     * Get the latest taken-date photo
     */
    public Photo getLatestPhoto() {
        if (mPhotos.size() > 0) {
            return mPhotos.get(mPhotos.size() - 1);
        } else {
            return null;
        }
    }

    /**
     * Build a Photo object for others
     *
     * @param name     file name
     * @param bitmap   bitmap object of this photo
     * @param filePath file path of the photo
     */
    public Photo buildPhoto(String name, Bitmap bitmap, String filePath) {
        return new Photo(name, bitmap, filePath);
    }

    /**
     * Find a specific photo in the model by a file name
     *
     * @param name file name
     * @return photo object or null
     */
    public Photo getPhotoByFileName(String name) {
        for (Photo eachPhoto :
                mPhotos) {
            if (eachPhoto.mName.equals(name)) return eachPhoto;
        }
        return null;
    }


    /**
     * Photo.java
     * <p>
     * Simple class of Photo, which contains some basic attributes
     *
     * @author Huashuai Cai
     * @version 1.0
     * @since 2020-10-10
     */
    static class Photo {
        CloudStatus onCloud;
        String filePath;
        String mName;
        Bitmap mBitmap;
        Long mAdded_date;

        enum CloudStatus {
            OFF_LINE,
            DOWNLOADING,
            UPLOADING,
            UPLOADED
        }

        public Photo(String mName, Bitmap mBitmap, String filePath) {
            this.mName = mName;
            this.mBitmap = mBitmap;
            this.filePath = filePath;
            mAdded_date = Long.parseLong(mName.substring(0, mName.length() - PhotoOperator.JPG_EXTENSION.length()));
            onCloud = CloudStatus.OFF_LINE;
        }
    }

    /**
     * sort the mPhotos which is relevant to the GridView Adapter by parsing the file name
     * (from early to late)
     */
    public void sort() {
        Collections.sort(mPhotos,
                (photo1, photo2) -> photo1.mAdded_date.compareTo(photo2.mAdded_date));
    }


    /**
     * Check whether the model has a photo which has a specific file name
     *
     * @param name the file name
     * @return the specific photo or null
     */
    public Photo contain(String name) {
        for (Photo eachPhoto :
                mPhotos) {
            if (eachPhoto.mName.equals(name)) {
                return eachPhoto;
            }
        }
        return null;
    }
}
