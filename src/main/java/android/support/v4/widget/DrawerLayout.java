package android.support.v4.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.os.SystemClock;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.KeyEventCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewGroupCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.ViewDragHelper.Callback;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

public class DrawerLayout extends ViewGroup {
    private static final boolean ALLOW_EDGE_LOCK = false;
    private static final int DEFAULT_SCRIM_COLOR = -1728053248;
    /* access modifiers changed from: private|static|final */
    public static final int[] LAYOUT_ATTRS = new int[]{16842931};
    public static final int LOCK_MODE_LOCKED_CLOSED = 1;
    public static final int LOCK_MODE_LOCKED_OPEN = 2;
    public static final int LOCK_MODE_UNLOCKED = 0;
    private static final int MIN_DRAWER_MARGIN = 64;
    private static final int MIN_FLING_VELOCITY = 400;
    private static final int PEEK_DELAY = 160;
    public static final int STATE_DRAGGING = 1;
    public static final int STATE_IDLE = 0;
    public static final int STATE_SETTLING = 2;
    private static final String TAG = "DrawerLayout";
    private boolean mChildrenCanceledTouch;
    private boolean mDisallowInterceptRequested;
    private int mDrawerState;
    private boolean mFirstLayout;
    private boolean mInLayout;
    private float mInitialMotionX;
    private float mInitialMotionY;
    private final ViewDragCallback mLeftCallback;
    private final ViewDragHelper mLeftDragger;
    private DrawerListener mListener;
    private int mLockModeLeft;
    private int mLockModeRight;
    private int mMinDrawerMargin;
    private final ViewDragCallback mRightCallback;
    private final ViewDragHelper mRightDragger;
    private int mScrimColor;
    private float mScrimOpacity;
    private Paint mScrimPaint;
    private Drawable mShadowLeft;
    private Drawable mShadowRight;

    public interface DrawerListener {
        void onDrawerClosed(View view);

        void onDrawerOpened(View view);

        void onDrawerSlide(View view, float f);

        void onDrawerStateChanged(int i);
    }

    public static class LayoutParams extends MarginLayoutParams {
        public int gravity;
        boolean isPeeking;
        boolean knownOpen;
        float onScreen;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            this.gravity = 0;
            TypedArray a = c.obtainStyledAttributes(attrs, DrawerLayout.LAYOUT_ATTRS);
            this.gravity = a.getInt(0, 0);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
            this.gravity = 0;
        }

        public LayoutParams(int width, int height, int gravity) {
            this(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.gravity = 0;
            this.gravity = source.gravity;
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
            this.gravity = 0;
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
            this.gravity = 0;
        }
    }

    protected static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        int lockModeLeft = 0;
        int lockModeRight = 0;
        int openDrawerGravity = 0;

        public SavedState(Parcel in) {
            super(in);
            this.openDrawerGravity = in.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(this.openDrawerGravity);
        }
    }

    class AccessibilityDelegate extends AccessibilityDelegateCompat {
        private final Rect mTmpRect = new Rect();

        AccessibilityDelegate() {
        }

        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            AccessibilityNodeInfoCompat superNode = AccessibilityNodeInfoCompat.obtain(info);
            super.onInitializeAccessibilityNodeInfo(host, superNode);
            info.setSource(host);
            ViewParent parent = ViewCompat.getParentForAccessibility(host);
            if (parent instanceof View) {
                info.setParent((View) parent);
            }
            copyNodeInfoNoChildren(info, superNode);
            superNode.recycle();
            int childCount = DrawerLayout.this.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = DrawerLayout.this.getChildAt(i);
                if (!filter(child)) {
                    info.addChild(child);
                }
            }
        }

        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child, AccessibilityEvent event) {
            if (filter(child)) {
                return DrawerLayout.ALLOW_EDGE_LOCK;
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }

        public boolean filter(View child) {
            View openDrawer = DrawerLayout.this.findOpenDrawer();
            return (openDrawer == null || openDrawer == child) ? DrawerLayout.ALLOW_EDGE_LOCK : true;
        }

        private void copyNodeInfoNoChildren(AccessibilityNodeInfoCompat dest, AccessibilityNodeInfoCompat src) {
            Rect rect = this.mTmpRect;
            src.getBoundsInParent(rect);
            dest.setBoundsInParent(rect);
            src.getBoundsInScreen(rect);
            dest.setBoundsInScreen(rect);
            dest.setVisibleToUser(src.isVisibleToUser());
            dest.setPackageName(src.getPackageName());
            dest.setClassName(src.getClassName());
            dest.setContentDescription(src.getContentDescription());
            dest.setEnabled(src.isEnabled());
            dest.setClickable(src.isClickable());
            dest.setFocusable(src.isFocusable());
            dest.setFocused(src.isFocused());
            dest.setAccessibilityFocused(src.isAccessibilityFocused());
            dest.setSelected(src.isSelected());
            dest.setLongClickable(src.isLongClickable());
            dest.addAction(src.getActions());
        }
    }

    public static abstract class SimpleDrawerListener implements DrawerListener {
        public void onDrawerSlide(View drawerView, float slideOffset) {
        }

        public void onDrawerOpened(View drawerView) {
        }

        public void onDrawerClosed(View drawerView) {
        }

        public void onDrawerStateChanged(int newState) {
        }
    }

    private class ViewDragCallback extends Callback {
        private ViewDragHelper mDragger;
        private final int mGravity;
        private final Runnable mPeekRunnable = new Runnable() {
            public void run() {
                ViewDragCallback.this.peekDrawer();
            }
        };

        public ViewDragCallback(int gravity) {
            this.mGravity = gravity;
        }

        public void setDragger(ViewDragHelper dragger) {
            this.mDragger = dragger;
        }

        public void removeCallbacks() {
            DrawerLayout.this.removeCallbacks(this.mPeekRunnable);
        }

        public boolean tryCaptureView(View child, int pointerId) {
            return (DrawerLayout.this.isDrawerView(child) && DrawerLayout.this.checkDrawerViewGravity(child, this.mGravity) && DrawerLayout.this.getDrawerLockMode(child) == 0) ? true : DrawerLayout.ALLOW_EDGE_LOCK;
        }

        public void onViewDragStateChanged(int state) {
            DrawerLayout.this.updateDrawerState(this.mGravity, state, this.mDragger.getCapturedView());
        }

        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            float offset;
            int childWidth = changedView.getWidth();
            if (DrawerLayout.this.checkDrawerViewGravity(changedView, 3)) {
                offset = ((float) (childWidth + left)) / ((float) childWidth);
            } else {
                offset = ((float) (DrawerLayout.this.getWidth() - left)) / ((float) childWidth);
            }
            DrawerLayout.this.setDrawerViewOffset(changedView, offset);
            changedView.setVisibility(offset == 0.0f ? 4 : 0);
            DrawerLayout.this.invalidate();
        }

        public void onViewCaptured(View capturedChild, int activePointerId) {
            ((LayoutParams) capturedChild.getLayoutParams()).isPeeking = DrawerLayout.ALLOW_EDGE_LOCK;
            closeOtherDrawer();
        }

        private void closeOtherDrawer() {
            int otherGrav = 3;
            if (this.mGravity == 3) {
                otherGrav = 5;
            }
            View toClose = DrawerLayout.this.findDrawerWithGravity(otherGrav);
            if (toClose != null) {
                DrawerLayout.this.closeDrawer(toClose);
            }
        }

        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int left;
            float offset = DrawerLayout.this.getDrawerViewOffset(releasedChild);
            int childWidth = releasedChild.getWidth();
            if (DrawerLayout.this.checkDrawerViewGravity(releasedChild, 3)) {
                left = (xvel > 0.0f || (xvel == 0.0f && offset > 0.5f)) ? 0 : -childWidth;
            } else {
                int width = DrawerLayout.this.getWidth();
                left = (xvel < 0.0f || (xvel == 0.0f && offset < 0.5f)) ? width - childWidth : width;
            }
            this.mDragger.settleCapturedViewAt(left, releasedChild.getTop());
            DrawerLayout.this.invalidate();
        }

        public void onEdgeTouched(int edgeFlags, int pointerId) {
            DrawerLayout.this.postDelayed(this.mPeekRunnable, 160);
        }

        /* access modifiers changed from: private */
        public void peekDrawer() {
            View toCapture;
            int childLeft;
            int i = 0;
            int peekDistance = this.mDragger.getEdgeSize();
            boolean leftEdge = this.mGravity == 3 ? true : DrawerLayout.ALLOW_EDGE_LOCK;
            if (leftEdge) {
                toCapture = DrawerLayout.this.findDrawerWithGravity(3);
                if (toCapture != null) {
                    i = -toCapture.getWidth();
                }
                childLeft = i + peekDistance;
            } else {
                toCapture = DrawerLayout.this.findDrawerWithGravity(5);
                childLeft = DrawerLayout.this.getWidth() - peekDistance;
            }
            if (toCapture == null) {
                return;
            }
            if (((leftEdge && toCapture.getLeft() < childLeft) || (!leftEdge && toCapture.getLeft() > childLeft)) && DrawerLayout.this.getDrawerLockMode(toCapture) == 0) {
                LayoutParams lp = (LayoutParams) toCapture.getLayoutParams();
                this.mDragger.smoothSlideViewTo(toCapture, childLeft, toCapture.getTop());
                lp.isPeeking = true;
                DrawerLayout.this.invalidate();
                closeOtherDrawer();
                DrawerLayout.this.cancelChildViewTouch();
            }
        }

        public boolean onEdgeLock(int edgeFlags) {
            return DrawerLayout.ALLOW_EDGE_LOCK;
        }

        public void onEdgeDragStarted(int edgeFlags, int pointerId) {
            View toCapture;
            if ((edgeFlags & 1) == 1) {
                toCapture = DrawerLayout.this.findDrawerWithGravity(3);
            } else {
                toCapture = DrawerLayout.this.findDrawerWithGravity(5);
            }
            if (toCapture != null && DrawerLayout.this.getDrawerLockMode(toCapture) == 0) {
                this.mDragger.captureChildView(toCapture, pointerId);
            }
        }

        public int getViewHorizontalDragRange(View child) {
            return child.getWidth();
        }

        public int clampViewPositionHorizontal(View child, int left, int dx) {
            if (DrawerLayout.this.checkDrawerViewGravity(child, 3)) {
                return Math.max(-child.getWidth(), Math.min(left, 0));
            }
            int width = DrawerLayout.this.getWidth();
            return Math.max(width - child.getWidth(), Math.min(left, width));
        }

        public int clampViewPositionVertical(View child, int top, int dy) {
            return child.getTop();
        }
    }

    public DrawerLayout(Context context) {
        this(context, null);
    }

    public DrawerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mScrimColor = DEFAULT_SCRIM_COLOR;
        this.mScrimPaint = new Paint();
        this.mFirstLayout = true;
        float density = getResources().getDisplayMetrics().density;
        this.mMinDrawerMargin = (int) ((64.0f * density) + 0.5f);
        float minVel = 400.0f * density;
        this.mLeftCallback = new ViewDragCallback(3);
        this.mRightCallback = new ViewDragCallback(5);
        this.mLeftDragger = ViewDragHelper.create(this, 0.5f, this.mLeftCallback);
        this.mLeftDragger.setEdgeTrackingEnabled(1);
        this.mLeftDragger.setMinVelocity(minVel);
        this.mLeftCallback.setDragger(this.mLeftDragger);
        this.mRightDragger = ViewDragHelper.create(this, 0.5f, this.mRightCallback);
        this.mRightDragger.setEdgeTrackingEnabled(2);
        this.mRightDragger.setMinVelocity(minVel);
        this.mRightCallback.setDragger(this.mRightDragger);
        setFocusableInTouchMode(true);
        ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegate());
        ViewGroupCompat.setMotionEventSplittingEnabled(this, ALLOW_EDGE_LOCK);
    }

    public void setDrawerShadow(Drawable shadowDrawable, int gravity) {
        int absGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
        if ((absGravity & 3) == 3) {
            this.mShadowLeft = shadowDrawable;
            invalidate();
        }
        if ((absGravity & 5) == 5) {
            this.mShadowRight = shadowDrawable;
            invalidate();
        }
    }

    public void setDrawerShadow(int resId, int gravity) {
        setDrawerShadow(getResources().getDrawable(resId), gravity);
    }

    public void setScrimColor(int color) {
        this.mScrimColor = color;
        invalidate();
    }

    public void setDrawerListener(DrawerListener listener) {
        this.mListener = listener;
    }

    public void setDrawerLockMode(int lockMode) {
        setDrawerLockMode(lockMode, 3);
        setDrawerLockMode(lockMode, 5);
    }

    public void setDrawerLockMode(int lockMode, int edgeGravity) {
        int absGrav = GravityCompat.getAbsoluteGravity(edgeGravity, ViewCompat.getLayoutDirection(this));
        if (absGrav == 3) {
            this.mLockModeLeft = lockMode;
        } else if (absGrav == 5) {
            this.mLockModeRight = lockMode;
        }
        if (lockMode != 0) {
            (absGrav == 3 ? this.mLeftDragger : this.mRightDragger).cancel();
        }
        switch (lockMode) {
            case 1:
                View toClose = findDrawerWithGravity(absGrav);
                if (toClose != null) {
                    closeDrawer(toClose);
                    return;
                }
                return;
            case 2:
                View toOpen = findDrawerWithGravity(absGrav);
                if (toOpen != null) {
                    openDrawer(toOpen);
                    return;
                }
                return;
            default:
                return;
        }
    }

    public void setDrawerLockMode(int lockMode, View drawerView) {
        if (isDrawerView(drawerView)) {
            setDrawerLockMode(lockMode, getDrawerViewGravity(drawerView));
            return;
        }
        throw new IllegalArgumentException("View " + drawerView + " is not a " + "drawer with appropriate layout_gravity");
    }

    public int getDrawerLockMode(int edgeGravity) {
        int absGrav = GravityCompat.getAbsoluteGravity(edgeGravity, ViewCompat.getLayoutDirection(this));
        if (absGrav == 3) {
            return this.mLockModeLeft;
        }
        if (absGrav == 5) {
            return this.mLockModeRight;
        }
        return 0;
    }

    public int getDrawerLockMode(View drawerView) {
        int gravity = getDrawerViewGravity(drawerView);
        if (gravity == 3) {
            return this.mLockModeLeft;
        }
        if (gravity == 5) {
            return this.mLockModeRight;
        }
        return 0;
    }

    /* access modifiers changed from: 0000 */
    public void updateDrawerState(int forGravity, int activeState, View activeDrawer) {
        int state;
        int leftState = this.mLeftDragger.getViewDragState();
        int rightState = this.mRightDragger.getViewDragState();
        if (leftState == 1 || rightState == 1) {
            state = 1;
        } else if (leftState == 2 || rightState == 2) {
            state = 2;
        } else {
            state = 0;
        }
        if (activeDrawer != null && activeState == 0) {
            LayoutParams lp = (LayoutParams) activeDrawer.getLayoutParams();
            if (lp.onScreen == 0.0f) {
                dispatchOnDrawerClosed(activeDrawer);
            } else if (lp.onScreen == 1.0f) {
                dispatchOnDrawerOpened(activeDrawer);
            }
        }
        if (state != this.mDrawerState) {
            this.mDrawerState = state;
            if (this.mListener != null) {
                this.mListener.onDrawerStateChanged(state);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void dispatchOnDrawerClosed(View drawerView) {
        LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (lp.knownOpen) {
            lp.knownOpen = ALLOW_EDGE_LOCK;
            if (this.mListener != null) {
                this.mListener.onDrawerClosed(drawerView);
            }
            sendAccessibilityEvent(32);
        }
    }

    /* access modifiers changed from: 0000 */
    public void dispatchOnDrawerOpened(View drawerView) {
        LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (!lp.knownOpen) {
            lp.knownOpen = true;
            if (this.mListener != null) {
                this.mListener.onDrawerOpened(drawerView);
            }
            drawerView.sendAccessibilityEvent(32);
        }
    }

    /* access modifiers changed from: 0000 */
    public void dispatchOnDrawerSlide(View drawerView, float slideOffset) {
        if (this.mListener != null) {
            this.mListener.onDrawerSlide(drawerView, slideOffset);
        }
    }

    /* access modifiers changed from: 0000 */
    public void setDrawerViewOffset(View drawerView, float slideOffset) {
        LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (slideOffset != lp.onScreen) {
            lp.onScreen = slideOffset;
            dispatchOnDrawerSlide(drawerView, slideOffset);
        }
    }

    /* access modifiers changed from: 0000 */
    public float getDrawerViewOffset(View drawerView) {
        return ((LayoutParams) drawerView.getLayoutParams()).onScreen;
    }

    /* access modifiers changed from: 0000 */
    public int getDrawerViewGravity(View drawerView) {
        return GravityCompat.getAbsoluteGravity(((LayoutParams) drawerView.getLayoutParams()).gravity, ViewCompat.getLayoutDirection(drawerView));
    }

    /* access modifiers changed from: 0000 */
    public boolean checkDrawerViewGravity(View drawerView, int checkFor) {
        return (getDrawerViewGravity(drawerView) & checkFor) == checkFor ? true : ALLOW_EDGE_LOCK;
    }

    /* access modifiers changed from: 0000 */
    public View findOpenDrawer() {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (((LayoutParams) child.getLayoutParams()).knownOpen) {
                return child;
            }
        }
        return null;
    }

    /* access modifiers changed from: 0000 */
    public void moveDrawerToOffset(View drawerView, float slideOffset) {
        float oldOffset = getDrawerViewOffset(drawerView);
        int width = drawerView.getWidth();
        int dx = ((int) (((float) width) * slideOffset)) - ((int) (((float) width) * oldOffset));
        if (!checkDrawerViewGravity(drawerView, 3)) {
            dx = -dx;
        }
        drawerView.offsetLeftAndRight(dx);
        setDrawerViewOffset(drawerView, slideOffset);
    }

    /* access modifiers changed from: 0000 */
    public View findDrawerWithGravity(int gravity) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if ((getDrawerViewGravity(child) & 7) == (gravity & 7)) {
                return child;
            }
        }
        return null;
    }

    static String gravityToString(int gravity) {
        if ((gravity & 3) == 3) {
            return "LEFT";
        }
        if ((gravity & 5) == 5) {
            return "RIGHT";
        }
        return Integer.toHexString(gravity);
    }

    /* access modifiers changed from: protected */
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mFirstLayout = true;
    }

    /* access modifiers changed from: protected */
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.mFirstLayout = true;
    }

    /* access modifiers changed from: protected */
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (widthMode == 1073741824 && heightMode == 1073741824) {
            setMeasuredDimension(widthSize, heightSize);
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != 8) {
                    LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    if (isContentView(child)) {
                        child.measure(MeasureSpec.makeMeasureSpec((widthSize - lp.leftMargin) - lp.rightMargin, 1073741824), MeasureSpec.makeMeasureSpec((heightSize - lp.topMargin) - lp.bottomMargin, 1073741824));
                    } else if (isDrawerView(child)) {
                        int childGravity = getDrawerViewGravity(child) & 7;
                        if ((0 & childGravity) != 0) {
                            throw new IllegalStateException("Child drawer has absolute gravity " + gravityToString(childGravity) + " but this " + TAG + " already has a " + "drawer view along that edge");
                        }
                        child.measure(getChildMeasureSpec(widthMeasureSpec, (this.mMinDrawerMargin + lp.leftMargin) + lp.rightMargin, lp.width), getChildMeasureSpec(heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height));
                    } else {
                        throw new IllegalStateException("Child " + child + " at index " + i + " does not have a valid layout_gravity - must be Gravity.LEFT, " + "Gravity.RIGHT or Gravity.NO_GRAVITY");
                    }
                }
            }
            return;
        }
        throw new IllegalArgumentException("DrawerLayout must be measured with MeasureSpec.EXACTLY.");
    }

    /* access modifiers changed from: protected */
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        this.mInLayout = true;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != 8) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (isContentView(child)) {
                    child.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight());
                } else {
                    int childLeft;
                    int childWidth = child.getMeasuredWidth();
                    int childHeight = child.getMeasuredHeight();
                    if (checkDrawerViewGravity(child, 3)) {
                        childLeft = (-childWidth) + ((int) (((float) childWidth) * lp.onScreen));
                    } else {
                        childLeft = (r - l) - ((int) (((float) childWidth) * lp.onScreen));
                    }
                    int height;
                    switch (lp.gravity & 112) {
                        case 16:
                            height = b - t;
                            int childTop = (height - childHeight) / 2;
                            if (childTop < lp.topMargin) {
                                childTop = lp.topMargin;
                            } else if (childTop + childHeight > height - lp.bottomMargin) {
                                childTop = (height - lp.bottomMargin) - childHeight;
                            }
                            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                            break;
                        case 80:
                            height = b - t;
                            child.layout(childLeft, (height - lp.bottomMargin) - child.getMeasuredHeight(), childLeft + childWidth, height - lp.bottomMargin);
                            break;
                        default:
                            child.layout(childLeft, lp.topMargin, childLeft + childWidth, childHeight);
                            break;
                    }
                    if (lp.onScreen == 0.0f) {
                        child.setVisibility(4);
                    }
                }
            }
        }
        this.mInLayout = ALLOW_EDGE_LOCK;
        this.mFirstLayout = ALLOW_EDGE_LOCK;
    }

    public void requestLayout() {
        if (!this.mInLayout) {
            super.requestLayout();
        }
    }

    public void computeScroll() {
        int childCount = getChildCount();
        float scrimOpacity = 0.0f;
        for (int i = 0; i < childCount; i++) {
            scrimOpacity = Math.max(scrimOpacity, ((LayoutParams) getChildAt(i).getLayoutParams()).onScreen);
        }
        this.mScrimOpacity = scrimOpacity;
        if ((this.mLeftDragger.continueSettling(true) | this.mRightDragger.continueSettling(true)) != 0) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private static boolean hasOpaqueBackground(View v) {
        Drawable bg = v.getBackground();
        if (bg == null || bg.getOpacity() != -1) {
            return ALLOW_EDGE_LOCK;
        }
        return true;
    }

    /* access modifiers changed from: protected */
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int height = getHeight();
        boolean drawingContent = isContentView(child);
        int clipLeft = 0;
        int clipRight = getWidth();
        int restoreCount = canvas.save();
        if (drawingContent) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View v = getChildAt(i);
                if (v != child && v.getVisibility() == 0 && hasOpaqueBackground(v) && isDrawerView(v) && v.getHeight() >= height) {
                    if (checkDrawerViewGravity(v, 3)) {
                        int vright = v.getRight();
                        if (vright > clipLeft) {
                            clipLeft = vright;
                        }
                    } else {
                        int vleft = v.getLeft();
                        if (vleft < clipRight) {
                            clipRight = vleft;
                        }
                    }
                }
            }
            canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);
        int shadowWidth;
        float alpha;
        if (this.mScrimOpacity > 0.0f && drawingContent) {
            this.mScrimPaint.setColor((((int) (((float) ((this.mScrimColor & -16777216) >>> 24)) * this.mScrimOpacity)) << 24) | (this.mScrimColor & 16777215));
            canvas.drawRect((float) clipLeft, 0.0f, (float) clipRight, (float) getHeight(), this.mScrimPaint);
        } else if (this.mShadowLeft != null && checkDrawerViewGravity(child, 3)) {
            shadowWidth = this.mShadowLeft.getIntrinsicWidth();
            int childRight = child.getRight();
            alpha = Math.max(0.0f, Math.min(((float) childRight) / ((float) this.mLeftDragger.getEdgeSize()), 1.0f));
            this.mShadowLeft.setBounds(childRight, child.getTop(), childRight + shadowWidth, child.getBottom());
            this.mShadowLeft.setAlpha((int) (255.0f * alpha));
            this.mShadowLeft.draw(canvas);
        } else if (this.mShadowRight != null && checkDrawerViewGravity(child, 5)) {
            shadowWidth = this.mShadowRight.getIntrinsicWidth();
            int childLeft = child.getLeft();
            alpha = Math.max(0.0f, Math.min(((float) (getWidth() - childLeft)) / ((float) this.mRightDragger.getEdgeSize()), 1.0f));
            this.mShadowRight.setBounds(childLeft - shadowWidth, child.getTop(), childLeft, child.getBottom());
            this.mShadowRight.setAlpha((int) (255.0f * alpha));
            this.mShadowRight.draw(canvas);
        }
        return result;
    }

    /* access modifiers changed from: 0000 */
    public boolean isContentView(View child) {
        return ((LayoutParams) child.getLayoutParams()).gravity == 0 ? true : ALLOW_EDGE_LOCK;
    }

    /* access modifiers changed from: 0000 */
    public boolean isDrawerView(View child) {
        return (GravityCompat.getAbsoluteGravity(((LayoutParams) child.getLayoutParams()).gravity, ViewCompat.getLayoutDirection(child)) & 7) != 0 ? true : ALLOW_EDGE_LOCK;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        boolean interceptForDrag = this.mLeftDragger.shouldInterceptTouchEvent(ev) | this.mRightDragger.shouldInterceptTouchEvent(ev);
        boolean interceptForTap = ALLOW_EDGE_LOCK;
        switch (action) {
            case 0:
                float x = ev.getX();
                float y = ev.getY();
                this.mInitialMotionX = x;
                this.mInitialMotionY = y;
                if (this.mScrimOpacity > 0.0f && isContentView(this.mLeftDragger.findTopChildUnder((int) x, (int) y))) {
                    interceptForTap = true;
                }
                this.mDisallowInterceptRequested = ALLOW_EDGE_LOCK;
                this.mChildrenCanceledTouch = ALLOW_EDGE_LOCK;
                break;
            case 1:
            case 3:
                closeDrawers(true);
                this.mDisallowInterceptRequested = ALLOW_EDGE_LOCK;
                this.mChildrenCanceledTouch = ALLOW_EDGE_LOCK;
                break;
            case 2:
                if (this.mLeftDragger.checkTouchSlop(3)) {
                    this.mLeftCallback.removeCallbacks();
                    this.mRightCallback.removeCallbacks();
                    break;
                }
                break;
        }
        if (interceptForDrag || interceptForTap || hasPeekingDrawer() || this.mChildrenCanceledTouch) {
            return true;
        }
        return ALLOW_EDGE_LOCK;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        this.mLeftDragger.processTouchEvent(ev);
        this.mRightDragger.processTouchEvent(ev);
        float x;
        float y;
        switch (ev.getAction() & MotionEventCompat.ACTION_MASK) {
            case 0:
                x = ev.getX();
                y = ev.getY();
                this.mInitialMotionX = x;
                this.mInitialMotionY = y;
                this.mDisallowInterceptRequested = ALLOW_EDGE_LOCK;
                this.mChildrenCanceledTouch = ALLOW_EDGE_LOCK;
                break;
            case 1:
                x = ev.getX();
                y = ev.getY();
                boolean peekingOnly = true;
                View touchedView = this.mLeftDragger.findTopChildUnder((int) x, (int) y);
                if (touchedView != null && isContentView(touchedView)) {
                    float dx = x - this.mInitialMotionX;
                    float dy = y - this.mInitialMotionY;
                    int slop = this.mLeftDragger.getTouchSlop();
                    if ((dx * dx) + (dy * dy) < ((float) (slop * slop))) {
                        View openDrawer = findOpenDrawer();
                        if (openDrawer != null) {
                            peekingOnly = getDrawerLockMode(openDrawer) == 2 ? true : ALLOW_EDGE_LOCK;
                        }
                    }
                }
                closeDrawers(peekingOnly);
                this.mDisallowInterceptRequested = ALLOW_EDGE_LOCK;
                break;
            case 3:
                closeDrawers(true);
                this.mDisallowInterceptRequested = ALLOW_EDGE_LOCK;
                this.mChildrenCanceledTouch = ALLOW_EDGE_LOCK;
                break;
        }
        return true;
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (!(this.mLeftDragger.isEdgeTouched(1) || this.mRightDragger.isEdgeTouched(2))) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
        this.mDisallowInterceptRequested = disallowIntercept;
        if (disallowIntercept) {
            closeDrawers(true);
        }
    }

    public void closeDrawers() {
        closeDrawers(ALLOW_EDGE_LOCK);
    }

    /* access modifiers changed from: 0000 */
    public void closeDrawers(boolean peekingOnly) {
        boolean needsInvalidate = ALLOW_EDGE_LOCK;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (isDrawerView(child) && (!peekingOnly || lp.isPeeking)) {
                int childWidth = child.getWidth();
                if (checkDrawerViewGravity(child, 3)) {
                    needsInvalidate |= this.mLeftDragger.smoothSlideViewTo(child, -childWidth, child.getTop());
                } else {
                    needsInvalidate |= this.mRightDragger.smoothSlideViewTo(child, getWidth(), child.getTop());
                }
                lp.isPeeking = ALLOW_EDGE_LOCK;
            }
        }
        this.mLeftCallback.removeCallbacks();
        this.mRightCallback.removeCallbacks();
        if (needsInvalidate) {
            invalidate();
        }
    }

    public void openDrawer(View drawerView) {
        if (isDrawerView(drawerView)) {
            if (this.mFirstLayout) {
                LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
                lp.onScreen = 1.0f;
                lp.knownOpen = true;
            } else if (checkDrawerViewGravity(drawerView, 3)) {
                this.mLeftDragger.smoothSlideViewTo(drawerView, 0, drawerView.getTop());
            } else {
                this.mRightDragger.smoothSlideViewTo(drawerView, getWidth() - drawerView.getWidth(), drawerView.getTop());
            }
            invalidate();
            return;
        }
        throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
    }

    public void openDrawer(int gravity) {
        int absGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
        View drawerView = findDrawerWithGravity(absGravity);
        if (drawerView == null) {
            throw new IllegalArgumentException("No drawer view found with absolute gravity " + gravityToString(absGravity));
        }
        openDrawer(drawerView);
    }

    public void closeDrawer(View drawerView) {
        if (isDrawerView(drawerView)) {
            if (this.mFirstLayout) {
                LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
                lp.onScreen = 0.0f;
                lp.knownOpen = ALLOW_EDGE_LOCK;
            } else if (checkDrawerViewGravity(drawerView, 3)) {
                this.mLeftDragger.smoothSlideViewTo(drawerView, -drawerView.getWidth(), drawerView.getTop());
            } else {
                this.mRightDragger.smoothSlideViewTo(drawerView, getWidth(), drawerView.getTop());
            }
            invalidate();
            return;
        }
        throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
    }

    public void closeDrawer(int gravity) {
        int absGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
        View drawerView = findDrawerWithGravity(absGravity);
        if (drawerView == null) {
            throw new IllegalArgumentException("No drawer view found with absolute gravity " + gravityToString(absGravity));
        }
        closeDrawer(drawerView);
    }

    public boolean isDrawerOpen(View drawer) {
        if (isDrawerView(drawer)) {
            return ((LayoutParams) drawer.getLayoutParams()).knownOpen;
        }
        throw new IllegalArgumentException("View " + drawer + " is not a drawer");
    }

    public boolean isDrawerOpen(int drawerGravity) {
        View drawerView = findDrawerWithGravity(drawerGravity);
        if (drawerView != null) {
            return isDrawerOpen(drawerView);
        }
        return ALLOW_EDGE_LOCK;
    }

    public boolean isDrawerVisible(View drawer) {
        if (isDrawerView(drawer)) {
            return ((LayoutParams) drawer.getLayoutParams()).onScreen > 0.0f ? true : ALLOW_EDGE_LOCK;
        } else {
            throw new IllegalArgumentException("View " + drawer + " is not a drawer");
        }
    }

    public boolean isDrawerVisible(int drawerGravity) {
        View drawerView = findDrawerWithGravity(drawerGravity);
        if (drawerView != null) {
            return isDrawerVisible(drawerView);
        }
        return ALLOW_EDGE_LOCK;
    }

    private boolean hasPeekingDrawer() {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (((LayoutParams) getChildAt(i).getLayoutParams()).isPeeking) {
                return true;
            }
        }
        return ALLOW_EDGE_LOCK;
    }

    /* access modifiers changed from: protected */
    public android.view.ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(-1, -1);
    }

    /* access modifiers changed from: protected */
    public android.view.ViewGroup.LayoutParams generateLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (p instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) p);
        }
        return p instanceof MarginLayoutParams ? new LayoutParams((MarginLayoutParams) p) : new LayoutParams(p);
    }

    /* access modifiers changed from: protected */
    public boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return ((p instanceof LayoutParams) && super.checkLayoutParams(p)) ? true : ALLOW_EDGE_LOCK;
    }

    public android.view.ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    private boolean hasVisibleDrawer() {
        return findVisibleDrawer() != null ? true : ALLOW_EDGE_LOCK;
    }

    private View findVisibleDrawer() {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (isDrawerView(child) && isDrawerVisible(child)) {
                return child;
            }
        }
        return null;
    }

    /* access modifiers changed from: 0000 */
    public void cancelChildViewTouch() {
        if (!this.mChildrenCanceledTouch) {
            long now = SystemClock.uptimeMillis();
            MotionEvent cancelEvent = MotionEvent.obtain(now, now, 3, 0.0f, 0.0f, 0);
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).dispatchTouchEvent(cancelEvent);
            }
            cancelEvent.recycle();
            this.mChildrenCanceledTouch = true;
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != 4 || !hasVisibleDrawer()) {
            return super.onKeyDown(keyCode, event);
        }
        KeyEventCompat.startTracking(event);
        return true;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode != 4) {
            return super.onKeyUp(keyCode, event);
        }
        View visibleDrawer = findVisibleDrawer();
        if (visibleDrawer != null && getDrawerLockMode(visibleDrawer) == 0) {
            closeDrawers();
        }
        return visibleDrawer != null ? true : ALLOW_EDGE_LOCK;
    }

    /* access modifiers changed from: protected */
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        if (ss.openDrawerGravity != 0) {
            View toOpen = findDrawerWithGravity(ss.openDrawerGravity);
            if (toOpen != null) {
                openDrawer(toOpen);
            }
        }
        setDrawerLockMode(ss.lockModeLeft, 3);
        setDrawerLockMode(ss.lockModeRight, 5);
    }

    /* access modifiers changed from: protected */
    public Parcelable onSaveInstanceState() {
        SavedState ss = new SavedState(super.onSaveInstanceState());
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (isDrawerView(child)) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.knownOpen) {
                    ss.openDrawerGravity = lp.gravity;
                    break;
                }
            }
        }
        ss.lockModeLeft = this.mLockModeLeft;
        ss.lockModeRight = this.mLockModeRight;
        return ss;
    }
}
