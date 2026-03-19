package com.example.coen490solarpanel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Quarter-circle arc slider for panel tilt angle (0° to 90°).
 *
 * Convention: 0° = VERTICAL (panel upright), 90° = HORIZONTAL (panel flat).
 *
 * Visual layout:
 *   - Pivot at bottom-left
 *   - 0° at the BOTTOM of the arc (right side)
 *   - 90° at the TOP of the arc
 *   - Arc fills upward from 0° as you drag toward 90°
 */
public class ArcSliderView extends View {

    private Paint arcBackgroundPaint;
    private Paint arcActivePaint;
    private Paint thumbPaint;
    private Paint thumbInnerPaint;
    private Paint tickPaint;
    private Paint tickLabelPaint;
    private Paint angleLabelPaint;
    private Paint panelPaint;
    private Paint basePaint;

    private float centerX, centerY;
    private float radius;
    private float currentAngle = 0f; // 0–90

    private OnAngleChangedListener listener;
    private boolean isDragging = false;

    public interface OnAngleChangedListener {
        void onAngleChanged(float angle);
        void onAngleFinalized(float angle);
    }

    public ArcSliderView(Context context) { super(context); init(); }
    public ArcSliderView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ArcSliderView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        arcBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcBackgroundPaint.setStyle(Paint.Style.STROKE);
        arcBackgroundPaint.setStrokeWidth(16f);
        arcBackgroundPaint.setColor(0xFFDDDDDD);
        arcBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        arcActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcActivePaint.setStyle(Paint.Style.STROKE);
        arcActivePaint.setStrokeWidth(16f);
        arcActivePaint.setColor(0xFF2196F3);
        arcActivePaint.setStrokeCap(Paint.Cap.ROUND);

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(0xFF1976D2);

        thumbInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbInnerPaint.setStyle(Paint.Style.FILL);
        thumbInnerPaint.setColor(Color.WHITE);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(2f);
        tickPaint.setColor(0xFF999999);

        tickLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickLabelPaint.setTextSize(28f);
        tickLabelPaint.setColor(0xFF666666);
        tickLabelPaint.setTextAlign(Paint.Align.CENTER);

        angleLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        angleLabelPaint.setTextSize(48f);
        angleLabelPaint.setColor(0xFF1976D2);
        angleLabelPaint.setTextAlign(Paint.Align.CENTER);
        angleLabelPaint.setFakeBoldText(true);

        panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelPaint.setStyle(Paint.Style.STROKE);
        panelPaint.setStrokeWidth(6f);
        panelPaint.setColor(0xFF4CAF50);
        panelPaint.setStrokeCap(Paint.Cap.ROUND);

        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(4f);
        basePaint.setColor(0xFF888888);
        basePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, (int) (size * 0.85f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = 60f;
        radius = Math.min(getWidth(), getHeight()) - padding * 2;

        // Pivot at bottom-left
        centerX = padding;
        centerY = getHeight() - padding + 20;

        android.graphics.RectF arcRect = new android.graphics.RectF(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);

        // Ground line (horizontal from pivot to the right)
        canvas.drawLine(centerX, centerY, centerX + radius + 30, centerY, basePaint);

        // ---------------------------------------------------------------
        // ARC:  0° is at the RIGHT (canvas 0°), 90° is at the TOP (canvas 270°)
        // Background: draw the full quarter from right (0°) counter-clockwise to top (270°)
        //   In Android drawArc, counter-clockwise = negative sweep
        // ---------------------------------------------------------------
        canvas.drawArc(arcRect, 0f, -90f, false, arcBackgroundPaint);

        // Active arc: fills from the BOTTOM (right / canvas 0°) UPWARD
        // Sweep counter-clockwise by currentAngle degrees
        if (currentAngle > 0) {
            canvas.drawArc(arcRect, 0f, -currentAngle, false, arcActivePaint);
        }

        // ---------------------------------------------------------------
        // TICK MARKS every 15°
        // Our 0° = canvas 0° (right), our 90° = canvas 270° (up)
        // Canvas angle for our deg = -deg  (or equivalently 360-deg)
        // ---------------------------------------------------------------
        for (int deg = 0; deg <= 90; deg += 15) {
            float canvasRad = (float) Math.toRadians(-deg);
            float innerR = radius - 25f;
            float outerR = radius + 25f;
            float x1 = centerX + innerR * (float) Math.cos(canvasRad);
            float y1 = centerY + innerR * (float) Math.sin(canvasRad);
            float x2 = centerX + outerR * (float) Math.cos(canvasRad);
            float y2 = centerY + outerR * (float) Math.sin(canvasRad);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);

            float labelR = radius + 45f;
            float lx = centerX + labelR * (float) Math.cos(canvasRad);
            float ly = centerY + labelR * (float) Math.sin(canvasRad) + 10f;
            canvas.drawText(deg + "°", lx, ly, tickLabelPaint);
        }

        // ---------------------------------------------------------------
        // THUMB at current angle
        // ---------------------------------------------------------------
        float thumbRad = (float) Math.toRadians(-currentAngle);
        float thumbX = centerX + radius * (float) Math.cos(thumbRad);
        float thumbY = centerY + radius * (float) Math.sin(thumbRad);

        canvas.drawCircle(thumbX, thumbY, isDragging ? 28f : 22f, thumbPaint);
        canvas.drawCircle(thumbX, thumbY, isDragging ? 16f : 12f, thumbInnerPaint);

        // ---------------------------------------------------------------
        // PANEL INDICATOR LINE from pivot
        // ---------------------------------------------------------------
        float panelLen = radius * 0.65f;
        float panelEndX = centerX + panelLen * (float) Math.cos(thumbRad);
        float panelEndY = centerY + panelLen * (float) Math.sin(thumbRad);
        canvas.drawLine(centerX, centerY, panelEndX, panelEndY, panelPaint);
        canvas.drawCircle(centerX, centerY, 8f, panelPaint);

        // ---------------------------------------------------------------
        // ANGLE READOUT
        // ---------------------------------------------------------------
        String angleText = String.format("%.1f°", currentAngle);
        float textX = centerX + radius * 0.55f;
        float textY = centerY - radius * 0.55f;
        canvas.drawText(angleText, textX, textY, angleLabelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                updateAngleFromTouch(x, y);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (isDragging) updateAngleFromTouch(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    updateAngleFromTouch(x, y);
                    if (listener != null) listener.onAngleFinalized(currentAngle);
                    getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateAngleFromTouch(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;

        // atan2: right=0°, up=-90°
        double rawDeg = Math.toDegrees(Math.atan2(dy, dx));

        // Our mapping: canvas 0° (right) = our 0°, canvas -90° (up) = our 90°
        // So:  ourAngle = -rawDeg,  clamped 0..90
        double mapped = -rawDeg;

        currentAngle = (float) Math.max(0, Math.min(90, mapped));
        currentAngle = Math.round(currentAngle * 2f) / 2f;

        invalidate();
        if (listener != null) listener.onAngleChanged(currentAngle);
    }

    public void setAngle(float angle) {
        this.currentAngle = Math.max(0, Math.min(90, angle));
        invalidate();
    }

    public float getAngle() { return currentAngle; }
    public boolean isDragging() { return isDragging; }

    public void setOnAngleChangedListener(OnAngleChangedListener listener) {
        this.listener = listener;
    }
}
