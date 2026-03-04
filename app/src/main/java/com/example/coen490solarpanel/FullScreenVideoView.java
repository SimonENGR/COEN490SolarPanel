package com.example.coen490solarpanel;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * A custom VideoView that overrides the default measurement logic
 * to force the video to stretch and fill its layout bounds, ignoring aspect ratio.
 */
public class FullScreenVideoView extends VideoView {

    public FullScreenVideoView(Context context) {
        super(context);
    }

    public FullScreenVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FullScreenVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Ignore the video's actual aspect ratio and force it to fill the measured space
        int width = getDefaultSize(0, widthMeasureSpec);
        int height = getDefaultSize(0, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
}
