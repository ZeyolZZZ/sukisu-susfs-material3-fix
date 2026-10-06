package com.zeyol.susfsm3fix;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SukiSU Ultra 的 Material 3 设置页把 SUSFS 菜单嵌在了 KPM 可用性判断里面：
 *
 *   SettingsMaterial.kt
 *     if (isSusfsSupported) {
 *         if (isKpmAvailable) { ...SUSFS 菜单项... }   // 没有 else！
 *     }
 *
 * 而 isKpmAvailable 来自 rememberKpmAvailable()，其判定是
 * `ksud kpm version` 是否返回非空且不含 "Error"。KPM 不可用时该命令返回空，
 * 于是整个 SUSFS 菜单在 Material 3 下不渲染（Miuix 主题没这个嵌套，所以正常）。
 *
 * 本模块只做一件事：把 getKpmVersion() 的返回值替换成一个假版本号，
 * 使 isKpmAvailable 为 true，让 Material 3 照常渲染 SUSFS 菜单。
 * 不修改任何检测逻辑、不触碰 SUSFS 本身。
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "SusfsM3Fix";
    private static final String TARGET_PKG = "com.sukisu.ultra";
    /** KsuCli.kt 中的顶层函数编译后位于 KsuCliKt（静态方法，易钩）。 */
    private static final String CLI_CLASS = "com.sukisu.ultra.ui.util.KsuCliKt";
    private static final String METHOD = "getKpmVersion";
    /** 非空且不含 "Error" 即可让 rememberKpmAvailable() 返回 true。 */
    private static final String FAKE_VERSION = "1.0.0";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        try {
            XposedHelpers.findAndHookMethod(
                    CLI_CLASS, lpparam.classLoader, METHOD,
                    XC_MethodReplacement.returnConstant(FAKE_VERSION));
            XposedBridge.log(TAG + ": hooked " + CLI_CLASS + "." + METHOD + " -> " + FAKE_VERSION);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": primary hook failed: " + t);
            hookByNameScan(lpparam);
        }
    }

    /**
     * 兜底：某些构建可能对 KsuCliKt 做了重命名/内联。
     * 这里退一步，直接按方法名在 util 包里找一个无参静态 String 方法并替换。
     */
    private void hookByNameScan(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cli = XposedHelpers.findClass(CLI_CLASS, lpparam.classLoader);
            for (java.lang.reflect.Method m : cli.getDeclaredMethods()) {
                if (!METHOD.equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                XposedBridge.hookMethod(
                        m,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.setResult(FAKE_VERSION);
                            }
                        });
                XposedBridge.log(TAG + ": fallback hook on declared method ok");
                return;
            }
            XposedBridge.log(TAG + ": no suitable method found; SUSFS menu stays hidden");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": fallback hook failed: " + t);
        }
    }
}
