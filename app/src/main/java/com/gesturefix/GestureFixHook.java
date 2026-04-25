package com.gesturefix;

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

            // Hook getGestureStubWindowParam，返回后修正参数
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

            // Hook resetRenderProperty，每次窗口刷新前也修正
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

            // Hook setGestureStubPosition，位置变化时也修正
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
