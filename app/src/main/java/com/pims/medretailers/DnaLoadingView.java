package com.pims.medretailers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class DnaLoadingView extends View {
    private Paint paint1, paint2;
    private float phase = 0;
    private static final int DOT_COUNT = 10; // More dots for a fuller strand
    private static final float SPEED = 0.12f; // Slightly slower for better visibility

    public DnaLoadingView(Context context) {
        super(context);
        init();
    }

    public DnaLoadingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint1.setColor(Color.parseColor("#835C9F")); // Your primary purple

        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setColor(Color.parseColor("#B19CD9")); // Lighter purple for depth
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2f;
        float radius = height / 2.2f; // Wider helix
        float dotRadius = 16f; // Larger dots

        for (int i = 0; i < DOT_COUNT; i++) {
            float x = (width / (float) (DOT_COUNT + 1)) * (i + 1);
            float offset = (float) (i * Math.PI / 3f);

            // Calculate z-index equivalent using sine for depth (scaling and alpha)
            float sinVal1 = (float) Math.sin(phase + offset);
            float cosVal1 = (float) Math.cos(phase + offset);

            // Sine wave 1 (Foreground/Background)
            float y1 = centerY + cosVal1 * radius;
            float scale1 = (sinVal1 + 2.0f) / 2.5f; // Oscillate size
            int alpha1 = (int) ((sinVal1 + 2.0f) / 3.0f * 255); // Oscillate transparency
            paint1.setAlpha(alpha1);
            canvas.drawCircle(x, y1, dotRadius * scale1, paint1);

            // Sine wave 2 (180 degrees out of phase)
            float sinVal2 = (float) Math.sin(phase + offset + Math.PI);
            float cosVal2 = (float) Math.cos(phase + offset + Math.PI);
            float y2 = centerY + cosVal2 * radius;
            float scale2 = (sinVal2 + 2.0f) / 2.5f;
            int alpha2 = (int) ((sinVal2 + 2.0f) / 3.0f * 255);
            paint2.setAlpha(alpha2);
            canvas.drawCircle(x, y2, dotRadius * scale2, paint2);
        }

        phase += SPEED;
        invalidate();
    }
}
