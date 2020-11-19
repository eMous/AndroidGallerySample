package au.edu.sydney.comp5216.assignment2;

/**
 * CommonModel.java
 * <p>
 * This is a singleton class which stores the Activity objects,
 * when there is a need in other activity, these activities can be easily found
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class CommonModel {
    private static CommonModel singletonObject;

    private MainActivity mMainActivity;
    private CameraActivity mCameraActivity;

    static {
        singletonObject = new CommonModel();
    }

    public static CommonModel getInstance() {
        return singletonObject;
    }

    public MainActivity getMainActivity() {
        return mMainActivity;
    }

    public void setMainActivity(MainActivity mainActivity) {
        this.mMainActivity = mainActivity;
    }

    public CameraActivity getCameraActivity() {
        return mCameraActivity;
    }

    public void setCameraActivity(CameraActivity mCameraActivity) {
        this.mCameraActivity = mCameraActivity;
    }
}
