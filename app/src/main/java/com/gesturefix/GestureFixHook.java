package com.gesturefix;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class GestureFixHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.miui.home")) return;
        XposedBridge.log("[GestureFix] Hooked into com.miui.home");

        try {
            Class<?> gestureStubClass = XposedHelpers.findClass(
                "com.miui.home.recents.GestureStubView", lpparam.classLoader);
            Class<?> wmImplClass = XposedHelpers.findClass(
                "android.view.WindowManagerImpl", lpparam.classLoader);

            // ── KEY FIX ──────────────────────────────────────────────────────────
            // initGestureEdgeSize() reads mRotation to calculate the gesture
            // detection pixel range, then passes it to GesturesBackController.
            // There is no setGestureEdgeWidth — the registration happens inside
            // initGestureEdgeSize. Fixing mRotation before it runs makes the
            // entire calculation produce correct values.
            XC_MethodHook rotationFix = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    android.view.Display display = view.getDisplay();
                    if (display == null) return;
                    int actual = display.getRotation();
                    int stored = XposedHelpers.getIntField(view, "mRotation");
                    XposedBridge.log("[GestureFix] " + param.method.getName()
                        + " mRotation=" + stored + " actual=" + actual);
                    if (actual != stored) {
                        XposedHelpers.setIntField(view, "mRotation", actual);
                        XposedBridge.log("[GestureFix] mRotation fixed: " + stored + " → " + actual);
                    }
                }
            };

            XposedHelpers.findAndHookMethod(gestureStubClass, "initGestureEdgeSize", rotationFix);
            XposedHelpers.findAndHookMethod(gestureStubClass, "rotateGesture", rotationFix);

            // ── VISUAL FIX ───────────────────────────────────────────────────────
            // Fix window layout so the transparent overlay position matches reality.
            XC_MethodHook wmHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(param.args[1] instanceof WindowManager.LayoutParams)) return;
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.args[1];
                    CharSequence titleCs = lp.getTitle();
                    if (titleCs == null) return;
                    String title = titleCs.toString();
                    boolean isLeft  = title.contains("GestureStubLeft");
                    boolean isRight = title.contains("GestureStubRight");
                    if (!isLeft && !isRight) return;

                    try {
                        View view = (View) param.args[0];
                        int screenHeight = XposedHelpers.getIntField(view, "mScreenHeight");
                        int screenWidth  = XposedHelpers.getIntField(view, "mScreenWidth");
                        int stubSize     = XposedHelpers.getIntField(view, "mGestureStubSize");
                        if (stubSize <= 0) stubSize = Math.min(lp.width, lp.height);

                        android.view.Display display = view.getDisplay();
                        int rotation = (display != null) ? display.getRotation()
                                                         : XposedHelpers.getIntField(view, "mRotation");
                        int fullHeight = (rotation == 0 || rotation == 2) ? screenHeight : screenWidth;

                        lp.width   = stubSize;
                        lp.height  = fullHeight;
                        lp.x       = 0;
                        lp.y       = 0;
                        lp.gravity = isLeft ? (Gravity.LEFT | Gravity.TOP) : (Gravity.RIGHT | Gravity.TOP);

                        // Fix mRotation and re-run initGestureEdgeSize so GesturesBackController
                        // registers the correct pixel range for gesture detection.
                        // initGestureEdgeSize was called before addView (getDisplay()=null then),
                        // so we must call it again now that the view has a valid display.
                        int storedRot = XposedHelpers.getIntField(view, "mRotation");
                        if (storedRot != rotation) {
                            XposedHelpers.setIntField(view, "mRotation", rotation);
                            XposedBridge.log("[GestureFix] mRotation fixed: " + storedRot + " → " + rotation);
                        }
                        XposedHelpers.callMethod(view, "initGestureEdgeSize");

                        XposedBridge.log("[GestureFix] WM " + title
                            + " rot=" + rotation + " fixed=[0,0 " + lp.width + "x" + lp.height + "]");
                    } catch (Throwable e) {
                        XposedBridge.log("[GestureFix] WM error: " + e.getMessage());
                    }
                }
            };

            XposedHelpers.findAndHookMethod(wmImplClass, "addView",
                View.class, ViewGroup.LayoutParams.class, wmHook);
            XposedHelpers.findAndHookMethod(wmImplClass, "updateViewLayout",
                View.class, ViewGroup.LayoutParams.class, wmHook);

            XposedBridge.log("[GestureFix] hooks registered");

        } catch (Throwable t) {
            XposedBridge.log("[GestureFix] Hook failed: " + t.getMessage());
        }
    }
}
