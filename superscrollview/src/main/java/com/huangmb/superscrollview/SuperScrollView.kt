package com.huangmb.superscrollview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.view.AccessibilityDelegateCompat
import android.support.v4.view.InputDeviceCompat
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.NestedScrollingChild
import android.support.v4.view.NestedScrollingChildHelper
import android.support.v4.view.NestedScrollingParent
import android.support.v4.view.NestedScrollingParentHelper
import android.support.v4.view.ScrollingView
import android.support.v4.view.ViewCompat
import android.support.v4.view.accessibility.AccessibilityEventCompat
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.support.v4.widget.EdgeEffectCompat
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AnimationUtils
import android.widget.EdgeEffect
import android.widget.FrameLayout
import android.widget.OverScroller
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Created by bob.huang on 2018/8/05.
 * 类似IOS的UIScrollView,在NestedScrollView的基础上,支持双向滚动,缩放,分页
 */
class SuperScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent, NestedScrollingChild, ScrollingView {

    private var mLastScroll: Long = 0

    private val mTempRect = Rect()
    private val mScroller = OverScroller(context, null)
    private var mEdgeGlowTop: EdgeEffect? = null
    private var mEdgeGlowBottom: EdgeEffect? = null

    private var mEdgeGlowLeft: EdgeEffect? = null
    private var mEdgeGlowRight: EdgeEffect? = null
    /**
     * Position of the last motion event.
     */
    private var mLastMotionY: Int = 0

    private var mLastMotionX: Int = 0

    /**
     * True when the layout has changed but the traversal has not come through yet.
     * Ideally the view hierarchy would keep track of this for us.
     */
    private var mIsLayoutDirty = true
    private var mIsLaidOut = false

    /**
     * The child to give focus to in the event that a child has requested focus while the
     * layout is dirty. This prevents the scroll from being wrong if the child has not been
     * laid out before requesting focus.
     */
    private var mChildToScrollTo: View? = null

    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private var mIsBeingDragged = false

    /**
     * Determines speed during touch scrolling
     */
    private var mVelocityTracker: VelocityTracker? = null

    /**
     * When set to true, the scroll view measure its child to make it fill the currently
     * visible area.
     */
    /**
     * Indicates whether this ScrollView's content is stretched to fill the viewport.
     *
     * @return True if the content fills the viewport, false otherwise.
     *
     * @attr ref android.R.styleable#ScrollView_fillViewport
     */
    /**
     * Indicates this ScrollView whether it should stretch its content height to fill
     * the viewport or not.
     *
     * @param fillViewport True to stretch the content's height to the viewport's
     * boundaries, false otherwise.
     *
     * @attr ref android.R.styleable#ScrollView_fillViewport
     */
    var isFillViewport: Boolean = false
        set(fillViewport) {
            if (fillViewport != isFillViewport) {
                field = fillViewport
                requestLayout()
            }
        }

    /**
     * Whether arrow scrolling is animated.
     */
    var isSmoothScrollingEnabled = true

    /**
     * When true, the scroll view stops on multiples of the scroll view's size
     * when scrolling. This can be used for horizontal pagination. The default
     * value is false.
     */
    var isPagingEnabled = false

    var horizontalPagingThreshold = 0.5
    var verticalPagingThreshold = 0.5


    /**
     * When true, the ScrollView will try to lock to only vertical or horizontal
     * scrolling while dragging.
     */
    var isDirectionalLockEnabled = false

    private var mActivelyScrolling = false

    private var mPostTouchRunnable: Runnable? = null
    private val configuration = ViewConfiguration.get(context)
    private var mTouchSlop = configuration.scaledTouchSlop
    private var mMinimumVelocity = configuration.scaledMinimumFlingVelocity
    private var mMaximumVelocity = configuration.scaledMaximumFlingVelocity

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private var mActivePointerId = INVALID_POINTER

    /**
     * Used during scrolling to retrieve the new offset within the window.
     */
    private val mScrollOffset = IntArray(2)
    private val mScrollConsumed = IntArray(2)
    private var mNestedXOffset: Int = 0
    private var mNestedYOffset: Int = 0

    private var mSavedState: SavedState? = null

    private val mParentHelper: NestedScrollingParentHelper
    private val mChildHelper: NestedScrollingChildHelper

    private var mVerticalScrollFactor: Float = 0F

    private var mOnScrollChangeListener: OnScrollChangeListener? = null

    /**
     * @return The maximum amount this scroll view will scroll in response to
     * an arrow event.
     */
    val maxScrollAmountY: Int
        get() = (MAX_SCROLL_FACTOR * height).toInt()

    val maxScrollAmountX: Int
        get() = (MAX_SCROLL_FACTOR * width).toInt()

    private val verticalScrollFactorCompat: Float
        get() {
            if (mVerticalScrollFactor == 0f) {
                val outValue = TypedValue()
                val context = context
                if (!context.theme.resolveAttribute(
                                android.R.attr.listPreferredItemHeight, outValue, true)) {
                    throw IllegalStateException(
                            "Expected theme to define listPreferredItemHeight.")
                }
                mVerticalScrollFactor = outValue.getDimension(
                        context.resources.displayMetrics)
            }
            return mVerticalScrollFactor
        }

    private val horizontalScrollFactorCompat: Float
        get() = verticalScrollFactorCompat

    private val scrollRangeX: Int
        get() {
            var scrollRange = 0
            if (childCount > 0) {
                val child = getChildAt(0)
                scrollRange = max(0,
                        child.width - (width - paddingLeft - paddingRight))
            }
            return scrollRange
        }

    private val scrollRangeY: Int
        get() {
            var scrollRange = 0
            if (childCount > 0) {
                val child = getChildAt(0)
                scrollRange = max(0,
                        child.height - (height - paddingBottom - paddingTop))
            }
            return scrollRange
        }

    val isHorizontal: Boolean
        get() = childCount > 0 && getChildAt(0).width > (width - paddingLeft - paddingRight)

    /**
     * Interface definition for a callback to be invoked when the scroll
     * X or Y positions of a view change.
     *
     *
     * This version of the interface works on all versions of Android, back to API v4.
     *
     * @see .setOnScrollChangeListener
     */
    interface OnScrollChangeListener {
        /**
         * Called when the scroll position of a view changes.
         *
         * @param v The view whose scroll position has changed.
         * @param scrollX Current horizontal scroll origin.
         * @param scrollY Current vertical scroll origin.
         * @param oldScrollX Previous horizontal scroll origin.
         * @param oldScrollY Previous vertical scroll origin.
         */
        fun onScrollChange(v: SuperScrollView, scrollX: Int, scrollY: Int,
                           oldScrollX: Int, oldScrollY: Int)
    }

    init {
        initScrollView()

        val a = context.obtainStyledAttributes(
                attrs, R.styleable.SuperScrollView, defStyleAttr, 0)

        isFillViewport = a.getBoolean(R.styleable.SuperScrollView_isFillViewport, false)
        isPagingEnabled = a.getBoolean(R.styleable.SuperScrollView_isPagingEnabled, false)

        a.recycle()

        mParentHelper = NestedScrollingParentHelper(this)
        mChildHelper = NestedScrollingChildHelper(this)

        // ...because why else would you be using this widget?
        isNestedScrollingEnabled = true

        ViewCompat.setAccessibilityDelegate(this, ACCESSIBILITY_DELEGATE)
    }

    // NestedScrollingChild

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return mChildHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return mChildHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        mChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return mChildHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
                                      dyUnconsumed: Int, offsetInWindow: IntArray?): Boolean {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                offsetInWindow)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?): Boolean {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    // NestedScrollingParent

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return nestedScrollAxes != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, nestedScrollAxes: Int) {
        mParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes)
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL and ViewCompat.SCROLL_AXIS_HORIZONTAL)
    }

    override fun onStopNestedScroll(target: View) {
        mParentHelper.onStopNestedScroll(target)
        stopNestedScroll()
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
                                dyUnconsumed: Int) {
        val oldScrollX = scrollX
        val oldScrollY = scrollY
        scrollBy(dxUnconsumed, dyUnconsumed)
        val myConsumedX = scrollX - oldScrollX
        val myUnconsumedX = dxUnconsumed - myConsumedX
        val myConsumedY = scrollY - oldScrollY
        val myUnconsumedY = dyUnconsumed - myConsumedY
        dispatchNestedScroll(myConsumedX, myConsumedY, myUnconsumedX, myUnconsumedY, null)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        dispatchNestedPreScroll(dx, dy, consumed, null)
    }

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        if (!consumed) {
            flingWithNestedDispatch(velocityX.toInt(), velocityY.toInt())
            return true
        }
        return false
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        return dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun getNestedScrollAxes(): Int {
        return mParentHelper.nestedScrollAxes
    }

    // ScrollView import

    override fun shouldDelayChildPressedState(): Boolean {
        return true
    }

    override fun getLeftFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = horizontalFadingEdgeLength
        if (scrollX < length) {
            return scrollX / length.toFloat()
        }

        return 1.0f
    }

    override fun getRightFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = horizontalFadingEdgeLength
        val rightEdge = width - paddingRight
        val span = getChildAt(0).right - scrollX - rightEdge
        return if (span < length) {
            span / length.toFloat()
        } else 1.0f

    }


    override fun getTopFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = verticalFadingEdgeLength
        val scrollY = scrollY
        return if (scrollY < length) {
            scrollY / length.toFloat()
        } else 1.0f

    }

    override fun getBottomFadingEdgeStrength(): Float {
        if (childCount == 0) {
            return 0.0f
        }

        val length = verticalFadingEdgeLength
        val bottomEdge = height - paddingBottom
        val span = getChildAt(0).bottom - scrollY - bottomEdge
        return if (span < length) {
            span / length.toFloat()
        } else 1.0f

    }

    private fun initScrollView() {
        isFocusable = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        setWillNotDraw(false)
    }

    override fun addView(child: View) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }

        super.addView(child)
    }

    override fun addView(child: View, index: Int) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }

        super.addView(child, index)
    }

    override fun addView(child: View, params: ViewGroup.LayoutParams) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }

        super.addView(child, params)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (childCount > 0) {
            throw IllegalStateException("ScrollView can host only one direct child")
        }

        super.addView(child, index, params)
    }

    /**
     * Register a callback to be invoked when the scroll X or Y positions of
     * this view change.
     *
     * This version of the method works on all versions of Android, back to API v4.
     *
     * @param l The listener to notify when the scroll X or Y position changes.
     * @see android.view.View.getScrollX
     * @see android.view.View.getScrollY
     */
    fun setOnScrollChangeListener(l: OnScrollChangeListener) {
        mOnScrollChangeListener = l
    }

    /**
     * @return Returns true this ScrollView can be scrolled
     */
    private fun canScroll(): Boolean {
        val child = getChildAt(0)
        if (child != null) {
            val childHeight = child.height
            val childWidth = child.width
            return height < childHeight + paddingTop + paddingBottom || width < childWidth + paddingLeft + paddingRight
        }
        return false
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        if (mOnScrollChangeListener != null) {
            mOnScrollChangeListener!!.onScrollChange(this, l, t, oldl, oldt)
        }

        mActivelyScrolling = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (!isFillViewport) {
            return
        }

        if (childCount == 0) {
            return
        }
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            return
        }

        val child = getChildAt(0)
        val height = measuredHeight
        val width = measuredWidth
        val fillHorizontal = child.measuredWidth < width
        val fillVertical = child.measuredHeight < height
        if (!fillHorizontal && !fillVertical) {
            return
        }
        val lp = child.layoutParams as FrameLayout.LayoutParams
        val childWidthMeasureSpec = if (fillHorizontal) {
            MeasureSpec.makeMeasureSpec(width - paddingLeft - paddingRight, MeasureSpec.EXACTLY)
        } else {
            getChildMeasureSpec(widthMeasureSpec,
                    paddingLeft + paddingRight, lp.width)
        }
        val childHeightMeasureSpec = if (fillVertical) {
            MeasureSpec.makeMeasureSpec(height - paddingTop - paddingBottom, View.MeasureSpec.EXACTLY)
        } else {
            ViewGroup.getChildMeasureSpec(heightMeasureSpec, paddingTop + paddingBottom, lp.height)
        }
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Let the focused view and/or our descendants get the key first
        return super.dispatchKeyEvent(event) || executeKeyEvent(event)
    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    fun executeKeyEvent(event: KeyEvent): Boolean {
        mTempRect.setEmpty()

        if (!canScroll()) {
            if (isFocused && event.keyCode != KeyEvent.KEYCODE_BACK) {
                var currentFocused: View? = findFocus()
                if (currentFocused === this) currentFocused = null
                val nextFocused = FocusFinder.getInstance().findNextFocus(this,
                        currentFocused, View.FOCUS_DOWN)
                return (nextFocused != null
                        && nextFocused != this
                        && nextFocused.requestFocus(View.FOCUS_DOWN))
            }
            return false
        }

        var handled = false
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> handled = if (!event.isAltPressed) {
                    arrowScrollHorizontal(View.FOCUS_LEFT)
                } else {
                    fullScrollHorizontal(View.FOCUS_LEFT)
                }
                KeyEvent.KEYCODE_DPAD_UP -> handled = if (!event.isAltPressed) {
                    arrowScrollVertical(View.FOCUS_UP)
                } else {
                    fullScrollVertical(View.FOCUS_UP)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> handled = if (!event.isAltPressed) {
                    arrowScrollHorizontal(View.FOCUS_RIGHT)
                } else {
                    fullScrollHorizontal(View.FOCUS_RIGHT)
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> handled = if (!event.isAltPressed) {
                    arrowScrollVertical(View.FOCUS_DOWN)
                } else {
                    fullScrollVertical(View.FOCUS_DOWN)
                }
                KeyEvent.KEYCODE_SPACE -> {
                    val canScrollHorizontal = computeHorizontalScrollRange() > computeHorizontalScrollExtent()
                    val canScrollVertical = computeVerticalScrollRange() > computeVerticalScrollExtent()

                    if (canScrollVertical) {
                        val direction = if (event.isShiftPressed) View.FOCUS_UP else View.FOCUS_DOWN
                        pageScrollVertical(direction)
                    } else {
                        val direction = if (event.isShiftPressed) View.FOCUS_LEFT else View.FOCUS_RIGHT
                        pageScrollHorizontal(direction)
                    }

                }
            }
        }

        return handled
    }

    private fun inChild(x: Int, y: Int): Boolean {
        if (childCount > 0) {
            val scrollY = scrollY
            val child = getChildAt(0)
            return !(y < child.top - scrollY
                    || y >= child.bottom - scrollY
                    || x < child.left
                    || x >= child.right)
        }
        return false
    }

    private fun initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        } else {
            mVelocityTracker!!.clear()
        }
    }

    private fun initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            recycleVelocityTracker()
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        val action = ev.action
        if (action == MotionEvent.ACTION_MOVE && mIsBeingDragged) {
            return true
        }

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_MOVE -> move@ {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                 * Locally do absolute value. mLastMotionY is set to the y value
                 * of the down event.
                 */
                val activePointerId = mActivePointerId
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    return@move
                }

                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId
                            + " in onInterceptTouchEvent")
                    return@move
                }

                var x = ev.getX(pointerIndex).toInt()
                val y = ev.getY(pointerIndex).toInt()
                val xDiff = abs(x - mLastMotionX)
                val yDiff = abs(y - mLastMotionY)
                val canScrollHorizontal = computeHorizontalScrollRange() > computeHorizontalScrollExtent()
                val canScrollVertical = computeVerticalScrollRange() > computeVerticalScrollExtent()

                if (canScrollHorizontal && xDiff > mTouchSlop && nestedScrollAxes and ViewCompat.SCROLL_AXIS_HORIZONTAL == 0
                        || canScrollVertical && yDiff > mTouchSlop && nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL == 0) {
                    mIsBeingDragged = true
                    mLastMotionX = x
                    mLastMotionY = y

                    initVelocityTrackerIfNotExists()
                    mVelocityTracker!!.addMovement(ev)
                    mNestedYOffset = 0
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_DOWN -> down@ {
                val x = ev.x.toInt()
                val y = ev.y.toInt()
                if (!inChild(x, y)) {
                    mIsBeingDragged = false
                    recycleVelocityTracker()
                    return@down
                }

                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                mLastMotionX = x
                mLastMotionY = y
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0)

                initOrResetVelocityTracker()
                mVelocityTracker!!.addMovement(ev)
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't. mScroller.isFinished should be false when
                 * being flinged. We need to call computeScrollOffset() first so that
                 * isFinished() is correct.
                 */
                mScroller.computeScrollOffset()
                mIsBeingDragged = !mScroller.isFinished
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL and ViewCompat.SCROLL_AXIS_HORIZONTAL)
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                /* Release the drag */
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
                recycleVelocityTracker()
                if (mScroller.springBack(scrollX, scrollY, 0, scrollRangeX, 0, scrollRangeY)) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                stopNestedScroll()
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        initVelocityTrackerIfNotExists()

        val vtev = MotionEvent.obtain(ev)

        val actionMasked = MotionEventCompat.getActionMasked(ev)

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0
        }
        vtev.offsetLocation(0f, mNestedYOffset.toFloat())

        val velocityTracker = mVelocityTracker
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (childCount == 0) {
                    return false
                }
                mIsBeingDragged = !mScroller.isFinished
                if (mIsBeingDragged) {
                    val parent = parent
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished) {
                    mScroller.abortAnimation()
                }

                // Remember where the motion event started
                mLastMotionX = ev.x.toInt()
                mLastMotionY = ev.y.toInt()
                mActivePointerId = ev.getPointerId(0)
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }
            MotionEvent.ACTION_MOVE -> move@ {
                val activePointerIndex = ev.findPointerIndex(mActivePointerId)
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=$mActivePointerId in onTouchEvent")
                    return@move
                }

                val x = ev.getX(activePointerIndex).toInt()
                val y = ev.getY(activePointerIndex).toInt()
                var deltaX = mLastMotionX - x
                var deltaY = mLastMotionY - y
                if (dispatchNestedPreScroll(deltaX, deltaY, mScrollConsumed, mScrollOffset)) {
                    deltaX -= mScrollConsumed[0]
                    deltaY -= mScrollConsumed[1]
                    vtev.offsetLocation(mScrollOffset[0].toFloat(), mScrollOffset[1].toFloat())
                    mNestedXOffset += mScrollOffset[0]
                    mNestedYOffset += mScrollOffset[1]
                }
                if (!mIsBeingDragged) {
                    if (abs(deltaY) > mTouchSlop) {
                        if (deltaY > 0) {
                            deltaY -= mTouchSlop
                        } else {
                            deltaY += mTouchSlop
                        }
                        mIsBeingDragged = true
                    } else {
                        deltaY = 0
                    }

                    if (abs(deltaX) > mTouchSlop) {
                        if (deltaX > 0) {
                            deltaX -= mTouchSlop
                        } else {
                            deltaX += mTouchSlop
                        }
                        mIsBeingDragged = true
                    } else {
                        deltaX = 0
                    }
                    if (mIsBeingDragged) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }

                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mLastMotionX = x - mScrollOffset[0]
                    mLastMotionY = y - mScrollOffset[1]

                    val oldX = scrollX
                    val oldY = scrollY
                    val rangeX = scrollRangeX
                    val rangeY = scrollRangeY
                    val overscrollMode = overScrollMode
                    val canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS || overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && (rangeX > 0 || rangeY > 0)

                    // Calling overScrollByCompat will call onOverScrolled, which
                    // calls onScrollChanged if applicable.
                    if (overScrollByCompat(deltaX, deltaY, oldX, oldY, rangeX, rangeY, 0,
                                    0, true) && !hasNestedScrollingParent()) {
                        // Break our velocity if we hit a scroll barrier.
                        velocityTracker?.clear()
                    }

                    val scrolledDeltaX = scrollX - oldX
                    val scrolledDeltaY = scrollY - oldY
                    val unconsumedX = deltaX - scrolledDeltaX
                    val unconsumedY = deltaY - scrolledDeltaY
                    if (dispatchNestedScroll(scrolledDeltaX, scrolledDeltaY, unconsumedX, unconsumedY, mScrollOffset)) {
                        mLastMotionX -= mScrollOffset[0]
                        mLastMotionY -= mScrollOffset[1]
                        vtev.offsetLocation(mScrollOffset[0].toFloat(), mScrollOffset[1].toFloat())
                        mNestedXOffset += mScrollOffset[0]
                        mNestedYOffset += mScrollOffset[1]
                    } else if (canOverscroll) {
                        ensureGlows()
                        val edgeGlowLeft = mEdgeGlowLeft
                        val edgeGlowTop = mEdgeGlowTop
                        val edgeGlowRight = mEdgeGlowRight
                        val edgeGlowBottom = mEdgeGlowBottom
                        if (edgeGlowLeft != null) {
                            val pulledToX = oldX + deltaX
                            if (pulledToX < 0) {
                                EdgeEffectCompat.onPull(edgeGlowLeft, deltaX.toFloat() / width,
                                        1F - ev.getY(activePointerIndex) / height)
                                if (!edgeGlowRight!!.isFinished) {
                                    edgeGlowRight.onRelease()
                                }
                            } else if (pulledToX > rangeX) {
                                EdgeEffectCompat.onPull(edgeGlowRight, deltaX.toFloat() / width,
                                        ev.getY(activePointerIndex) / height)
                                if (!edgeGlowLeft.isFinished) {
                                    edgeGlowLeft.onRelease()
                                }
                            }
                        }
                        if (edgeGlowTop != null) {
                            val pulledToY = oldY + deltaY
                            if (pulledToY < 0) {
                                EdgeEffectCompat.onPull(edgeGlowTop, deltaY.toFloat() / height,
                                        ev.getX(activePointerIndex) / width)
                                if (!edgeGlowBottom!!.isFinished) {
                                    edgeGlowBottom.onRelease()
                                }
                            } else if (pulledToY > rangeY) {
                                EdgeEffectCompat.onPull(edgeGlowBottom, deltaY.toFloat() / height,
                                        1f - ev.getX(activePointerIndex) / width)
                                if (!edgeGlowTop.isFinished) {
                                    edgeGlowTop.onRelease()
                                }
                            }
                        }
                        if (edgeGlowTop != null && (!edgeGlowTop.isFinished || !edgeGlowBottom!!.isFinished)
                                || edgeGlowLeft != null && (!edgeGlowLeft.isFinished || !edgeGlowRight!!.isFinished)) {
                            ViewCompat.postInvalidateOnAnimation(this)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mIsBeingDragged) {
                    velocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                    val initialVelocitx = velocityTracker.getXVelocity(mActivePointerId).toInt()
                    val initialVelocity = velocityTracker.getYVelocity(mActivePointerId).toInt()

                    val vx = if (abs(initialVelocitx) > mMinimumVelocity) -initialVelocitx else 0
                    val vy = if (abs(initialVelocity) > mMinimumVelocity) -initialVelocity else 0
                    if (vx != 0 || vy != 0) {
                        flingWithNestedDispatch(vx, vy)
                    } else if (mScroller.springBack(scrollX, scrollY, 0, scrollRangeX, 0,
                                    scrollRangeY)) {
                        ViewCompat.postInvalidateOnAnimation(this)
                    }
                }
                mActivePointerId = INVALID_POINTER
                endDrag()
                handlePostTouchScrolling()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mIsBeingDragged && childCount > 0) {
                    if (mScroller.springBack(scrollX, scrollY, 0, scrollRangeX, 0,
                                    scrollRangeY)) {
                        ViewCompat.postInvalidateOnAnimation(this)
                    }
                }
                mActivePointerId = INVALID_POINTER
                endDrag()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                mLastMotionY = ev.getY(index).toInt()
                mActivePointerId = ev.getPointerId(index)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
                mLastMotionY = ev.getY(ev.findPointerIndex(mActivePointerId)).toInt()
            }
        }

        velocityTracker?.addMovement(vtev)

        vtev.recycle()
        return true
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mLastMotionY = ev.getY(newPointerIndex).toInt()
            mActivePointerId = ev.getPointerId(newPointerIndex)
            if (mVelocityTracker != null) {
                mVelocityTracker!!.clear()
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDeviceCompat.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (!mIsBeingDragged) {
                        val hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                        val vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                        if (hscroll != 0f || vscroll != 0f) {
                            val deltaX = (hscroll * horizontalScrollFactorCompat).toInt()
                            val deltaY = (vscroll * verticalScrollFactorCompat).toInt()

                            val rangeX = scrollRangeX
                            val rangeY = scrollRangeY

                            val oldScrollX = scrollX
                            var newScrollX = oldScrollX - deltaX
                            if (newScrollX < 0) {
                                newScrollX = 0
                            } else if (newScrollX > rangeX) {
                                newScrollX = rangeX
                            }

                            val oldScrollY = scrollY
                            var newScrollY = oldScrollY - deltaY
                            if (newScrollY < 0) {
                                newScrollY = 0
                            } else if (newScrollY > rangeY) {
                                newScrollY = rangeY
                            }
                            if (newScrollY != oldScrollY || newScrollX != oldScrollX) {
                                super.scrollTo(newScrollX, newScrollY)
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int,
                                clampedX: Boolean, clampedY: Boolean) {
        super.scrollTo(scrollX, scrollY)
    }

    private fun overScrollByCompat(deltaX: Int, deltaY: Int,
                                   scrollX: Int, scrollY: Int,
                                   scrollRangeX: Int, scrollRangeY: Int,
                                   maxOverScrollX: Int, maxOverScrollY: Int,
                                   isTouchEvent: Boolean): Boolean {
        var maxOverScrollX = maxOverScrollX
        var maxOverScrollY = maxOverScrollY
        val overScrollMode = overScrollMode
        val canScrollHorizontal = computeHorizontalScrollRange() > computeHorizontalScrollExtent()
        val canScrollVertical = computeVerticalScrollRange() > computeVerticalScrollExtent()
        val overScrollHorizontal = overScrollMode == OVER_SCROLL_ALWAYS || overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollHorizontal
        val overScrollVertical = overScrollMode == OVER_SCROLL_ALWAYS || overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollVertical

        var newScrollX = scrollX + deltaX
        if (!overScrollHorizontal) {
            maxOverScrollX = 0
        }

        var newScrollY = scrollY + deltaY
        if (!overScrollVertical) {
            maxOverScrollY = 0
        }

        // Clamp values if at the limits and record
        val left = -maxOverScrollX
        val right = maxOverScrollX + scrollRangeX
        val top = -maxOverScrollY
        val bottom = maxOverScrollY + scrollRangeY

        var clampedX = false
        if (newScrollX > right) {
            newScrollX = right
            clampedX = true
        } else if (newScrollX < left) {
            newScrollX = left
            clampedX = true
        }

        var clampedY = false
        if (newScrollY > bottom) {
            newScrollY = bottom
            clampedY = true
        } else if (newScrollY < top) {
            newScrollY = top
            clampedY = true
        }

        if (clampedY) {
            mScroller.springBack(newScrollX, newScrollY, 0, scrollRangeX, 0, scrollRangeY)
        }

        onOverScrolled(newScrollX, newScrollY, clampedX, clampedY)

        return clampedX || clampedY
    }

    /**
     *
     *
     * Finds the next focusable component that fits in the specified bounds.
     *
     *
     * @param topFocus look for a candidate is the one at the top of the bounds
     * if topFocus is true, or at the bottom of the bounds if topFocus is
     * false
     * @param top      the top offset of the bounds in which a focusable must be
     * found
     * @param bottom   the bottom offset of the bounds in which a focusable must
     * be found
     * @return the next focusable component in the bounds or null if none can
     * be found
     */
    private fun findFocusableViewInBounds(topFocus: Boolean, top: Int, bottom: Int): View? {

        val focusables = getFocusables(View.FOCUS_FORWARD)
        var focusCandidate: View? = null

        /*
         * A fully contained focusable is one where its top is below the bound's
         * top, and its bottom is above the bound's bottom. A partially
         * contained focusable is one where some part of it is within the
         * bounds, but it also has some part that is not within bounds.  A fully contained
         * focusable is preferred to a partially contained focusable.
         */
        var foundFullyContainedFocusable = false

        val count = focusables.size
        for (i in 0 until count) {
            val view = focusables[i]
            val viewTop = view.top
            val viewBottom = view.bottom

            if (top < viewBottom && viewTop < bottom) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */

                val viewIsFullyContained = top < viewTop && viewBottom < bottom

                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view
                    foundFullyContainedFocusable = viewIsFullyContained
                } else {
                    val viewIsCloserToBoundary = topFocus && viewTop < focusCandidate.top || !topFocus && viewBottom > focusCandidate
                            .bottom

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /* Any fully contained view beats a partially contained view */
                            focusCandidate = view
                            foundFullyContainedFocusable = true
                        } else if (viewIsCloserToBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view
                        }
                    }
                }
            }
        }

        return focusCandidate
    }

    /**
     *
     * Handles scrolling in response to a "page up/down" shortcut press. This
     * method will scroll the view by one page up or down and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.
     *
     * @param direction the scroll direction: [android.view.View.FOCUS_UP]
     * to go one page up or
     * [android.view.View.FOCUS_DOWN] to go one page down
     * @return true if the key event is consumed by this method, false otherwise
     */
    fun pageScrollVertical(direction: Int): Boolean {
        val down = direction == View.FOCUS_DOWN
        val height = height

        if (down) {
            mTempRect.top = scrollY + height
            val count = childCount
            if (count > 0) {
                val view = getChildAt(count - 1)
                if (mTempRect.top + height > view.bottom) {
                    mTempRect.top = view.bottom - height
                }
            }
        } else {
            mTempRect.top = scrollY - height
            if (mTempRect.top < 0) {
                mTempRect.top = 0
            }
        }
        mTempRect.bottom = mTempRect.top + height

        return scrollAndFocusVertical(direction, mTempRect.top, mTempRect.bottom)
    }

    /**
     *
     * Handles scrolling in response to a "page up/down" shortcut press. This
     * method will scroll the view by one page left or right and give the focus
     * to the leftmost/rightmost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.
     *
     * @param direction the scroll direction: [android.view.View.FOCUS_LEFT]
     * to go one page left or [android.view.View.FOCUS_RIGHT]
     * to go one page right
     * @return true if the key event is consumed by this method, false otherwise
     */
    fun pageScrollHorizontal(direction: Int): Boolean {
        val right = direction == View.FOCUS_RIGHT
        val width = width

        if (right) {
            mTempRect.left = scrollX + width
            val count = childCount
            if (count > 0) {
                val view = getChildAt(0)
                if (mTempRect.left + width > view.right) {
                    mTempRect.left = view.right - width
                }
            }
        } else {
            mTempRect.left = scrollX - width
            if (mTempRect.left < 0) {
                mTempRect.left = 0
            }
        }
        mTempRect.right = mTempRect.left + width

        return scrollAndFocusHorizontal(direction, mTempRect.left, mTempRect.right)
    }

    /**
     *
     * Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the top or bottom and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.
     *
     * @param direction the scroll direction: [android.view.View.FOCUS_UP]
     * to go the top of the view or
     * [android.view.View.FOCUS_DOWN] to go the bottom
     * @return true if the key event is consumed by this method, false otherwise
     */
    fun fullScrollVertical(direction: Int): Boolean {
        val down = direction == View.FOCUS_DOWN
        val height = height

        mTempRect.top = 0
        mTempRect.bottom = height

        if (down) {
            val count = childCount
            if (count > 0) {
                val view = getChildAt(count - 1)
                mTempRect.bottom = view.bottom + paddingBottom
                mTempRect.top = mTempRect.bottom - height
            }
        }

        return scrollAndFocusVertical(direction, mTempRect.top, mTempRect.bottom)
    }

    /**
     *
     * Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the left or right and give the focus
     * to the leftmost/rightmost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.
     *
     * @param direction the scroll direction: [android.view.View.FOCUS_LEFT]
     * to go the left of the view or [android.view.View.FOCUS_RIGHT]
     * to go the right
     * @return true if the key event is consumed by this method, false otherwise
     */
    fun fullScrollHorizontal(direction: Int): Boolean {
        val right = direction == View.FOCUS_RIGHT
        val width = width

        mTempRect.left = 0
        mTempRect.right = width

        if (right) {
            val count = childCount
            if (count > 0) {
                val view = getChildAt(0)
                mTempRect.right = view.right
                mTempRect.left = mTempRect.right - width
            }
        }

        return scrollAndFocusHorizontal(direction, mTempRect.left, mTempRect.right)
    }

    /**
     *
     * Scrolls the view to make the area defined by `top` and
     * `bottom` visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this ScrollView.
     *
     * @param direction the scroll direction: [android.view.View.FOCUS_UP]
     * to go upward, [android.view.View.FOCUS_DOWN] to downward
     * @param top       the top offset of the new area to be made visible
     * @param bottom    the bottom offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private fun scrollAndFocusVertical(direction: Int, top: Int, bottom: Int): Boolean {
        var handled = true

        val height = height
        val containerTop = scrollY
        val containerBottom = containerTop + height
        val up = direction == View.FOCUS_UP

        var newFocused = findFocusableViewInBounds(up, top, bottom)
        if (newFocused == null) {
            newFocused = this
        }

        if (top >= containerTop && bottom <= containerBottom) {
            handled = false
        } else {
            val delta = if (up) top - containerTop else bottom - containerBottom
            doScroll(0, delta)
        }

        if (newFocused !== findFocus()) newFocused.requestFocus(direction)

        return handled
    }

    /**
     *
     * Scrolls the view to make the area defined by `left` and
     * `right` visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this scrollview.
     *
     * @param direction the scroll direction: [android.view.View.FOCUS_LEFT]
     * to go left [android.view.View.FOCUS_RIGHT] to right
     * @param left     the left offset of the new area to be made visible
     * @param right    the right offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private fun scrollAndFocusHorizontal(direction: Int, left: Int, right: Int): Boolean {
        var handled = true

        val width = width
        val containerLeft = scrollX
        val containerRight = containerLeft + width
        val goLeft = direction == View.FOCUS_LEFT

        var newFocused = findFocusableViewInBounds(goLeft, left, right)
        if (newFocused == null) {
            newFocused = this
        }

        if (left >= containerLeft && right <= containerRight) {
            handled = false
        } else {
            val delta = if (goLeft) left - containerLeft else right - containerRight
            doScroll(delta, 0)
        }

        if (newFocused !== findFocus()) newFocused.requestFocus(direction)

        return handled
    }

    /**
     * Handle scrolling in response to a left or right arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     *                  pressed
     * @return True if we consumed the event, false otherwise
     */
    fun arrowScrollHorizontal(direction: Int): Boolean {
        var currentFocused: View? = findFocus()
        if (currentFocused === this) currentFocused = null

        val nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction)

        val maxJump = maxScrollAmountX

        if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJump)) {
            nextFocused.getDrawingRect(mTempRect)
            offsetDescendantRectToMyCoords(nextFocused, mTempRect)
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect)
            doScroll(scrollDelta.first, 0)
            nextFocused.requestFocus(direction)
        } else {
            // no new focus
            var scrollDelta = maxJump

            if (direction == View.FOCUS_LEFT && scrollX < scrollDelta) {
                scrollDelta = scrollX
            } else if (direction == View.FOCUS_RIGHT && childCount > 0) {

                val daRight = getChildAt(0).right

                val screenRight = scrollX + width

                if (daRight - screenRight < maxJump) {
                    scrollDelta = daRight - screenRight
                }
            }
            if (scrollDelta == 0) {
                return false
            }
            doScroll(if (direction == View.FOCUS_RIGHT) scrollDelta else -scrollDelta, 0)
        }

        if (currentFocused != null && currentFocused.isFocused
                && isOffScreen(currentFocused)) {
            // previously focused item still has focus and is off screen, give
            // it up (take it back to ourselves)
            // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
            // sure to
            // get it)
            val descendantFocusability = descendantFocusability  // save
            setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS)
            requestFocus()
            setDescendantFocusability(descendantFocusability)  // restore
        }
        return true
    }

    /**
     * Handle scrolling in response to an up or down arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     * pressed
     * @return True if we consumed the event, false otherwise
     */
    fun arrowScrollVertical(direction: Int): Boolean {
        var currentFocused: View? = findFocus()
        if (currentFocused === this) currentFocused = null

        val nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction)

        val maxJump = maxScrollAmountY

        if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJump)) {
            nextFocused.getDrawingRect(mTempRect)
            offsetDescendantRectToMyCoords(nextFocused, mTempRect)
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect)
            doScroll(0, scrollDelta.second)
            nextFocused.requestFocus(direction)
        } else {
            // no new focus
            var scrollDelta = maxJump

            if (direction == View.FOCUS_UP && scrollY < scrollDelta) {
                scrollDelta = scrollY
            } else if (direction == View.FOCUS_DOWN) {
                if (childCount > 0) {
                    val daBottom = getChildAt(0).bottom
                    val screenBottom = scrollY + height - paddingBottom
                    if (daBottom - screenBottom < maxJump) {
                        scrollDelta = daBottom - screenBottom
                    }
                }
            }
            if (scrollDelta == 0) {
                return false
            }
            doScroll(0, if (direction == View.FOCUS_DOWN) scrollDelta else -scrollDelta)
        }

        if (currentFocused != null && currentFocused.isFocused
                && isOffScreen(currentFocused)) {
            // previously focused item still has focus and is off screen, give
            // it up (take it back to ourselves)
            // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
            // sure to
            // get it)
            val descendantFocusability = descendantFocusability  // save
            setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS)
            requestFocus()
            setDescendantFocusability(descendantFocusability)  // restore
        }
        return true
    }


    private fun handlePostTouchScrolling() {
        if (isPagingEnabled) {
            mActivelyScrolling = false
            mPostTouchRunnable = PostTouchRunnable()
            ViewCompat.postOnAnimationDelayed(this, mPostTouchRunnable, MOMENTUM_DELAY)
        }
    }

    /**
     * This will smooth scroll us to the nearest page boundary
     * It currently just looks at where the content is relative to the page and slides to the nearest
     * page.  It is intended to be run after we are done scrolling, and handling any momentum
     * scrolling.
     */
    private fun smoothScrollToPage(velocityX: Int = 0, velocityY: Int = 0) {
        val width = width
        val currentX = scrollX
        val predictedX = currentX + velocityX
        var hpage = currentX / width
        if (predictedX > hpage * width + width * horizontalPagingThreshold) {
            hpage += 1
        }

        val height = height
        val currentY = scrollY
        val preY = currentY + velocityY
        var vpage = currentY / height
        if (preY > vpage * height + height * verticalPagingThreshold) {
            vpage += 1
        }

        smoothScrollTo(hpage * width, vpage * height)

    }

    /**
     * @return whether the descendant of this scroll view is scrolled off
     * screen.
     */
    private fun isOffScreen(descendant: View): Boolean {
        return !isWithinDeltaOfScreen(descendant)
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     * pixels of being on the screen.
     */
    private fun isWithinDeltaOfScreen(descendant: View, delta: Int = 0, width: Int = getWidth(), height: Int = getHeight()): Boolean {
        descendant.getDrawingRect(mTempRect)
        offsetDescendantRectToMyCoords(descendant, mTempRect)

        return (mTempRect.right + delta >= scrollX && mTempRect.left - delta <= scrollX + width)
                && mTempRect.bottom + delta >= scrollY && mTempRect.top - delta <= scrollY + height
    }

    /**
     * Smooth scroll by a X/Y delta
     *
     * @param deltaX the number of pixels to scroll by on the X axis
     * @param deltaY the number of pixels to scroll by on the Y axis
     */
    private fun doScroll(deltaX: Int, deltaY: Int) {
        if (deltaX != 0 || deltaY != 0) {
            if (isSmoothScrollingEnabled) {
                smoothScrollBy(deltaX, deltaY)
            } else {
                scrollBy(deltaX, deltaY)
            }
        }
    }

    /**
     * Like [View.scrollBy], but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    fun smoothScrollBy(dx: Int, dy: Int) {
        var dx = dx
        var dy = dy
        if (childCount == 0) {
            // Nothing to do.
            return
        }
        val duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll
        if (duration > ANIMATED_SCROLL_GAP) {
            val child = getChildAt(0)
            val width = width - paddingRight - paddingLeft
            val right = child.right
            val height = height - paddingBottom - paddingTop
            val bottom = child.height
            val maxX = max(0, right - width)
            val maxY = max(0, bottom - height)
            val scrollX = scrollX
            val scrollY = scrollY
            dx = max(0, min(scrollX + dx, maxX)) - scrollX
            dy = max(0, min(scrollY + dy, maxY)) - scrollY

            mScroller.startScroll(scrollX, scrollY, dx, dy)
            ViewCompat.postInvalidateOnAnimation(this)
        } else {
            if (!mScroller.isFinished) {
                mScroller.abortAnimation()
            }
            scrollBy(dx, dy)
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis()
    }

    /**
     * Like [.scrollTo], but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    fun smoothScrollTo(x: Int, y: Int) {
        smoothScrollBy(x - scrollX, y - scrollY)
    }

    /**
     *
     * The scroll range of a scroll view is the overall height of all of its
     * children.
     * @hide
     */
    override fun computeVerticalScrollRange(): Int {
        val count = childCount
        val contentHeight = height - paddingBottom - paddingTop
        if (count == 0) {
            return contentHeight
        }

        var scrollRange = getChildAt(0).bottom
        val scrollY = scrollY
        val overscrollBottom = Math.max(0, scrollRange - contentHeight)
        if (scrollY < 0) {
            scrollRange -= scrollY
        } else if (scrollY > overscrollBottom) {
            scrollRange += scrollY - overscrollBottom
        }

        return scrollRange
    }

    override fun computeVerticalScrollOffset(): Int {
        return max(0, super.computeVerticalScrollOffset())
    }

    override fun computeVerticalScrollExtent(): Int {
        return super.computeVerticalScrollExtent()
    }

    override fun computeHorizontalScrollRange(): Int {
        val count = childCount
        val contentWidth = width - paddingLeft - paddingRight
        if (count == 0) {
            return contentWidth
        }

        var scrollRange = getChildAt(0).right
        val scrollX = scrollX
        val overscrollRight = Math.max(0, scrollRange - contentWidth)
        if (scrollX < 0) {
            scrollRange -= scrollX
        } else if (scrollX > overscrollRight) {
            scrollRange += scrollX - overscrollRight
        }

        return scrollRange
    }

    override fun computeHorizontalScrollOffset(): Int {
        return max(0, super.computeHorizontalScrollOffset())
    }

    override fun computeHorizontalScrollExtent(): Int {
        return super.computeHorizontalScrollExtent()
    }

    override fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val lp = child.layoutParams

        val childWidthMeasureSpec = if (lp.width == LayoutParams.MATCH_PARENT) {
            getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight, lp.width)
        } else {
            MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }
        val childHeightMeasureSpec = if (lp.height == LayoutParams.MATCH_PARENT) {
            ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec, paddingTop + paddingBottom, lp.height)
        } else {
            MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int,
                                         parentHeightMeasureSpec: Int, heightUsed: Int) {
        val lp = child.layoutParams as ViewGroup.MarginLayoutParams

        val childWidthMeasureSpec = if (lp.width == LayoutParams.MATCH_PARENT) {
            getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width)
        } else {
            MeasureSpec.makeMeasureSpec(+lp.leftMargin + lp.rightMargin, View.MeasureSpec.UNSPECIFIED)
        }
        val childHeightMeasureSpec = if (lp.height == LayoutParams.MATCH_PARENT) {
            ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec, paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height)
        } else {
            MeasureSpec.makeMeasureSpec(lp.topMargin + lp.bottomMargin, View.MeasureSpec.UNSPECIFIED)
        }

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            val oldX = scrollX
            val oldY = scrollY
            val x = mScroller.currX
            val y = mScroller.currY

            if (oldX != x || oldY != y) {
                val rangeX = scrollRangeX
                val rangeY = scrollRangeY
                val overscrollMode = overScrollMode
                val canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS || overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && (rangeX > 0 || rangeY > 0)

                overScrollByCompat(x - oldX, y - oldY, oldX, oldY, rangeX, rangeY,
                        0, 0, false)

                if (canOverscroll) {
                    ensureGlows()
                    val currVelocity = mScroller.currVelocity.toInt()
                    if (x <= 0 && oldX > 0) {
                        mEdgeGlowLeft!!.onAbsorb(currVelocity)
                    } else if (rangeX in (oldX + 1)..x) {
                        mEdgeGlowRight!!.onAbsorb(currVelocity)
                    }

                    if (y <= 0 && oldY > 0) {
                        mEdgeGlowTop!!.onAbsorb(currVelocity)
                    } else if (rangeY in (oldY + 1)..y) {
                        mEdgeGlowBottom!!.onAbsorb(currVelocity)
                    }
                }
            }
        }
    }

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    private fun scrollToChild(child: View) {
        child.getDrawingRect(mTempRect)

        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect)

        val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect)

        if (scrollDelta.first != 0 || scrollDelta.second != 0) {
            scrollBy(scrollDelta.first, scrollDelta.second)
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the
     * first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private fun scrollToChildRect(rect: Rect, immediate: Boolean): Boolean {
        val delta = computeScrollDeltaToGetChildRectOnScreen(rect)
        val scroll = delta.first != 0 || delta.second != 0
        if (scroll) {
            if (immediate) {
                scrollBy(delta.first, delta.second)
            } else {
                smoothScrollBy(delta.first, delta.second)
            }
        }
        return scroll
    }

    /**
     * Compute the amount to scroll in the Y direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    protected fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Pair<Int, Int> {
        if (childCount == 0) return 0 to 0

        val width = width
        val height = height
        var screenLeft = scrollX
        var screenRight = screenLeft + width
        var screenTop = scrollY
        var screenBottom = screenTop + height

        val fadingEdgeX = horizontalFadingEdgeLength
        val fadingEdgeY = verticalFadingEdgeLength

        val child = getChildAt(0)

        // leave room for left fading edge as long as rect isn't at very left
        if (rect.left > 0) {
            screenLeft += fadingEdgeX
        }

        // leave room for top fading edge as long as rect isn't at very top
        if (rect.top > 0) {
            screenTop += fadingEdgeY
        }

        // leave room for right fading edge as long as rect isn't at very right
        if (rect.right < getChildAt(0).width) {
            screenRight -= fadingEdgeX
        }


        // leave room for bottom fading edge as long as rect isn't at very bottom
        if (rect.bottom < child.height) {
            screenBottom -= fadingEdgeY
        }

        var scrollXDelta = 0
        var scrollYDelta = 0

        if (rect.bottom > screenBottom && rect.top > screenTop) {
            // need to move down to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            scrollYDelta += if (rect.height() > height) {
                // just enough to get screen size chunk on
                rect.top - screenTop
            } else {
                // get entire rect at bottom of screen
                rect.bottom - screenBottom
            }

            // make sure we aren't scrolling beyond the end of our content
            val bottom = child.bottom
            val distanceToBottom = bottom - screenBottom
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom)

        } else if (rect.top < screenTop && rect.bottom < screenBottom) {
            // need to move up to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            scrollYDelta -= if (rect.height() > height) {
                // screen size chunk
                screenBottom - rect.bottom
            } else {
                // entire rect at top
                screenTop - rect.top
            }

            // make sure we aren't scrolling any further than the top our content
            scrollYDelta = Math.max(scrollYDelta, -scrollY)
        }

        if (rect.right > screenRight && rect.left > screenLeft) {
            // need to move right to get it in view: move right just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            scrollXDelta += if (rect.width() > width) {
                // just enough to get screen size chunk on
                rect.left - screenLeft
            } else {
                // get entire rect at right of screen
                rect.right - screenRight
            }

            // make sure we aren't scrolling beyond the end of our content
            val right = getChildAt(0).right
            val distanceToRight = right - screenRight
            scrollXDelta = Math.min(scrollXDelta, distanceToRight)

        } else if (rect.left < screenLeft && rect.right < screenRight) {
            // need to move right to get it in view: move right just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            scrollXDelta -= if (rect.width() > width) {
                // screen size chunk
                screenRight - rect.right
            } else {
                // entire rect at left
                screenLeft - rect.left
            }

            // make sure we aren't scrolling any further than the left our content
            scrollXDelta = Math.max(scrollXDelta, -scrollX)
        }
        return scrollXDelta to scrollYDelta
    }

    override fun requestChildFocus(child: View, focused: View) {
        if (!mIsLayoutDirty) {
            scrollToChild(focused)
        } else {
            // The child may not be laid out yet, we can't compute the scroll yet
            mChildToScrollTo = focused
        }
        super.requestChildFocus(child, focused)
    }


    /**
     * When looking for focus in children of a scroll view, need to be a little
     * more careful not to give focus to something that is scrolled off screen.
     *
     * This is more expensive than the default [android.view.ViewGroup]
     * implementation, otherwise this behavior might have been made the default.
     */
    override fun onRequestFocusInDescendants(direction: Int,
                                             previouslyFocusedRect: Rect?): Boolean {
        // convert from forward / backward notation to up / down / left / right
        // (ugh).
        var dir = when (direction) {
            View.FOCUS_FORWARD -> View.FOCUS_DOWN
            View.FOCUS_BACKWARD -> View.FOCUS_UP
            else -> direction
        }

        val nextFocus = (if (previouslyFocusedRect == null)
            FocusFinder.getInstance().findNextFocus(this, null, dir)
        else
            FocusFinder.getInstance().findNextFocusFromRect(this,
                    previouslyFocusedRect, dir)) ?: return false

        return if (isOffScreen(nextFocus)) {
            false
        } else nextFocus.requestFocus(dir, previouslyFocusedRect)

    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect,
                                               immediate: Boolean): Boolean {
        // offset into coordinate space of this scroll view
        rectangle.offset(child.left - child.scrollX,
                child.top - child.scrollY)

        return scrollToChildRect(rectangle, immediate)
    }

    override fun requestLayout() {
        mIsLayoutDirty = true
        super.requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        mIsLayoutDirty = false
        // Give a child focus if it needs it
        val child = mChildToScrollTo
        if (child != null && isViewDescendantOf(child, this)) {
            scrollToChild(child)
        }
        mChildToScrollTo = null

        if (!mIsLaidOut) {
            val savedState = mSavedState
            if (savedState != null) {
                scrollTo(savedState.scrollPositionX, savedState.scrollPositionY)
                mSavedState = null
            } // mScrollY default value is "0"

            val child = if (childCount > 0) getChildAt(0) else null
            val childWidth = child?.measuredHeight ?: 0
            val scrollRangeX = max(0, childWidth - (r - l - paddingLeft - paddingRight))

            val childHeight = child?.measuredHeight ?: 0
            val scrollRangeY = max(0, childHeight - (b - t - paddingBottom - paddingTop))

            // Don't forget to clamp
            val mScrollX = if (scrollX > scrollRangeX) scrollRangeX else if (scrollX < 0) 0 else scrollX
            val mScrollY = if (scrollY > scrollRangeY) scrollRangeY else if (scrollY < 0) 0 else scrollX
            scrollTo(mScrollX, mScrollY)
        }

        // Calling this with the present values causes it to re-claim them
        scrollTo(scrollX, scrollY)
        mIsLaidOut = true
    }

    public override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mIsLaidOut = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val currentFocused = findFocus()
        if (null == currentFocused || this == currentFocused)
            return

        // If the currently-focused view was visible on the screen when the
        // screen was at the old height, then scroll the screen to make that
        // view visible with the new screen height.
        if (isWithinDeltaOfScreen(currentFocused, 0, oldw, oldh)) {
            currentFocused.getDrawingRect(mTempRect)
            offsetDescendantRectToMyCoords(currentFocused, mTempRect)
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect)
            doScroll(scrollDelta.first, scrollDelta.second)
        }
    }

    /**
     * Fling the scroll view
     *
     * @param velocityY The initial velocity in the Y direction. Positive
     * numbers mean that the finger/cursor is moving down the screen,
     * which means we want to scroll towards the top.
     */
    fun fling(velocityX: Int, velocityY: Int) {
        if (isPagingEnabled) {
            smoothScrollToPage(velocityX, velocityY)
        } else {
            if (childCount > 0) {
                val view = getChildAt(0)
                val width = width - paddingRight - paddingLeft
                val right = view.width

                val height = height - paddingBottom - paddingTop
                val bottom = view.height

                mScroller.fling(scrollX, scrollY, velocityX, velocityY, 0, max(0, right - width), 0,
                        max(0, bottom - height), width / 2, height / 2)

                ViewCompat.postInvalidateOnAnimation(this)
            }
        }
        handlePostTouchScrolling()
    }

    private fun flingWithNestedDispatch(velocityX: Int, velocityY: Int) {
        val scrollX = scrollX
        val scrollY = scrollY
        val canFling = (scrollY > 0 || velocityY > 0) && (scrollY < scrollRangeY || velocityY < 0)
                || (scrollX > 0 || velocityX > 0) && (scrollX < scrollRangeX || velocityX < 0)
        if (!dispatchNestedPreFling(velocityX.toFloat(), velocityY.toFloat())) {
            dispatchNestedFling(velocityX.toFloat(), velocityY.toFloat(), canFling)
            if (canFling) {
                fling(velocityX, velocityY)
            }
        }
    }

    private fun endDrag() {
        mIsBeingDragged = false

        recycleVelocityTracker()
        stopNestedScroll()

        if (mEdgeGlowTop != null) {
            mEdgeGlowTop!!.onRelease()
            mEdgeGlowBottom!!.onRelease()
        }
        if (mEdgeGlowLeft != null) {
            mEdgeGlowLeft!!.onRelease()
            mEdgeGlowRight!!.onRelease()
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * This version also clamps the scrolling to the bounds of our child.
     */
    override fun scrollTo(x: Int, y: Int) {
        // we rely on the fact the View.scrollBy calls scrollTo.
        if (childCount > 0) {
            val child = getChildAt(0)
            val x = clamp(x, width - paddingRight - paddingLeft, child.width)
            val y = clamp(y, height - paddingBottom - paddingTop, child.height)
            if (x != scrollX || y != scrollY) {
                super.scrollTo(x, y)
            }
        }
    }

    private fun ensureGlows() {
        if (overScrollMode != OVER_SCROLL_NEVER) {
            val context = context
            if (mEdgeGlowTop == null && scrollRangeY > 0) {
                mEdgeGlowTop = EdgeEffect(context)
                mEdgeGlowBottom = EdgeEffect(context)
            }
            if (mEdgeGlowLeft == null && scrollRangeX > 0) {
                mEdgeGlowLeft = EdgeEffect(context)
                mEdgeGlowRight = EdgeEffect(context)
            }
        } else {
            mEdgeGlowTop = null
            mEdgeGlowBottom = null
            mEdgeGlowLeft = null
            mEdgeGlowRight = null
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val edgeGlowTop = mEdgeGlowTop
        val scrollX = scrollX
        val scrollY = scrollY
        if (edgeGlowTop != null) {
            val paddingLeft = paddingLeft
            val paddingRight = paddingRight

            if (!edgeGlowTop.isFinished) {
                val restoreCount = canvas.save()
                val width = width - paddingLeft - paddingRight

                canvas.translate((paddingLeft + scrollX).toFloat(), Math.min(0, scrollY).toFloat())
                edgeGlowTop.setSize(width, height)
                if (edgeGlowTop.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                canvas.restoreToCount(restoreCount)
            }
            val edgeGlowBottom = mEdgeGlowBottom!!
            if (!edgeGlowBottom.isFinished) {
                val restoreCount = canvas.save()
                val width = width - paddingLeft - paddingRight
                val height = height

                canvas.translate((-width + paddingLeft + scrollX).toFloat(),
                        (max(scrollRangeY, scrollY) + height).toFloat())
                canvas.rotate(180f, width.toFloat(), 0f)
                edgeGlowBottom.setSize(width, height)
                if (edgeGlowBottom.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                canvas.restoreToCount(restoreCount)
            }
        }
        val edgeGlowLeft = mEdgeGlowLeft
        if (edgeGlowLeft != null) {
            val paddingTop = paddingTop
            val paddingBottom = paddingBottom

            if (!edgeGlowLeft.isFinished) {
                val restoreCount = canvas.save()
                val height = height - paddingTop - paddingBottom
                canvas.rotate(270f)
                canvas.translate(-height + paddingTop.toFloat() - scrollY, min(0, scrollX).toFloat())
                edgeGlowLeft.setSize(height, width)
                if (edgeGlowLeft.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                canvas.restoreToCount(restoreCount)
            }
            val edgeGlowRight = mEdgeGlowRight!!
            if (!edgeGlowRight.isFinished) {
                val restoreCount = canvas.save()
                val width = width
                val height = height - paddingTop - paddingBottom

                canvas.rotate(90f)
                canvas.translate(scrollY - paddingTop.toFloat(), -(max(scrollRangeX, scrollX) + width).toFloat())
                edgeGlowRight.setSize(height, width)
                if (edgeGlowRight.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                canvas.restoreToCount(restoreCount)
            }
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)
        mSavedState = state
        requestLayout()
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.scrollPositionX = scrollX
        ss.scrollPositionY = scrollY
        return ss
    }

    internal class SavedState : View.BaseSavedState {
        var scrollPositionX = 0
        var scrollPositionY = 0

        constructor(superState: Parcelable) : super(superState)

        constructor(source: Parcel) : super(source) {
            scrollPositionX = source.readInt()
            scrollPositionY = source.readInt()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(scrollPositionX)
            dest.writeInt(scrollPositionY)
        }

        override fun toString(): String {
            return ("HorizontalScrollView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " scrollPositionX=$scrollPositionX scrollPositionY=$scrollPositionY }")
        }

        companion object {

            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(src: Parcel): SavedState {
                    return SavedState(src)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    inner class PostTouchRunnable : Runnable {
        private var mSnappingToPage = false

        override fun run() {
            if (mActivelyScrolling) {
                mActivelyScrolling = false
                ViewCompat.postOnAnimationDelayed(this@SuperScrollView, this, MOMENTUM_DELAY)

            } else {
                var doneWithAllScrolling = true
                if (isPagingEnabled && !mSnappingToPage) {
                    mSnappingToPage = true
                    smoothScrollToPage()
                    doneWithAllScrolling = false
                }
                if (doneWithAllScrolling) {
                    mPostTouchRunnable = null
                } else {
                    ViewCompat.postOnAnimationDelayed(this@SuperScrollView, this, MOMENTUM_DELAY)
                }
            }
        }
    }

    internal class AccessibilityDelegate : AccessibilityDelegateCompat() {
        override fun performAccessibilityAction(host: View, action: Int, arguments: Bundle): Boolean {
            if (super.performAccessibilityAction(host, action, arguments)) {
                return true
            }
            val nsvHost = host as SuperScrollView
            if (!nsvHost.isEnabled) {
                return false
            }
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> {
                    run {
                        val viewportWidth = nsvHost.width - nsvHost.paddingLeft - nsvHost.paddingRight
                        val targetScrollX = min(nsvHost.scrollX + viewportWidth, nsvHost.scrollRangeX)
                        val viewportHeight = (nsvHost.height - nsvHost.paddingBottom
                                - nsvHost.paddingTop)
                        val targetScrollY = Math.min(nsvHost.scrollY + viewportHeight,
                                nsvHost.scrollRangeY)
                        if (targetScrollY != nsvHost.scrollY || targetScrollX != nsvHost.scrollX) {
                            nsvHost.smoothScrollTo(targetScrollX, targetScrollY)
                            return true
                        }
                    }
                    return false
                }
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
                    run {
                        val viewportWidth = nsvHost.width - nsvHost.paddingLeft - nsvHost.paddingRight
                        val targetScrollX = max(nsvHost.scrollX - viewportWidth, 0)
                        val viewportHeight = (nsvHost.height - nsvHost.paddingBottom
                                - nsvHost.paddingTop)
                        val targetScrollY = max(nsvHost.scrollY - viewportHeight, 0)
                        if (targetScrollY != nsvHost.scrollY || targetScrollX != nsvHost.scrollX) {
                            nsvHost.smoothScrollTo(targetScrollX, targetScrollY)
                            return true
                        }
                    }
                    return false
                }
            }
            return false
        }

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val nsvHost = host as SuperScrollView
            info.className = ScrollView::class.java.name
            if (nsvHost.isEnabled) {
                val scrollRangeX = nsvHost.scrollRangeX
                val scrollRangeY = nsvHost.scrollRangeY
                if (scrollRangeX > 0 || scrollRangeY > 0) {
                    info.isScrollable = true
                    if (nsvHost.scrollX > 0 || nsvHost.scrollY > 0) {
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
                    }
                    if (nsvHost.scrollX < scrollRangeX || nsvHost.scrollY < scrollRangeY) {
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                    }
                }
            }
        }

        override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) {
            super.onInitializeAccessibilityEvent(host, event)
            val nsvHost = host as SuperScrollView
            event.className = ScrollView::class.java.name
            val record = AccessibilityEventCompat.asRecord(event)
            val scrollRangeX = nsvHost.scrollRangeX
            val scrollRangeY = nsvHost.scrollRangeY
            val scrollable = scrollRangeX > 0 || scrollRangeY > 0
            record.isScrollable = scrollable
            record.scrollX = nsvHost.scrollX
            record.scrollY = nsvHost.scrollY
            record.maxScrollX = scrollRangeX
            record.maxScrollY = scrollRangeY
        }
    }

    companion object {
        const val ANIMATED_SCROLL_GAP = 250

        const val MAX_SCROLL_FACTOR = 0.5f

        private const val TAG = "SuperScrollView"

        const val MOMENTUM_DELAY = 20L

        /**
         * Sentinel value for no current active pointer.
         * Used by [.mActivePointerId].
         */
        private const val INVALID_POINTER = -1

        private val ACCESSIBILITY_DELEGATE = AccessibilityDelegate()

        /**
         * Return true if child is a descendant of parent, (or equal to the parent).
         */
        private fun isViewDescendantOf(child: View, parent: View): Boolean {
            if (child === parent) {
                return true
            }

            val theParent = child.parent
            return theParent is ViewGroup && isViewDescendantOf(theParent as View, parent)
        }

        private fun clamp(n: Int, my: Int, child: Int): Int {
            if (my >= child || n < 0) {
                /* my >= child is this case:
             *                    |--------------- me ---------------|
             *     |------ child ------|
             * or
             *     |--------------- me ---------------|
             *            |------ child ------|
             * or
             *     |--------------- me ---------------|
             *                                  |------ child ------|
             *
             * n < 0 is this case:
             *     |------ me ------|
             *                    |-------- child --------|
             *     |-- mScrollX --|
             */
                return 0
            }
            return if (my + n > child) {
                /* this case:
             *                    |------ me ------|
             *     |------ child ------|
             *     |-- mScrollX --|
             */
                child - my
            } else n
        }
    }
}
