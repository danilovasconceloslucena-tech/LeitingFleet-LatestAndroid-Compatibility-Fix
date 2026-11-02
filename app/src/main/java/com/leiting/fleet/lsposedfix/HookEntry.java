package com.leiting.fleet.lsposedfix;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "TT1Android16Fix";
    private static final String TARGET_PKG = "com.turner.ttgfigures";

    private static final String UNITY_HANDLER =
            "bitter.jnibridge.JNIBridge$a";

    private Method legacyTwoArg;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        try {
            legacyTwoArg = ServiceConnection.class.getMethod(
                    "onServiceConnected",
                    ComponentName.class,
                    IBinder.class
            );
        } catch (NoSuchMethodException e) {
            XposedBridge.log(TAG + ": FATAL - legacy method missing: " + e);
            return;
        }

        if (!hookUnityHandler(lpparam)) {
            hookGenericProxyDispatch();
        }
    }

    private boolean hookUnityHandler(LoadPackageParam lpparam) {
        Class<?> handler = XposedHelpers.findClassIfExists(
                UNITY_HANDLER,
                lpparam.classLoader
        );

        if (handler == null) {
            XposedBridge.log(TAG + ": Unity handler not found; using fallback");
            return false;
        }

        Set<XC_MethodHook.Unhook> unhooks =
                XposedBridge.hookAllMethods(
                        handler,
                        "invoke",
                        rewriteHook
                );

        XposedBridge.log(TAG + ": hooked " + unhooks.size() + " methods");
        return !unhooks.isEmpty();
    }

    private void hookGenericProxyDispatch() {
        try {
            Set<XC_MethodHook.Unhook> unhooks =
                    XposedBridge.hookAllMethods(
                            java.lang.reflect.Proxy.class,
                            "invoke",
                            rewriteHook
                    );

            XposedBridge.log(TAG + ": generic Proxy hook installed: "
                    + unhooks.size());

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": generic Proxy hook failed: " + t);
        }
    }

    private final XC_MethodHook rewriteHook = new XC_MethodHook() {

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {

            final Object[] a = param.args;

            Method method = null;
            int methodIdx = -1;
            int argsIdx = -1;

            for (int i = 0; i < a.length; i++) {

                if (method == null && a[i] instanceof Method) {
                    method = (Method) a[i];
                    methodIdx = i;

                } else if (argsIdx == -1 && a[i] instanceof Object[]) {
                    argsIdx = i;
                }
            }

            if (method == null || argsIdx == -1) {
                return;
            }

            if (!"onServiceConnected".equals(method.getName())) {
                return;
            }

            final Class<?>[] pt = method.getParameterTypes();

            if (pt.length != 3
                    || pt[0] != ComponentName.class
                    || pt[1] != IBinder.class) {
                return;
            }

            final Object[] callArgs = (Object[]) a[argsIdx];

            if (callArgs == null || callArgs.length < 2) {
                return;
            }

            a[methodIdx] = legacyTwoArg;
            a[argsIdx] = new Object[] {
                    callArgs[0],
                    callArgs[1]
            };

            XposedBridge.log(TAG
                    + ": rewrote 3-arg onServiceConnected to 2-arg");
        }
    };
                        }
