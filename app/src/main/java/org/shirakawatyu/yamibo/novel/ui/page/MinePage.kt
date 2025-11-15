package org.shirakawatyu.yamibo.novel.ui.page

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.module.YamiboWebViewClient
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors
import org.shirakawatyu.yamibo.novel.ui.widget.ReaderModeFAB
import org.shirakawatyu.yamibo.novel.util.ComposeUtil.Companion.SetStatusBarColor
import org.shirakawatyu.yamibo.novel.util.ReaderModeDetector
import java.net.URLEncoder

private val hideCommand = """
    javascript:(function() {
        var style = document.createElement('style');
        style.innerHTML = '.my { display: none !important; }';
        document.head.appendChild(style);
    })()
""".trimIndent()

/**
 * 个人中心，WebView每次访问时创建，离开时销毁
 *
 * @param isSelected 表示当前页面是否被选中，用于控制页面加载逻辑和状态更新。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MinePage(
    isSelected: Boolean,
    navController: NavController
) {
    SetStatusBarColor(YamiboColors.primary)
    val mineUrl = "https://bbs.yamibo.com/home.php?mod=space&do=profile&mycenter=1&mobile=2"
    val bbsUrl = "https://bbs.yamibo.com/?mobile=2" // 你原来的定义
    val baseBbsUrl = "https://bbs.yamibo.com/"      // 根URL
    val indexUrl = "https://bbs.yamibo.com/forum.php" // 论坛主页

    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showLoadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var timeoutJob by remember { mutableStateOf<Job?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf<String?>(null) }

    val canConvertToReader = remember(currentUrl) {
        ReaderModeDetector.canConvertToReaderMode(currentUrl)
    }
    lateinit var startLoading: (webView: WebView, url: String) -> Unit

    fun runTimeout(webView: WebView, onTimeout: () -> Unit) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(10000)
            if (isLoading) {
                onTimeout()
            }
        }
    }

    startLoading = { webView: WebView, url: String ->
        isLoading = true
        showLoadError = false
        retryCount = 0

        runTimeout(webView) {
            Log.w("MinePage", "WebView loading timed out. Retrying...")
            webView.stopLoading()
            retryCount++

            runTimeout(webView) {
                Log.e("MinePage", "Retry timed out. Giving up.")
                isLoading = false
                showLoadError = true
                webView.stopLoading()
            }
            webView.reload()
        }

        webView.loadUrl(url)
    }
    val context = LocalContext.current
    val mineWebView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                useWideViewPort = true
            }
            webChromeClient = GlobalData.webChromeClient
        }
    }

    LaunchedEffect(mineWebView, isSelected) {
        mineWebView.webViewClient = object : YamiboWebViewClient() {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                currentUrl = url
                canGoBack = view?.canGoBack() ?: false
                view?.loadUrl(hideCommand)
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                // 页面内容已可见，立即停止加载圈
                if (isLoading) {
                    timeoutJob?.cancel()
                    retryCount = 0
                    isLoading = false
                    showLoadError = false
                }
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onPageFinished(view: WebView?, url: String?) {
                timeoutJob?.cancel()
                retryCount = 0
                isLoading = false
                showLoadError = false
                super.onPageFinished(view, url)
                currentUrl = url
                if (isSelected && view != null) {
                    val currentUrl = view.url ?: ""

                    val isHomepage = currentUrl == bbsUrl ||
                            currentUrl == baseBbsUrl ||
                            currentUrl == indexUrl
                    if (isHomepage) {
                        startLoading(view, mineUrl)
                    }
                }
                canGoBack = view?.canGoBack() ?: false
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                timeoutJob?.cancel()
                retryCount = 0
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    isLoading = false
                    if (retryCount == 0) {
                        showLoadError = true
                    }
                }
            }

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                timeoutJob?.cancel()
                retryCount = 0
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    isLoading = false
                    if (retryCount == 0) {
                        showLoadError = true
                    }
                }
            }
        }

        if (isSelected && mineWebView.url == null) {
            startLoading(mineWebView, mineUrl)
        } else {
            canGoBack = mineWebView.canGoBack()
        }
    }
    LaunchedEffect(isSelected) {
        if (!isSelected) {
            timeoutJob?.cancel()
            retryCount = 0
            isLoading = false
        }
    }
    BackHandler(enabled = canGoBack) {
        mineWebView.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mineWebView },
            update = {
                canGoBack = it.canGoBack()
                currentUrl = it.url
            },
            onRelease = {
                timeoutJob?.cancel()
                (it.parent as? ViewGroup)?.removeView(it)
                it.stopLoading()
                it.destroy() // 销毁实例
            }
        )
        ReaderModeFAB(
            visible = canConvertToReader && !isLoading && !showLoadError,
            onClick = {
                currentUrl?.let { url ->
                    ReaderModeDetector.extractThreadPath(url)?.let { threadPath ->
                        val encodedPath = URLEncoder.encode(threadPath, "utf-8")
                        navController.navigate("ReaderPage/$encodedPath")
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp)
        )

        if (showLoadError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "加载失败",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "页面加载失败",
                    fontSize = 18.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "请尝试切换至其他界面再切换回来，或重启应用。",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    startLoading(mineWebView, mineWebView.url ?: mineUrl)
                }) {
                    Text("重试")
                }
            }
        }

        // 局部加载指示器
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = YamiboColors.secondary
            )
        }
    }
}
