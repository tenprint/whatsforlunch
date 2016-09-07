package com.lipata.whatsforlunch.ui;

import android.content.Context;
import android.widget.ImageView;

import com.lipata.whatsforlunch.R;

/**
 * Created by jlipata on 9/6/16.
 */
public class LocationQualityView {
    // Status codes
    public static final int HIDE = 0;
    public static final int BEST = 10;
    public static final int OK = 20;
    public static final int BAD = 30;

    ImageView mImageView;
    Context context;

    public LocationQualityView(Context context, ImageView imageView) {
        this.mImageView = imageView;
        this.context = context;
    }

    public void setAccuracyCircleStatus(int statusCode){
        switch (statusCode){
            case HIDE:
                mImageView.setColorFilter(context.getResources().getColor(R.color.material_gray_100));
                break;
            case BEST:
                mImageView.setColorFilter(context.getResources().getColor(R.color.accuracy_BEST));
                break;
            case OK:
                mImageView.setColorFilter(context.getResources().getColor(R.color.accuracy_OK));
                break;
            case BAD:
                mImageView.setColorFilter(context.getResources().getColor(R.color.accuracy_BAD));
                break;
        }
    }
}


