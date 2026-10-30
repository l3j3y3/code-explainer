package com.l3j3y3.codeexplainer;

import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 15+（本工程 targetSdk 36）强制 edge-to-edge：状态栏变透明、盖在 WebView 上面。
        // 此时 @capacitor/status-bar 的 setOverlaysWebView(false) 与 setStatusBarColor 都会被系统忽略
        // （StatusBar.java 里 shouldSetStatusBarColor() 在 API > 35 直接返回 false），
        // 而 Android WebView 的 env(safe-area-inset-top) 在这套布局下是 0，
        // 结果就是顶栏控件画在状态栏那一条里、点击被系统窗口吃掉。
        // 这里把系统栏占住的尺寸让出来（状态栏在上、手势条在下），高度由系统给，不写死。
        if (Build.VERSION.SDK_INT >= 35) {
            View web = getBridge() == null ? null : getBridge().getWebView();
            if (web != null) {
                // 只加在 WebView 上，不去碰 android.R.id.content：
                // Capacitor Keyboard 插件已经给 content 装了 OnApplyWindowInsetsListener
                // （Keyboard.java 里 setOnApplyWindowInsetsListener(content, ...)），
                // 抢同一个 View 会把键盘自适应弄废。
                ViewCompat.setOnApplyWindowInsetsListener(web, (v, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    // 不返回 CONSUMED：键盘插件还要靠这条链路收 IME inset。
                    return insets;
                });
                ViewCompat.requestApplyInsets(web);

                // 让出来的那条带露出父容器背景，用和顶栏/底栏同一个面板色，避免出现一条浅灰缝。
                View parent = (View) web.getParent();
                if (parent != null) {
                    parent.setBackgroundColor(getColor(R.color.appSurface));
                }
            }
        }
    }
}
