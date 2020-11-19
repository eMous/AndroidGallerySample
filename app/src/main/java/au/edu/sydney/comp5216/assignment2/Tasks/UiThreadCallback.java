package au.edu.sydney.comp5216.assignment2.Tasks;

import android.os.Message;

/**
 * UiThreadCallback.java
 * <p>
 * This interface is used to communicate with UI thread
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public interface UiThreadCallback {
    void publishToUiThread(Message message);
}
