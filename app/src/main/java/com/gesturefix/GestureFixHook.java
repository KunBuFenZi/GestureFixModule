package com.gesturefix;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
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
            Class<?> gestureStubViewClass = XposedHelpers.findClass(
                "com.miui.home.recents.GestureStubView",
                lpparam.classLoader
            );

            // Hook 构造函数
            XposedHelpers.findAndHookConstructor(
                gestureStubViewClass,
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("[GestureFix] GestureStubView constructed, scheduling fix");
                        final Object thiz = param.thisObject;
                        View view = (View) thiz;
                        view.postDelayed(() -> {
                            try {
                                XposedHelpers.callMethod(thiz, "adaptRotation", false);
                            } catch (Throwable t) {
                                XposedBridge.log("[GestureFix] adaptRotation error: " + t.getMessage());
                            }
                            fixLayoutParams(thiz);
                            try {
                                XposedHelpers.callMethod(thiz, "resetRenderProperty", "GestureFixForce");
                            } catch (Throwable t) {
                                XposedBridge.log("[GestureFix] force reset error: " + t.getMessage());
                            }
                        }, 800);
                    }
                }
            );

            // Hook getGestureStubWindowParam
            XposedHelpers.findAndHookMethod(
                gestureStubViewClass,
                "getGestureStubWindowParam",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        fixLayoutParams(param.thisObject);
                    }
                }
            );

            // Hook resetRenderProperty
            XposedHelpers.findAndHookMethod(
                gestureStubViewClass,
                "resetRenderProperty",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        fixLayoutParams(param.thisObject);
                    }
                }
            );

            // Hook setGestureStubPosition
            XposedHelpers.findAndHookMethod(
                gestureStubViewClass,
                "setGestureStubPosition",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        fixLayoutParams(param.thisObject);
                    }
                }
            );

            XposedBridge.log("[GestureFix] Hook registered successfully");

        } catch (Throwable t) {
            XposedBridge.log("[GestureFix] Hook failed: " + t.getMessage());
        }
    }

    private void fixLayoutParams(Object thiz) {
        try {
            WindowManager.LayoutParams lp =
                (WindowManager.LayoutParams) XposedHelpers.getObjectField(thiz, "mGestureStubParams");
            if (lp == null) return;

            String title = lp.getTitle() != null ? lp.getTitle().toString() : "";
            if (!title.contains("GestureStubLeft") && !title.contains("GestureStubRight")) return;

            boolean isLeft = title.contains("GestureStubLeft");
            int screenHeight = XposedHelpers.getIntField(thiz, "mScreenHeight");
            int screenWidth  = XposedHelpers.getIntField(thiz, "mScreenWidth");
            int rotation     = XposedHelpers.getIntField(thiz, "mRotation");
            int stubSize     = XposedHelpers.getIntField(thiz, "mGestureStubSize");

            XposedBridge.log("[GestureFix] Before fix: x=" + lp.x + " y=" + lp.y
                + " w=" + lp.width + " h=" + lp.height
                + " gravity=" + lp.gravity
                + " title=" + title + " rotation=" + rotation);

            if (rotation == 0 || rotation == 2) {
                // 竖屏：左右手势条，竖向贴边
                lp.width = stubSize;
                lp.height = screenHeight;
                lp.x = 0;
                lp.y = 0;
                lp.gravity = isLeft
                    ? (Gravity.LEFT | Gravity.TOP)
                    : (Gravity.RIGHT | Gravity.TOP);
            } else {
                // 横屏 (rotation=1 或 3)：左右手势条，横向时屏幕宽高对调
                // screenWidth 是短边，screenHeight 是长边
                // 横屏时实际显示宽=screenHeight，高=screenWidth
                lp.width = stubSize;
                lp.height = screenHeight; // 横屏时高度用长边
                lp.x = 0;
                lp.y = 0;
                lp.gravity = isLeft
                    ? (Gravity.LEFT | Gravity.TOP)
                    : (Gravity.RIGHT | Gravity.TOP);
            }

            XposedBridge.log("[GestureFix] After fix:  x=" + lp.x + " y=" + lp.y
                + " w=" + lp.width + " h=" + lp.height
                + " gravity=" + lp.gravity);

        } catch (Throwable t) {
            XposedBridge.log("[GestureFix] fixLayoutParams error: " + t.getMessage());
        }
    }
}