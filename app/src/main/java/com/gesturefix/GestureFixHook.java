package com.gesturefix;

import android.content.Context;
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

            // Hook 构造函数，窗口刚创建时就修正，解决软重启后失效问题
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
                            fixLayoutParams(thiz);
                            try {
                                XposedHelpers.callMethod(thiz, "resetRenderProperty", "GestureFixForce");
                            } catch (Throwable t) {
                                XposedBridge.log("[GestureFix] force reset error: " + t.getMessage());
                            }
                        }, 500);
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

            int screenHeight = XposedHelpers.getIntField(thiz, "mScreenHeight");
            int screenWidth  = XposedHelpers.getIntField(thiz, "mScreenWidth");
            int rotation     = XposedHelpers.getIntField(thiz, "mRotation");

            int fullHeight = (rotation == 0 || rotation == 2) ? screenHeight : screenWidth;

            XposedBridge.log("[GestureFix] Before fix: y=" + lp.y + " h=" + lp.height + " title=" + title);
            lp.y = 0;
            lp.height = fullHeight;
            XposedBridge.log("[GestureFix] After fix:  y=" + lp.y + " h=" + lp.height);

        } catch (Throwable t) {
            XposedBridge.log("[GestureFix] fixLayoutParams error: " + t.getMessage());
        }
    }
}