package me.yugy.github.library.screenshotlayout;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenShotLayout extends FrameLayout {

    private static final String TAG = ScreenShotLayout.class.getSimpleName();
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final String WRITE_EXTERNAL_STORAGE_PERMISSION =
            "android.permission.WRITE_EXTERNAL_STORAGE";

    private static final int TRIGGER_DISTANCE = 150;    //dp
    private static final int HOVER_COLOR = 0xDD000000;
    private static final int RING_COLOR = 0xDDFFFFFF;
    private static final int SCREENSHOT_COLOR = 0xCCFFFFFF;

    public static ScreenShotLayout attachToActivity(Activity activity) {
        ScreenShotLayout layout = new ScreenShotLayout(activity);
        layout.attach(activity);
        return layout;
    }

    private static final int INVALID = -1;
    private float mInitialDownY = INVALID;
    private int mActivePointIndex = INVALID;
    private int mTouchSlop;
    private int mTriggerDistance;
    private boolean mIsDragging = false;
    private float mProgress = 0f;

    private Paint mRingPaint;
    private RectF mRingRectF;

    private Bitmap mScreenShotBitmap;

    private boolean mIsInScreenShotAnimation = false;
    private boolean mIsInScreenShot = false;
    private int mScreenShotColor;

    public ScreenShotLayout(Context context) {
        this(context, null);
    }

    public ScreenShotLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScreenShotLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        float density = getResources().getDisplayMetrics().density;
        mTriggerDistance = (int) (TRIGGER_DISTANCE * density + 0.5f);

        mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRingPaint.setColor(RING_COLOR);
        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStrokeCap(Paint.Cap.ROUND);
        mRingPaint.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.ring_stroke_size));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            child.layout(l, t, l + child.getMeasuredWidth(), t + child.getMeasuredHeight());
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int ringSize = getResources().getDimensionPixelSize(R.dimen.ring_size);
        int left = (w - ringSize) / 2;
        int top = (h - ringSize) / 2;
        mRingRectF = new RectF(left, top, left + ringSize, top + ringSize);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mIsInScreenShot) return;
        if (mIsInScreenShotAnimation) {
            canvas.drawColor(mScreenShotColor);
        } else {
            int alpha = Color.alpha(HOVER_COLOR);
            int red = Color.red(HOVER_COLOR);
            int green = Color.green(HOVER_COLOR);
            int blue = Color.blue(HOVER_COLOR);
            int hoverColor = Color.argb((int) (alpha * mProgress), red, green, blue);
//        Log.d(TAG, Integer.toHexString(hoverColor));
            canvas.drawColor(hoverColor);
            canvas.drawArc(mRingRectF, 0, 360 * mProgress, false, mRingPaint);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
        if (mIsInScreenShotAnimation) {
            return super.onInterceptTouchEvent(ev);
        }
//        Log.d(TAG, MotionEvent.actionToString(ev.getActionMasked()));
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                int index = ev.getActionIndex();
                if (index == 2) {
                    mActivePointIndex = index;
                    mInitialDownY = ev.getY(mActivePointIndex);
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (mInitialDownY != INVALID && mActivePointIndex != INVALID) {
                    if (mIsDragging) {
                        float y = ev.getY(mActivePointIndex);
                        mProgress = (y - mInitialDownY) / mTriggerDistance;
                        if (mProgress > 1f) mProgress = 1f;
                        if (mProgress < 0f) mProgress = 0f;
                        dispatchProgress();
                    } else {
                        float y = ev.getY(mActivePointIndex);
                        if (y - mInitialDownY >= mTouchSlop) {
                            mIsDragging = true;
                            return true;
                        }
                    }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_POINTER_UP:
                index = ev.getActionIndex();
                if (index == 2) {
                    mIsDragging = false;
                    mInitialDownY = INVALID;
                    mActivePointIndex = INVALID;
                    mProgress = 0f;
                    dispatchProgress();
                }
                return true;
            case MotionEvent.ACTION_UP:
                mIsDragging = false;
                mInitialDownY = INVALID;
                mActivePointIndex = INVALID;
                mProgress = 0f;
                dispatchProgress();
                return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (mIsInScreenShotAnimation) {
            return super.onTouchEvent(event);
        }
//        Log.d(TAG, MotionEvent.actionToString(event.getActionMasked()));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mInitialDownY != INVALID && mActivePointIndex != INVALID) {
                    if (mIsDragging) {
                        float y = event.getY(event.getActionIndex());
                        mProgress = (y - mInitialDownY) / mTriggerDistance;
                        if (mProgress > 1f) mProgress = 1f;
                        if (mProgress < 0f) mProgress = 0f;
                        dispatchProgress();
                    } else {
                        float y = event.getY(event.getActionIndex());
                        if (y - mInitialDownY >= mTouchSlop) {
                            mIsDragging = true;
                            return true;
                        }
                    }
                    return true;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                mIsDragging = false;
                mInitialDownY = INVALID;
                mActivePointIndex = INVALID;

                if (mProgress == 1f) {
                    dispatchFire();
                }

                mProgress = 0f;
                dispatchProgress();
                return false;
        }
        return super.onTouchEvent(event);
    }

    private void dispatchProgress() {
        invalidate();
    }

    private void dispatchFire() {
        mIsInScreenShotAnimation = true;

        makeScreenShot();

        if (mScreenShotBitmap == null) {
            Toast.makeText(getContext(), "screenshot failed", Toast.LENGTH_SHORT).show();
        }

        Animation flashAnimation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, @NonNull Transformation t) {
                int alpha = Color.alpha(SCREENSHOT_COLOR);
                int red = Color.red(SCREENSHOT_COLOR);
                int green = Color.green(SCREENSHOT_COLOR);
                int blue = Color.blue(SCREENSHOT_COLOR);
                mScreenShotColor =
                        Color.argb((int) (alpha * (1 - interpolatedTime)), red, green, blue);
                invalidate();
            }
        };
        flashAnimation.setDuration(200);
        flashAnimation.setInterpolator(new LinearInterpolator());
        flashAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                final String title = getContext().getPackageName() + "_" + DATE_FORMAT.format(new Date());
                boolean allowWriteExternal = checkPermission(WRITE_EXTERNAL_STORAGE_PERMISSION);
                if (allowWriteExternal) {
                    String screenshotSavePath = MediaStore.Images.Media.insertImage(
                            getContext().getContentResolver(), mScreenShotBitmap, title, "screenshot");
                    if (screenshotSavePath == null) {
                        Toast.makeText(
                                getContext(), "save screenshot failed.", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, screenshotSavePath);

                        //noinspection StringBufferReplaceableByString
                        StringBuilder contentBuilder = new StringBuilder();
                        contentBuilder
                                .append("From Context: ").append(getContext().toString()).append("\n")
                                .append("Device: ").append(Build.DEVICE).append("\n")
                                .append("Brand: ").append(Build.BRAND).append("\n")
                                .append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
                                .append("Api Level: ").append(Build.VERSION.SDK_INT).append("\n")
                                .append("\n")
                                .append("Question Description: ");

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("application/image");
                        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"me@yanghui.name"});
                        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
                        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, contentBuilder.toString());
                        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(screenshotSavePath));
                        getContext().startActivity(Intent.createChooser(emailIntent, "Send mail..."));
                    }
                } else {
                    Toast.makeText(getContext(),
                            "Permission Denial: requires android.permission.WRITE_EXTERNAL_STORAGE",
                            Toast.LENGTH_SHORT).show();
                }
                mIsInScreenShotAnimation = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        startAnimation(flashAnimation);
    }

    private void attach(Activity activity) {
        if (getParent() != null) {
            throw new IllegalStateException("This layout has been added into a ViewGroup.");
        }

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ViewGroup decorChild = (ViewGroup) decor.getChildAt(0);

        decor.removeView(decorChild);
        addView(decorChild);
        decor.addView(this);
    }

    private void makeScreenShot() {
        mIsInScreenShot = true;
        Window window = ((Activity) getContext()).getWindow();
        Drawable background = window.getDecorView().getBackground();
        mScreenShotBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        mScreenShotBitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(mScreenShotBitmap);
        canvas.translate(-getScrollX(), -getScrollY());
        background.draw(canvas);
        draw(canvas);
        mIsInScreenShot = false;
    }

    private boolean checkPermission(String permission)
    {
        int res = getContext().checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }
}
