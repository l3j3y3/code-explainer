package com.l3j3y3.codeexplainer;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

/**
 * Android 15+（本工程 targetSdk 36）强制 edge-to-edge：状态栏透明地盖在 WebView 上，
 * 而下面两件事让「让它自己排版」这条路走不通：
 *
 * 1. WebView 的 padding 不移动它渲染的内容（Chromium 用整个 surface 画），
 *    所以 v1.1 里给 WebView setPadding 的做法在真机上等于没做；
 * 2. WebView 的 env(safe-area-inset-*) 只报 display cutout，从不报系统栏，
 *    没有挖孔的机器上那几个值恒为 0 —— CSS 里写 env() 也拿不到状态栏高度。
 *
 * 所以这里改成「推数据」：把 systemBars | displayCutout 的尺寸换算成 CSS px，
 * 作为行内样式写到 documentElement 的 --safe-area-* 上，让网页自己用 CSS 让位。
 * 行内样式天然压过样式表里 :root 的 env() 兜底，不需要 !important。
 */
public class MainActivity extends BridgeActivity {

    /** 上一次推给网页的值，避免重复 evaluateJavascript；页面重建时会清空以强制重推。 */
    private String lastInsetsKey;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 先登记自定义插件，再交给 BridgeActivity 启动桥接
        registerPlugin(FolderSourcePlugin.class);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT < 35) return; // 老系统维持原样：状态栏不透明，内容本来就排在它下面

        // 明确声明「内容按 insets 自己排」。targetSdk 35+ 本来就强制这样，写出来是为了不依赖平台默认值。
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WebView web = getBridge() == null ? null : getBridge().getWebView();
        if (web == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(web, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            pushSafeArea((WebView) v, bars);
            // 不返回 CONSUMED：Capacitor Keyboard 插件还要靠这条链路收 IME inset。
            return insets;
        });

        // 页面加载完/旋转/键盘都会引发 layout；每次 layout 强制重推一遍，
        // 因为 WebView 重载后行内变量会丢，而我们没法直接观测「丢」这件事。
        web.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> rePush((WebView) v));

        rePush(web);
        // 首帧还没加载完时的兜底：document 真正就绪后再补两次
        web.postDelayed(() -> rePush(web), 900);
        web.postDelayed(() -> rePush(web), 2400);
    }

    @Override
    public void onResume() {
        super.onResume();
        WebView web = getBridge() == null ? null : getBridge().getWebView();
        if (web != null) rePush(web);
    }

    private void rePush(WebView web) {
        lastInsetsKey = null;
        ViewCompat.requestApplyInsets(web);
    }

    private void pushSafeArea(WebView web, Insets bars) {
        float density = web.getResources().getDisplayMetrics().density;
        if (density <= 0f) return;

        String key = bars.top + "/" + bars.bottom + "/" + bars.left + "/" + bars.right;
        if (key.equals(lastInsetsKey)) return;
        lastInsetsKey = key;

        String top = Math.round(bars.top / density) + "px";
        String bottom = Math.round(bars.bottom / density) + "px";
        String left = Math.round(bars.left / density) + "px";
        String right = Math.round(bars.right / density) + "px";

        String js = "(function(){var s=document.documentElement&&document.documentElement.style;if(!s)return;"
                + "s.setProperty('--safe-area-top','" + top + "');"
                + "s.setProperty('--safe-area-bottom','" + bottom + "');"
                + "s.setProperty('--safe-area-left','" + left + "');"
                + "s.setProperty('--safe-area-right','" + right + "');"
                + "window.__ceInsets={top:'" + top + "',bottom:'" + bottom + "',left:'" + left + "',right:'" + right + "'};"
                + "})()";
        web.evaluateJavascript(js, null);
    }
}
