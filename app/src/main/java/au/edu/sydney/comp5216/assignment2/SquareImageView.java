package au.edu.sydney.comp5216.assignment2;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;

/**
 * SquareImageView.java
 * <p>
 * Customized ImageView to fit the cell in GridView
 *
 * @author Huashuai Cai
 * @version 1.0
 * @since 2020-10-10
 */
public class SquareImageView extends androidx.appcompat.widget.AppCompatImageView {


    public SquareImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public SquareImageView(Context context, AttributeSet attributeSet, int defStyle) {
        super(context, attributeSet, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // make sure it display like as a square
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
        } else {
            setMeasuredDimension(getMeasuredHeight(), getMeasuredHeight());
        }
        setAdjustViewBounds(true);
        setScaleType(ScaleType.FIT_XY);
    }
}
