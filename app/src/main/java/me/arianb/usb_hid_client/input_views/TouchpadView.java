package me.arianb.usb_hid_client.input_views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;

import me.arianb.usb_hid_client.report_senders.MouseSender;
import timber.log.Timber;

// TODO:
//  - improve touchpad click handling
//      - I want a single tap to be sent immediately, I don't want to handle double taps or anything like that
//      - However, if multiple fingers tap at once, I do want to handle that in a special way (2 = right click, 3 = middle click)
//  - address the linting issue below
@SuppressLint("ClickableViewAccessibility")
public class TouchpadView extends androidx.appcompat.widget.AppCompatTextView {
    private VelocityTracker mVelocityTracker = null;
    private static final float DEADZONE = 0.3F;

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
        // TODO: on double click and drag, send report without "release" until sending release on finger up
        GestureDetectorCompat gestureDetector = new GestureDetectorCompat(this.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent event) {
                Timber.d("onSingleTapConfirmed: %s", event);

                byte button = MouseSender.MOUSE_BUTTON_LEFT;
                mouseSender.addReport(button, (byte) 0, (byte) 0);

                return true;
            }
        });

        this.setOnTouchListener((view, motionEvent) -> {
            if (gestureDetector.onTouchEvent(motionEvent)) {
                return true;
            }

            int index = motionEvent.getActionIndex();
            int action = motionEvent.getActionMasked();
            int pointerId = motionEvent.getPointerId(index);
            byte button; // unknown at this point

            Timber.d("motionEvent %d (x, y): (%f, %f)", action, motionEvent.getX(), motionEvent.getY());

            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    final int POINTER_COUNT = motionEvent.getPointerCount();
                    Timber.d("omg there's %d pointers!!!", POINTER_COUNT);

                    switch (POINTER_COUNT) {
                        case 2:
                            button = MouseSender.MOUSE_BUTTON_RIGHT;
                            mouseSender.addReport(button, (byte) 0, (byte) 0);
                            break;
//                            case 3: // FIXME: apparently, case 2 gets triggered right before case 3 gets triggered, gotta add a little timeout ig to differentiate
//                                button = MouseSender.MOUSE_BUTTON_MIDDLE;
//                                mouseSender.addReport(button, (byte) 0, (byte) 0);
//                                break;
                    }

                    break;
                case MotionEvent.ACTION_DOWN:
                    if (mVelocityTracker == null) {
                        // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        // Reset the velocity tracker back to its initial state.
                        mVelocityTracker.clear();
                    }
                    // Add a user's movement to the tracker.
                    mVelocityTracker.addMovement(motionEvent);
                    break;
                case MotionEvent.ACTION_MOVE:
                    mVelocityTracker.addMovement(motionEvent);

                    // Compute velocity (cap it to byte because the report uses a byte per axis)
                    mVelocityTracker.computeCurrentVelocity(10, Byte.MAX_VALUE);
                    float xVelocity = mVelocityTracker.getXVelocity(pointerId);
                    float yVelocity = mVelocityTracker.getYVelocity(pointerId);
                    Timber.d("X,Y velocity: (%s,%s)", xVelocity, yVelocity);

                    // Scale up velocities < 1 in magnitude (accounting for deadzone) to allow for precise movements
                    xVelocity = scaleWithDeadzone(xVelocity);
                    yVelocity = scaleWithDeadzone(yVelocity);

                    // No button clicked (not handled in this section of code)
                    button = MouseSender.MOUSE_BUTTON_NONE;
                    byte x = (byte) xVelocity;
                    byte y = (byte) yVelocity;
                    Timber.d("NEW X,Y velocity: (%s,%s)", x, y);

                    mouseSender.addReport(button, x, y);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
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
