package au.edu.sydney.comp5216.assignment2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;


import com.squareup.picasso.Picasso;

import java.io.File;

/**
 * PreviewCapturedPhotoActivity.java
 * <p>
 * Activity to display the captured photo
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class PreviewCapturedPhotoActivity extends Activity {
    static final String INTENT_PARAM_FILE_PATH  = "filePath";
    static final String INTENT_PARAM_RETURN_TO_MAIN  = "returnToMain";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview_captured_photo_activity);
        Intent intent = getIntent();
        String filePath = intent.getStringExtra(INTENT_PARAM_FILE_PATH);
        ImageView imageView = findViewById(R.id.captured_photo_imageView);
        imageView.setOnClickListener(v->{
            Intent resultIntent = new Intent();
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
        Picasso.get().load(new File(filePath)).into(imageView);


        Button backToMainBtn = findViewById(R.id.back_to_grid_view_btn);
        backToMainBtn.setOnClickListener(v->{
            Intent resultIntent = new Intent();
            resultIntent.putExtra(INTENT_PARAM_RETURN_TO_MAIN, true);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });

    }
}
