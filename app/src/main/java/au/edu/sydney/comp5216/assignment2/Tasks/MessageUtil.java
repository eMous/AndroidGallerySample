package au.edu.sydney.comp5216.assignment2.Tasks;

import android.os.Bundle;
import android.os.Message;

/**
 * MessageUtil.java
 * <p>
 * Message's definition and creation class,
 * used to communicate to UI handler
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class MessageUtil {
    public static final int MESSAGE_ID_UPDATE_GRID_VIEW = 1;
    public static final int MESSAGE_ID_UPDATE_THUMBNAIL = 2;
    public static final int MESSAGE_SHOW_SYNC_RESULT = 3;
    public static final String MESSAGE_BODY = "MESSAGE_BODY";

    public static Message createMessage(int id, String dataString) {
        Bundle bundle = new Bundle();
        bundle.putString(MESSAGE_BODY, dataString);
        Message message = new Message();
        message.what = id;
        message.setData(bundle);

        return message;
    }
}
