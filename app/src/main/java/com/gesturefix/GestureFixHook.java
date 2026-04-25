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

            // Hook getGestureStubWindowParam，在返回后把 y 改为 0，height 改为满屏
            XposedHelpers.findAndHookMethod(
                gestureStubViewClass,
                "getGestureStubWindowParam",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.getResult();
                        if (lp == null) return;

                        // 只处理左右手势条（GestureStubLeft / GestureStubRight）
                        // 不处理底部的 GestureStubBottom
                        String title = lp.getTitle() != null ? lp.getTitle().toString() : "";
                        if (!title.contains("GestureStubLeft") && !title.contains("GestureStubRight")) return;

                        XposedBridge.log("[GestureFix] Before fix: y=" + lp.y + " h=" + lp.height + " title=" + title);

                        // 获取屏幕真实高度
                        Object thiz = param.thisObject;
                        int screenHeight = (int) XposedHelpers.getIntField(thiz, "mScreenHeight");
                        int screenWidth  = (int) XposedHelpers.getIntField(thiz, "mScreenWidth");
                        int rotation     = (int) XposedHelpers.getIntField(thiz, "mRotation");

                        // rotation 0/2 竖屏，短边是 screenWidth，长边是 screenHeight
                        // rotation 1/3 横屏，屏幕实际高度是 screenWidth（因为 initScreenSizeAndDensity 始终把短边给 mScreenWidth）
                        int fullHeight = (rotation == 0 || rotation == 2) ? screenHeight : screenWidth;

                        lp.y = 0;
                        lp.height = fullHeight;

                        XposedBridge.log("[GestureFix] After fix:  y=" + lp.y + " h=" + lp.height + " fullHeight=" + fullHeight);

                        param.setResult(lp);
                    }
                }
            );

            XposedBridge.log("[GestureFix] Hook registered successfully");

        } catch (Throwable t) {
            XposedBridge.log("[GestureFix] Hook failed: " + t.getMessage());
        }
    }
}
