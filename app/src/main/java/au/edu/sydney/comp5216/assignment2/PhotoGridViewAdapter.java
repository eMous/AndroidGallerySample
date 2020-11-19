package au.edu.sydney.comp5216.assignment2;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.io.File;

/**
 * PhotoGridViewAdapter.java
 * <p>
 * It is the adapter to manage the UI stuff of GridView to support gallery function
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class PhotoGridViewAdapter extends BaseAdapter {

    /**
     * Bound model
     */
    private final PhotoModel mPhotoModel;

    /**
     * Inflater stored to inflate views when in the
     * beginning stage (the cells are not filled the screen)
     */
    private final LayoutInflater mInflater;

    public PhotoGridViewAdapter(Context context, PhotoModel mPhotoModel) {
        this.mPhotoModel = mPhotoModel;
        mInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return mPhotoModel.mPhotos.size();
    }

    @Override
    public long getItemId(int position) {
        return ((PhotoModel.Photo) getItem(position)).mName.hashCode();

    }

    @Override
    public Object getItem(int position) {
        return mPhotoModel.mPhotos.get(position);
    }

    @Override
    /**
     * Set or reset the view of the cell in specific position, when change happens
     * @param position position to render
     * @param convertView current view in that position
     * @param parent ViewGroup
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        PhotoModel.Photo photo = (PhotoModel.Photo) getItem(position);
        SquareImageView imageView;
        ImageView cloudView;
        TextView textView;
        View ret;
        ViewHolder viewHolder;
        if (convertView == null) {
            ret = mInflater.inflate(R.layout.grid_cell, null);
            textView = ret.findViewById(R.id.grid_text);
            imageView = ret.findViewById(R.id.grid_square_view);
            cloudView = ret.findViewById(R.id.cloud_image);
            // set view holder
            ret.setTag(new ViewHolder(position, imageView, textView, cloudView));
        } else {
            ret = convertView;
        }
        // get view holder
        viewHolder = (ViewHolder) ret.getTag();
        viewHolder.mNameText.setText(photo.mName);
        viewHolder.mPosition = position;
        cloudView = viewHolder.mCloudImage;
//        viewHolder.mSquareImageView.setImageBitmap(photo.mBitmap);
        Picasso.get().load(new File(photo.filePath)).into(viewHolder.mSquareImageView);

        // register new view click listener
        ret.setOnClickListener(v -> {
            Intent intent = new Intent(CommonModel.getInstance().getMainActivity(),
                    PreviewCapturedPhotoActivity.class);
            intent.putExtra(PreviewCapturedPhotoActivity.INTENT_PARAM_FILE_PATH, photo.filePath);
            CommonModel.getInstance().getMainActivity().startActivity(intent);
        });

        // display corresponding image to the specific sync status
        switch (photo.onCloud) {
            case OFF_LINE:
                cloudView.setImageResource(R.drawable.ic_baseline_cloud_off_24);
                break;
            case DOWNLOADING:
                cloudView.setImageResource(R.drawable.ic_baseline_cloud_download_24);
                break;
            case UPLOADING:
                cloudView.setImageResource(R.drawable.ic_baseline_cloud_upload_24);
                break;
            case UPLOADED:
                cloudView.setImageResource(R.drawable.ic_baseline_cloud_done_24);
                break;
        }
        return ret;
    }
    /**
     * ViewHolder.java
     * <p>
     * View Holder design pattern to avoid the frequent "findViewById" operation
     *
     * @author Huashuai Cai
     * @version 1.0
     * @since 2020-10-10
     */
    static class ViewHolder {
        final ImageView mCloudImage;
        final TextView mNameText;
        final SquareImageView mSquareImageView;
        int mPosition;

        public ViewHolder(int position, SquareImageView imageView,
                          TextView textView, ImageView cloudImage) {
            mPosition = position;
            mNameText = textView;
            mCloudImage = cloudImage;
            mSquareImageView = imageView;
        }
    }
}
