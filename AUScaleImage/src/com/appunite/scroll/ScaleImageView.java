/*
 * Copyright 2013 Jacek Marchwicki <jacek.marchwicki@gmail.com>
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appunite.scroll;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.OverScroller;

/**
 * A view representing a simple yet interactive line chart for the function <code>x^3 - x/4</code>.
 * <p>
 * This view isn't all that useful on its own; rather it serves as an example of how to correctly
 * implement these types of gestures to perform zooming and scrolling with interesting content
 * types.
 * <p>
 * The view is interactive in that it can be zoomed and panned using
 * typical <a href="http://developer.android.com/design/patterns/gestures.html">gestures</a> such
 * as double-touch, drag, pinch-open, and pinch-close. This is done using the
 * {@link android.view.ScaleGestureDetector}, {@link android.view.GestureDetector}, and {@link android.widget.OverScroller} classes. Note
 * that the platform-provided view scrolling behavior (e.g. {@link android.view.View#scrollBy(int, int)} is NOT
 * used.
 * <p>
 * The view also demonstrates the correct use of
 * <a href="http://developer.android.com/design/style/touch-feedback.html">touch feedback</a> to
 * indicate to users that they've reached the content edges after a pan or fling gesture. This
 * is done using the {@link android.support.v4.widget.EdgeEffectCompat} class.
 * <p>
 * Finally, this class demonstrates the basics of creating a custom view, including support for
 * custom attributes (see the constructors), a simple implementation for
 * {@link #onMeasure(int, int)}, an implementation for {@link #onSaveInstanceState()} and a fairly
 * straightforward {@link android.graphics.Canvas}-based rendering implementation in
 * {@link #onDraw(android.graphics.Canvas)}.
 * <p>
 * Note that this view doesn't automatically support directional navigation or other accessibility
 * methods. Activities using this view should generally provide alternate navigation controls.
 * Activities using this view should also present an alternate, text-based representation of this
 * view's content for vision-impaired users.
 */
public class ScaleImageView extends View {
    private static final String TAG = "InteractiveLineGraphView";

    /**
     * Initial fling velocity for pan operations, in screen widths (or heights) per second.
     *
     * @see #panLeft()
     * @see #panRight()
     * @see #panUp()
     * @see #panDown()
     */
    private static final float PAN_VELOCITY_FACTOR = 2f;

    /**
     * The scaling factor for a single zoom 'step'.
     *
     * @see #zoomIn()
     * @see #zoomOut()
     */
    private static final float ZOOM_AMOUNT = 0.5f;

    /**
     * The current destination rectangle (in pixel coordinates) into which the chart data should
     * be drawn. Chart labels are drawn outside this area.
     *
     */
    private Rect mContentRect = new Rect();


    private float mScale = 0.5f;
    private PointF mTranslation = new PointF(0.5f, 0.5f);
    private PointF mRealTranslation = new PointF();

    // Values
    private boolean mAllowParentHorizontalScroll = false;
    private boolean mAllowParentVerticalScroll = false;

    // Current attribute values and Paints.
    private final int mMinWidth;
    private final int mMinHeight;

    // State objects and values related to gesture tracking.
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetectorCompat mGestureDetector;
    private OverScroller mScroller;
    private Zoomer mZoomer;
    private PointF mZoomFocalPoint = new PointF();

    // Edge effect / overscroll tracking objects.
    private EdgeEffectCompat mEdgeEffectTop;
    private EdgeEffectCompat mEdgeEffectBottom;
    private EdgeEffectCompat mEdgeEffectLeft;
    private EdgeEffectCompat mEdgeEffectRight;

    private boolean mEdgeEffectTopActive;
    private boolean mEdgeEffectBottomActive;
    private boolean mEdgeEffectLeftActive;
    private boolean mEdgeEffectRightActive;

    private Point mMaxScrollBuffer = new Point();
    private Drawable mSrc;
    private Rect mSrcRect = new Rect();
    private float mMinScale;

    private RectF mRectF = new RectF();
    private float mZoomStartScale;
    private float mMinEdge;

    @SuppressWarnings("UnusedDeclaration")
    public ScaleImageView(Context context) {
        this(context, null, 0);
    }

    @SuppressWarnings("UnusedDeclaration")
    public ScaleImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("UnusedDeclaration")
    public ScaleImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mMinEdge = viewConfiguration.getScaledEdgeSlop();

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.ScaleImageView, defStyle, defStyle);
        assert a != null;

        Drawable src = null;
        try {
            mMinWidth = a.getDimensionPixelSize(R.styleable.ScaleImageView_android_minWidth, 0);
            mMinHeight = a.getDimensionPixelSize(R.styleable.ScaleImageView_android_minHeight, 0);
            src = a.getDrawable(R.styleable.ScaleImageView_android_src);
        } finally {
            a.recycle();
        }

        ScaleGestureDetector.OnScaleGestureListener scaleGestureListener =
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            /**
             * This is the active focal point in terms of the viewport. Could be a local
             * variable but kept here to minimize per-frame allocations.
             */
            private PointF viewportFocus = new PointF();

            @Override
            public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
                final ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                float focusX = scaleGestureDetector.getFocusX();
                float focusY = scaleGestureDetector.getFocusY();
                float scaleFactor = scaleGestureDetector.getScaleFactor();
                float previousScale = mScale;
                mScale *= scaleFactor;

                doScale(focusX, focusY, scaleFactor);
                return true;
            }

        };
        GestureDetector.SimpleOnGestureListener gestureListener =
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {

                releaseEdgeEffects();
                mScroller.forceFinished(true);
                ViewCompat.postInvalidateOnAnimation(ScaleImageView.this);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mZoomer.forceFinished(true);
                mZoomStartScale = mScale;
                mZoomFocalPoint.set(e.getX(), e.getY());
                mZoomer.startZoom(ZOOM_AMOUNT);
                ViewCompat.postInvalidateOnAnimation(ScaleImageView.this);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                    float distanceX, float distanceY) {

                getRealTranslation(mTranslation, mRealTranslation);
                mRealTranslation.offset(-distanceX, -distanceY);
                getTranslation(mRealTranslation, mTranslation);

                computeMaxScrollSize(mMaxScrollBuffer);
                getImageRect(mRectF);
                float scrolledX = -mRectF.left;
                float scrolledY = -mRectF.top;
                boolean canScrollX = mRectF.left > mContentRect.left ||
                        mRectF.right < mContentRect.right;
                boolean canScrollY = mRectF.top > mContentRect.top ||
                        mRectF.bottom < mContentRect.bottom;
                validateTranslation();

                disallowParentInterceptWhenOnEdge(distanceX, distanceY);

                if (mScale > mMinScale) {
                    if (canScrollX && scrolledX < 0) {
                        mEdgeEffectLeft.onPull(scrolledX / (float) mContentRect.width());
                        mEdgeEffectLeftActive = true;
                    }
                    if (canScrollY && scrolledY < 0) {
                        mEdgeEffectTop.onPull(scrolledY / (float) mContentRect.height());
                        mEdgeEffectTopActive = true;
                    }
                    if (canScrollX && scrolledX > mMaxScrollBuffer.x) {
                        mEdgeEffectRight.onPull((scrolledX - mMaxScrollBuffer.x)
                                / (float) mContentRect.width());
                        mEdgeEffectRightActive = true;
                    }
                    if (canScrollY && scrolledY > mMaxScrollBuffer.y) {
                        mEdgeEffectBottom.onPull((scrolledY - mMaxScrollBuffer.y)
                                / (float) mContentRect.height());
                        mEdgeEffectBottomActive = true;
                    }
                }

                ViewCompat.postInvalidateOnAnimation(ScaleImageView.this);
                return true;
            }

            private void disallowParentInterceptWhenOnEdge(float directionX, float directionY) {
                final ViewParent parent = getParent();
                if (parent != null &&
                        (mAllowParentHorizontalScroll || mAllowParentVerticalScroll)) {
                    getImageRect(mRectF);
                    if (mAllowParentHorizontalScroll) {
                        if (directionX > 0 &&
                                Math.abs(mRectF.right - mContentRect.right) > mMinEdge) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        if (directionX < 0 &&
                                Math.abs(mRectF.left - mContentRect.left) > mMinEdge) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    if (mAllowParentVerticalScroll) {
                        if (directionY > 0 &&
                                Math.abs(mRectF.bottom - mContentRect.bottom) > mMinEdge) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        if (directionY < 0 &&
                                Math.abs(mRectF.top - mContentRect.top) > mMinEdge) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                disallowParentInterceptWhenOnEdge(velocityX, velocityY);
                fling((int) -velocityX, (int) -velocityY);
                return true;
            }
        };

        mScaleGestureDetector = new ScaleGestureDetector(context, scaleGestureListener);
        mGestureDetector = new GestureDetectorCompat(context, gestureListener);

        mScroller = new OverScroller(context);
        mZoomer = new Zoomer(context);

        // Sets up edge effects
        mEdgeEffectLeft = new EdgeEffectCompat(context);
        mEdgeEffectTop = new EdgeEffectCompat(context);
        mEdgeEffectRight = new EdgeEffectCompat(context);
        mEdgeEffectBottom = new EdgeEffectCompat(context);

        setSrcDrawable(src);
    }

    private void doScale(float focusX, float focusY, float scaleFactor) {
        getRealTranslation(mTranslation, mRealTranslation);
        mRealTranslation.offset(-focusX, -focusY);
        mRealTranslation.x *= scaleFactor;
        mRealTranslation.y *= scaleFactor;
        mRealTranslation.offset(focusX, focusY);
        getTranslation(mRealTranslation, mTranslation);

        validateScale();
        validateTranslation();

        ViewCompat.postInvalidateOnAnimation(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mContentRect.set(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        if (w != oldw || h != oldh) {
            setupImage();
            validateScale();
            validateTranslation();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                Math.max(getSuggestedMinimumWidth(),
                        resolveSize(mMinWidth + getPaddingLeft() + getPaddingRight(),
                                widthMeasureSpec)),
                Math.max(getSuggestedMinimumHeight(),
                        resolveSize(mMinHeight + getPaddingTop() + getPaddingBottom(),
                                heightMeasureSpec)));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and objects related to drawing
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupImage() {
        final int intrinsicWidth2 = mSrc.getIntrinsicWidth() / 2;
        final int intrinsicHeight2 = mSrc.getIntrinsicHeight() / 2;
        mSrcRect.set(-intrinsicWidth2, -intrinsicHeight2, intrinsicWidth2, intrinsicHeight2);
        mSrc.setBounds(mSrcRect);
        mTranslation.set(0.5f, 0.5f);

        final float scaleWidth = (float)mContentRect.width() / mSrcRect.width();
        final float scaleHeight = (float)mContentRect.height() / mSrcRect.height();
        mMinScale = scaleWidth > scaleHeight ? scaleHeight : scaleWidth;
    }

    private void getImageRect(RectF rect) {
        rect.set(mSrcRect);
        final float width2 = Math.max(mSrcRect.width() * mScale, mContentRect.width()) / 2.0f;
        final float height2 = Math.max(mSrcRect.height() * mScale, mContentRect.height()) / 2.0f;
        rect.set(-width2, -height2, width2, height2);
        getRealTranslation(mTranslation, mRealTranslation);
        rect.offset(mRealTranslation.x, mRealTranslation.y);
    }

    private void setTranslationFromScroll(float scrollX, float scrollY) {
        float centerX = mScale * mSrcRect.width() / 2.0f - scrollX;
        float centerY = mScale * mSrcRect.height() / 2.0f - scrollY;
        mRealTranslation.set(centerX, centerY);
        getTranslation(mRealTranslation, mTranslation);
    }

    private boolean validateScale() {
        if (mScale < mMinScale) {
            mScale = mMinScale;
            return false;
        }
        return true;
    }

    private boolean validateTranslation() {
        getImageRect(mRectF);
        boolean valid = true;
        if (mRectF.left > mContentRect.left) {
            final float width = mRectF.width();
            mRectF.left = mContentRect.left;
            mRectF.right = mContentRect.left + width;
            valid = false;
        } else if (mRectF.right < mContentRect.right) {
            final float width = mRectF.width();
            mRectF.right = mContentRect.right;
            mRectF.left = mContentRect.right - width;
            valid = false;
        }

        if (mRectF.top > mContentRect.top) {
            final float height = mRectF.height();
            mRectF.top = mContentRect.top;
            mRectF.bottom = mContentRect.top + height;
            valid = false;
        } else if (mRectF.bottom < mContentRect.bottom) {
            final float height = mRectF.height();
            mRectF.bottom = mContentRect.bottom;
            mRectF.top = mContentRect.bottom - height;
            valid = false;
        }
        mRealTranslation.set(mRectF.centerX(), mRectF.centerY());
        getTranslation(mRealTranslation, mTranslation);
        return valid;
    }

    private void getRealTranslation(PointF translation, PointF outRealTranslation) {
        outRealTranslation.set(mContentRect.width() * translation.x,
                mContentRect.height() * translation.y);
    }

    private void getTranslation(PointF realTranslation, PointF outTranslation) {
        outTranslation.set(realTranslation.x / mContentRect.width(),
                realTranslation.y / mContentRect.height());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Clips the next few drawing operations to the content area
        int clipRestoreCount = canvas.save();
        canvas.clipRect(mContentRect);


        if (mSrc != null) {
            getRealTranslation(mTranslation, mRealTranslation);
            canvas.translate(mRealTranslation.x, mRealTranslation.y);
            canvas.scale(mScale, mScale);
            Log.v(TAG, "scale=" + mScale +
                    " translation="+mTranslation.toString() +
                    " realTranslation=" + mRealTranslation.toString());
            mSrc.draw(canvas);
        }

        // Removes clipping rectangle
        canvas.restoreToCount(clipRestoreCount);

        drawEdgeEffectsUnclipped(canvas);

    }

    /**
     * Draws the overscroll "glow" at the four edges of the chart region, if necessary. The edges
     * of the chart region are stored in {@link #mContentRect}.
     *
     * @see android.support.v4.widget.EdgeEffectCompat
     */
    private void drawEdgeEffectsUnclipped(Canvas canvas) {
        // The methods below rotate and translate the canvas as needed before drawing the glow,
        // since EdgeEffectCompat always draws a top-glow at 0,0.

        boolean needsInvalidate = false;

        if (!mEdgeEffectTop.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.left, mContentRect.top);
            mEdgeEffectTop.setSize(mContentRect.width(), mContentRect.height());
            if (mEdgeEffectTop.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectBottom.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(2 * mContentRect.left - mContentRect.right, mContentRect.bottom);
            canvas.rotate(180, mContentRect.width(), 0);
            mEdgeEffectBottom.setSize(mContentRect.width(), mContentRect.height());
            if (mEdgeEffectBottom.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectLeft.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.left, mContentRect.bottom);
            canvas.rotate(-90, 0, 0);
            mEdgeEffectLeft.setSize(mContentRect.height(), mContentRect.width());
            if (mEdgeEffectLeft.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectRight.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.right, mContentRect.top);
            canvas.rotate(90, 0, 0);
            mEdgeEffectRight.setSize(mContentRect.height(), mContentRect.width());
            if (mEdgeEffectRight.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and objects related to gesture handling
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retVal = mScaleGestureDetector.onTouchEvent(event);
        retVal = mGestureDetector.onTouchEvent(event) || retVal;
        return retVal || super.onTouchEvent(event);
    }

    private void releaseEdgeEffects() {
        mEdgeEffectLeftActive
                = mEdgeEffectTopActive
                = mEdgeEffectRightActive
                = mEdgeEffectBottomActive
                = false;
        mEdgeEffectLeft.onRelease();
        mEdgeEffectTop.onRelease();
        mEdgeEffectRight.onRelease();
        mEdgeEffectBottom.onRelease();
    }

    private void fling(int velocityX, int velocityY) {
        releaseEdgeEffects();
        // Flings use math in pixels (as opposed to math based on the viewport).
        computeMaxScrollSize(mMaxScrollBuffer);
        getImageRect(mRectF);
        int startX = (int) -mRectF.left;
        int startY = (int) -mRectF.top;
        mScroller.forceFinished(true);
        mScroller.fling(
                startX,
                startY,
                velocityX,
                velocityY,
                0, mMaxScrollBuffer.x,
                0, mMaxScrollBuffer.y,
                mContentRect.width() / 2,
                mContentRect.height() / 2);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void computeMaxScrollSize(Point out) {
        out.set((int)(mScale  * mSrcRect.width() - mContentRect.width()),
                (int)(mScale*mSrcRect.height() - mContentRect.height()));
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        boolean needsInvalidate = false;

        if (mScroller.computeScrollOffset()) {
            // The scroller isn't finished, meaning a fling or programmatic pan operation is
            // currently active.

            computeMaxScrollSize(mMaxScrollBuffer);
            int currX = mScroller.getCurrX();
            int currY = mScroller.getCurrY();

            setTranslationFromScroll(currX, currY);
            getImageRect(mRectF);

            boolean canScrollX = mRectF.left > mContentRect.left ||
                    mRectF.right < mContentRect.right;
            boolean canScrollY = mRectF.top > mContentRect.top ||
                    mRectF.bottom < mContentRect.bottom;

            if (mScale > mMinScale) {
                if (canScrollX
                        && currX < 0
                        && mEdgeEffectLeft.isFinished()
                        && !mEdgeEffectLeftActive) {
                    mEdgeEffectLeft.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                    mEdgeEffectLeftActive = true;
                    needsInvalidate = true;
                } else if (canScrollX
                        && currX > mMaxScrollBuffer.x
                        && mEdgeEffectRight.isFinished()
                        && !mEdgeEffectRightActive) {
                    mEdgeEffectRight.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                    mEdgeEffectRightActive = true;
                    needsInvalidate = true;
                }

                if (canScrollY
                        && currY < 0
                        && mEdgeEffectTop.isFinished()
                        && !mEdgeEffectTopActive) {
                    mEdgeEffectTop.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                    mEdgeEffectTopActive = true;
                    needsInvalidate = true;
                } else if (canScrollY
                        && currY > mMaxScrollBuffer.y
                        && mEdgeEffectBottom.isFinished()
                        && !mEdgeEffectBottomActive) {
                    mEdgeEffectBottom.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                    mEdgeEffectBottomActive = true;
                    needsInvalidate = true;
                }
            }

            validateTranslation();
            ViewCompat.postInvalidateOnAnimation(this);
        }

        if (mZoomer.computeZoom()) {
            float newScale = (1.0f+mZoomer.getCurrZoom()) * mZoomStartScale;

            float scaleFactor = newScale / mScale;
            mScale *= scaleFactor;
            doScale(mZoomFocalPoint.x, mZoomFocalPoint.y, scaleFactor);
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods for programmatically changing the viewport
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Reset original image scroll and zoom to default value
     */
    public void resetTranslateScale() {
        mScale = mMinScale;
        mTranslation.set(0.5f, 0.5f);
        invalidate();
    }

    /**
     * Smoothly zooms the chart in one step.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void zoomIn() {
        mZoomer.forceFinished(true);
        mZoomStartScale = mScale;
        mZoomFocalPoint.set(
                mContentRect.centerX(),
                mContentRect.centerY());
        mZoomer.startZoom(ZOOM_AMOUNT);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly zooms the chart out one step.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void zoomOut() {
        mZoomer.forceFinished(true);
        mZoomStartScale = mScale;
        mZoomFocalPoint.set(
                mContentRect.centerX(),
                mContentRect.centerY());
        mZoomer.startZoom(-ZOOM_AMOUNT);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly pans the chart left one step.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void panLeft() {
        fling((int) (-PAN_VELOCITY_FACTOR * getWidth()), 0);
    }

    /**
     * Smoothly pans the chart right one step.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void panRight() {
        fling((int) (PAN_VELOCITY_FACTOR * getWidth()), 0);
    }

    /**
     * Smoothly pans the chart up one step.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void panUp() {
        fling(0, (int) (-PAN_VELOCITY_FACTOR * getHeight()));
    }

    /**
     * Smoothly pans the chart down one step.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void panDown() {
        fling(0, (int) (PAN_VELOCITY_FACTOR * getHeight()));
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods related to custom attributes
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if horizontal scroll via parent is enabled
     *
     * @return if horizontal scroll is enabled
     * @see #setAllowParentHorizontalScroll(boolean)
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean getAllowParentHorizontalScroll() {
        return mAllowParentHorizontalScroll;
    }

    /**
     * Return true if vertical scroll via parent is enabled
     * @return if vertical scroll is enabled
     * @see #setAllowParentVerticalScroll(boolean)
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean getAllowParentVerticalScroll() {
        return mAllowParentVerticalScroll;
    }

    /**
     * Set if horizontal scroll via parent is enabled, for example by ViewPager
     *
     * @see #getAllowParentHorizontalScroll()
     * @param allowParentHorizontalScroll allow horizontal scroll via parent
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setAllowParentHorizontalScroll(boolean allowParentHorizontalScroll) {
        mAllowParentHorizontalScroll = allowParentHorizontalScroll;
    }

    /**
     * Set if vertical scroll via parent is enabled, for example by ListView
     *
     * @see #getAllowParentHorizontalScroll()
     * @param allowParentVerticalScroll allow vertical scroll via parent
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setAllowParentVerticalScroll(boolean allowParentVerticalScroll) {
        mAllowParentVerticalScroll = allowParentVerticalScroll;
    }

    /**
     * Set image drawable
     * @param drawable set image to display
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setSrcDrawable(Drawable drawable) {
        this.mSrc = drawable;
        if (mSrc != null) {
            setupImage();
            mScale = mMinScale;
        }
        resetTranslateScale();
    }

    /**
     * Set image drawable
     * @param drawable set image to display
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setSrcResource(int drawable) {
        if (drawable == 0) {
            setSrcDrawable(null);
            return;
        }
        final Resources resources = getResources();
        assert resources != null;
        setSrcDrawable(resources.getDrawable(drawable));
    }

    /**
     * Set image drawable
     * @param bitmap set image to display
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setSrcBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            setSrcDrawable(null);
            return;
        }
        final Resources resources = getResources();
        assert resources != null;
        setSrcDrawable(new BitmapDrawable(resources, bitmap));
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and classes related to view state persistence.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.translation = mTranslation;
        ss.minScale = mMinScale;
        ss.scale = mScale;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mTranslation = ss.translation;
        mMinScale = ss.minScale;
        mScale = ss.scale;
    }

    /**
     * Persistent state that is saved by InteractiveLineGraphView.
     */
    public static class SavedState extends BaseSavedState {
        private PointF translation;
        private float minScale;
        private float scale;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(translation.x);
            out.writeFloat(translation.y);
            out.writeFloat(minScale);
            out.writeFloat(scale);
        }

        @Override
        public String toString() {
            return "InteractiveLineGraphView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " translation=" + translation.toString()
                    + " minScale=" + minScale
                    + " scale=" + scale
                    + "}";
        }

        @SuppressWarnings("UnusedDeclaration")
        public static final Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        });

        SavedState(Parcel in) {
            super(in);
            translation = new PointF(in.readFloat(), in.readFloat());
            minScale = in.readFloat();
            scale = in.readFloat();
        }
    }

}
