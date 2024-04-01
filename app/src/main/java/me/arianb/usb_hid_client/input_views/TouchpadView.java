package me.arianb.usb_hid_client.input_views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.arianb.usb_hid_client.report_senders.MouseSender;
import timber.log.Timber;

// TODO: address the linting issue below
@SuppressLint("ClickableViewAccessibility")
public class TouchpadView extends androidx.appcompat.widget.AppCompatTextView {
    private VelocityTracker mVelocityTracker = null;
    private static final float DEADZONE = 0.3F;

    // Vars for tracking a single touch event, which I'm defining as: ACTION_DOWN, (anything), ACTION_UP
    private int currentPointerCount;

    public TouchpadView(@NonNull Context context) {
        super(context);
    }

    public TouchpadView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchpadView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTouchListeners(MouseSender mouseSender) {
        // TODO:
        //  - add gestures
        //      - on double click and drag, send report without "release" until sending release on finger up
        //      - on long press and drag, send report without "release" until sending release on finger up
        //      - on two finger scroll, send scroll events
        this.setOnTouchListener((view, motionEvent) -> {
            int action = motionEvent.getActionMasked();
            int index = motionEvent.getActionIndex();
            int pointerId = motionEvent.getPointerId(index);

            //Timber.d("motionEvent %d (x, y): (%f, %f)", action, motionEvent.getX(), motionEvent.getY());
            //Timber.d("current pointer count: %d (pressure: %s)", currentPointerCount, motionEvent.getPressure());

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    currentPointerCount = 1;
                    Timber.d("Action Down");

                    if (mVelocityTracker == null) {
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        mVelocityTracker.clear();
                    }
                    mVelocityTracker.addMovement(motionEvent);
                    break;
                case MotionEvent.ACTION_MOVE:
                    //Timber.d("Action Move");
                    mVelocityTracker.addMovement(motionEvent);

                    // Compute velocity (cap it to byte because the report uses a byte per axis)
                    mVelocityTracker.computeCurrentVelocity(10, Byte.MAX_VALUE);
                    float xVelocity = mVelocityTracker.getXVelocity(pointerId);
                    float yVelocity = mVelocityTracker.getYVelocity(pointerId);

                    // Scale up velocities < 1 in magnitude (accounting for deadzone) to allow for precise movements
                    xVelocity = scaleWithDeadzone(xVelocity);
                    yVelocity = scaleWithDeadzone(yVelocity);

                    byte x = (byte) xVelocity;
                    byte y = (byte) yVelocity;

                    mouseSender.move(x, y);
                    break;
                case MotionEvent.ACTION_UP:
                    Timber.d("Action Up (max pointer count = %d)", currentPointerCount);

                    final long pointerDownTimeMillis = motionEvent.getEventTime() - motionEvent.getDownTime();
                    Timber.d("Pointer was down for %d milliseconds", pointerDownTimeMillis);

                    // If user has been holding down for a while, don't send any clicks, they're probably dragging
                    if (pointerDownTimeMillis > 300) {
                        break;
                    }

                    // TODO: if finger has traveled too far, don't send any click? I already have the timeout thing though.

                    switch (currentPointerCount) {
                        case 1:
                            mouseSender.click(MouseSender.MOUSE_BUTTON_LEFT);
                            break;
                        case 2:
                            mouseSender.click(MouseSender.MOUSE_BUTTON_RIGHT);
                            break;
                        case 3:
                            mouseSender.click(MouseSender.MOUSE_BUTTON_MIDDLE);
                            break;
                    }

                    currentPointerCount = 0;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    currentPointerCount++;
                    //Timber.d("Action Pointer Down (pointer count = %s)", motionEvent.getPointerCount());

                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    Timber.d("Action Pointer Up");

                    // this pointer decrement is conceptually happening, but I'm not doing it because I want ACTION_UP
                    // to use the max pointer count.
                    // currentPointerCount--;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    Timber.d("Action Cancel");
                    mVelocityTracker.recycle(); // Return a VelocityTracker object back to be re-used by others.
                    currentPointerCount = 0;
                    break;
            }
            return true;

        });
    }

    private float scaleWithDeadzone(float inputVelocity) {
        if (Math.abs(inputVelocity) != 0 && Math.abs(inputVelocity) > DEADZONE) {
            if (inputVelocity < 0) {
                return (float) Math.floor(inputVelocity);
            } else {
                return (float) Math.ceil(inputVelocity);
            }
        } else {
            return inputVelocity;
        }
    }
}
