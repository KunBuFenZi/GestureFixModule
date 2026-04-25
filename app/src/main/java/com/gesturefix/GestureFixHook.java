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
            Class<?> wmImplClass = XposedHelpers.findClass(
                "android.view.WindowManagerImpl", lpparam.classLoader);

            // Hook at WindowManagerImpl level so no code in GestureStubView can override our fix.
            // beforeHookedMethod runs just before the window operation is submitted to system server.
            XC_MethodHook fixHook = new XC_MethodHook() {
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
                        int rotation     = XposedHelpers.getIntField(view, "mRotation");
                        int stubSize     = XposedHelpers.getIntField(view, "mGestureStubSize");
                        if (stubSize <= 0) stubSize = Math.min(lp.width, lp.height);

                        // mScreenWidth = short side (1840), mScreenHeight = long side (2944), constant regardless of rotation.
                        // portrait (0/2): display height = long side; landscape (1/3): display height = short side.
                        int fullHeight = (rotation == 0 || rotation == 2) ? screenHeight : screenWidth;

                        XposedBridge.log("[GestureFix] " + title
                            + " rot=" + rotation + " stubSize=" + stubSize + " fullH=" + fullHeight
                            + " before=[" + lp.x + "," + lp.y + " " + lp.width + "x" + lp.height + " g=" + lp.gravity + "]");

                        lp.width   = stubSize;
                        lp.height  = fullHeight;
                        lp.x       = 0;
                        lp.y       = 0;
                        lp.gravity = isLeft ? (Gravity.LEFT | Gravity.TOP) : (Gravity.RIGHT | Gravity.TOP);

                        XposedBridge.log("[GestureFix] fixed=[" + lp.x + "," + lp.y
                            + " " + lp.width + "x" + lp.height + " g=" + lp.gravity + "]");
                    } catch (Throwable e) {
                        XposedBridge.log("[GestureFix] fix error: " + e.getMessage());
                    }
                }
            };

            XposedHelpers.findAndHookMethod(wmImplClass, "addView",
                View.class, ViewGroup.LayoutParams.class, fixHook);
            XposedHelpers.findAndHookMethod(wmImplClass, "updateViewLayout",
                View.class, ViewGroup.LayoutParams.class, fixHook);

            XposedBridge.log("[GestureFix] hooks registered on WindowManagerImpl");

        } catch (Throwable t) {
            XposedBridge.log("[GestureFix] Hook failed: " + t.getMessage());
        }
    }
}
