package hh.screenseek.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Full-screen selection surface used to choose the region sent to Gemini.
 *
 * The view owns rendering and touch interaction only. ScreenCaptureService
 * performs the actual capture after the user confirms the selection.
 */
public class SnipOverlayView extends View {

    public interface OnSnipListener {
        void onConfirmed(RectF rect);
        void onCancelled();
    }

    private static final int MODE_NONE = 0;
    private static final int MODE_CREATE = 1;
    private static final int MODE_DRAG = 2;

    private int currentMode = MODE_NONE;

    private Paint dimPaint;
    private Paint clearPaint;
    private Paint borderPaint;
    private Paint handlePaint;
    private Paint btnPaint;
    private Paint textPaint;

    private RectF selectionRect = new RectF();
    private boolean isBoxDrawn = false;

    private float startX, startY;
    private float lastTouchX, lastTouchY;

    private RectF searchBtnRect = new RectF();
    private RectF cancelBtnRect = new RectF();

    private OnSnipListener listener;

    public SnipOverlayView(Context context, OnSnipListener listener) {
        super(context);
        this.listener = listener;
        init();
    }

    private void init() {
        // Software rendering is required for the CLEAR blend mode used by the selection window.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        dimPaint = new Paint();
        dimPaint.setColor(Color.parseColor("#990B0B0E"));

        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#60A5FA"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f);

        handlePaint = new Paint();
        handlePaint.setColor(Color.parseColor("#60A5FA"));
        handlePaint.setStyle(Paint.Style.FILL);

        btnPaint = new Paint();
        btnPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        if (selectionRect.width() > 10 && selectionRect.height() > 10) {
            // Reveal the original screen beneath the dimmed overlay.
            canvas.drawRect(selectionRect, clearPaint);
            canvas.drawRect(selectionRect, borderPaint);

            float handleSize = 7f;
            canvas.drawCircle(selectionRect.left, selectionRect.top, handleSize, handlePaint);
            canvas.drawCircle(selectionRect.right, selectionRect.top, handleSize, handlePaint);
            canvas.drawCircle(selectionRect.left, selectionRect.bottom, handleSize, handlePaint);
            canvas.drawCircle(selectionRect.right, selectionRect.bottom, handleSize, handlePaint);

            if (isBoxDrawn) {
                drawActionButtons(canvas);
            }
        }
    }

    private void drawActionButtons(Canvas canvas) {
        float btnWidth = 140f;
        float btnHeight = 52f;
        float gap = 12f;

        // Keep the action buttons near the selection while avoiding screen edges.
        float top = selectionRect.bottom + 16f;
        if (top + btnHeight > getHeight() - 30) {
            top = selectionRect.top - btnHeight - 16f;
        }

        float left = selectionRect.centerX() - (btnWidth + gap / 2f);
        if (left < 16) left = 16;
        if (left + (btnWidth * 2) + gap > getWidth() - 16) {
            left = getWidth() - (btnWidth * 2) - gap - 16;
        }

        searchBtnRect.set(left, top, left + btnWidth, top + btnHeight);
        btnPaint.setColor(Color.parseColor("#2563EB"));
        canvas.drawRoundRect(searchBtnRect, 14f, 14f, btnPaint);
        canvas.drawText("Ask AI", searchBtnRect.centerX(), searchBtnRect.centerY() + 8f, textPaint);

        cancelBtnRect.set(left + btnWidth + gap, top, left + (btnWidth * 2) + gap, top + btnHeight);
        btnPaint.setColor(Color.parseColor("#27272F"));
        canvas.drawRoundRect(cancelBtnRect, 14f, 14f, btnPaint);
        canvas.drawText("Cancel", cancelBtnRect.centerX(), cancelBtnRect.centerY() + 8f, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                
                if (isBoxDrawn) {
                    if (searchBtnRect.contains(x, y)) {
                        if (listener != null) listener.onConfirmed(selectionRect);
                        return true;
                    }
                    if (cancelBtnRect.contains(x, y)) {
                        if (listener != null) listener.onCancelled();
                        return true;
                    }

                    
                    if (selectionRect.contains(x, y)) {
                        currentMode = MODE_DRAG;
                        lastTouchX = x;
                        lastTouchY = y;
                        invalidate();
                        return true;
                    }
                }

                
                currentMode = MODE_CREATE;
                isBoxDrawn = false;
                startX = x;
                startY = y;
                selectionRect.set(x, y, x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (currentMode == MODE_DRAG) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;

                    
                    // Clamp movement so the selection never leaves the overlay bounds.
                    if (selectionRect.left + dx < 0) dx = -selectionRect.left;
                    if (selectionRect.right + dx > getWidth()) dx = getWidth() - selectionRect.right;
                    if (selectionRect.top + dy < 0) dy = -selectionRect.top;
                    if (selectionRect.bottom + dy > getHeight()) dy = getHeight() - selectionRect.bottom;

                    selectionRect.offset(dx, dy);
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                    return true;
                } else if (currentMode == MODE_CREATE) {
                    selectionRect.set(
                            Math.min(startX, x),
                            Math.min(startY, y),
                            Math.max(startX, x),
                            Math.max(startY, y)
                    );
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currentMode == MODE_CREATE) {
                    if (selectionRect.width() > 30 && selectionRect.height() > 30) {
                        isBoxDrawn = true;
                    } else {
                        selectionRect.setEmpty();
                        isBoxDrawn = false;
                    }
                } else if (currentMode == MODE_DRAG) {
                    isBoxDrawn = true;
                }
                currentMode = MODE_NONE;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }
}