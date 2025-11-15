package org.shirakawatyu.yamibo.novel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.page.BBSPage
import org.shirakawatyu.yamibo.novel.ui.page.FavoritePage
import org.shirakawatyu.yamibo.novel.ui.page.MinePage
import org.shirakawatyu.yamibo.novel.ui.page.ReaderPage
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors
import org.shirakawatyu.yamibo.novel.ui.theme._300文学Theme
import org.shirakawatyu.yamibo.novel.ui.vm.BottomNavBarVM
import org.shirakawatyu.yamibo.novel.ui.widget.BottomNavBar
import java.net.URLDecoder
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cookies")

class MainActivity : ComponentActivity() {

    var bbsWebViewState by mutableStateOf<WebView?>(null)
        private set
    private var backgroundStopJob: Job? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        GlobalData.dataStore = applicationContext.dataStore
        GlobalData.displayMetrics = resources.displayMetrics
        window.setBackgroundDrawable(0xfffcf4cf.toInt().toDrawable())
        super.onCreate(savedInstanceState)
        bbsWebViewState = createBbsWebView(this)
        setContent {
            App(bbsWebView = bbsWebViewState)
        }
    }

    override fun onStart() {
        super.onStart()
        backgroundStopJob?.cancel()
        backgroundStopJob = null

        if (bbsWebViewState == null) {
            bbsWebViewState = createBbsWebView(this).apply {
                loadUrl("https://bbs.yamibo.com/forum.php")
            }
        } else {
            bbsWebViewState?.onResume()
            bbsWebViewState?.resumeTimers()
        }
    }

    override fun onStop() {
        super.onStop()

        bbsWebViewState?.onPause()
        bbsWebViewState?.pauseTimers()

        backgroundStopJob?.cancel()
        backgroundStopJob = mainScope.launch {
            delay(600_000L) // 10分钟
            bbsWebViewState?.destroy()
            bbsWebViewState = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        bbsWebViewState?.destroy()
        bbsWebViewState = null
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun createBbsWebView(context: Context): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
        }
        webViewClient = GlobalData.webViewClient
        webChromeClient = GlobalData.webChromeClient
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun App(bbsWebView: WebView?) {
    val isAppInitialized = GlobalData.isAppInitialized

    LaunchedEffect(Unit) {
        if (!GlobalData.isAppInitialized) {
            try {
                GlobalData.currentCookie = GlobalData.cookieFlow.first()
            } catch (e: Exception) {
                // 如果加载失败，就当用户未登录处理
                GlobalData.currentCookie = ""
            } finally {
                // 无论成功还是失败，都必须设置为true来解锁UI
                GlobalData.isAppInitialized = true
            }
        }
    }

    _300文学Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            if (isAppInitialized) {
                Box(contentAlignment = Alignment.TopCenter) {
                    val navController = rememberNavController()
                    val stateOwner = LocalViewModelStoreOwner.current
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val bottomNavBarVM: BottomNavBarVM = viewModel(stateOwner!!)
                    val context = LocalContext.current
                    val pageList = listOf("FavoritePage", "BBSPage", "MinePage")
                    val selectedItemIndex = pageList.indexOf(currentRoute).coerceAtLeast(0)
                    Column(verticalArrangement = Arrangement.SpaceBetween) {
                        NavHost(
                            modifier = Modifier.weight(1f),
                            navController = navController,
                            startDestination = "FavoritePage"
                        ) {
                            composable("FavoritePage") {
                                FavoritePage(
                                    viewModel(
                                        stateOwner,
                                        factory = ViewModelFactory(context.applicationContext)
                                    ),
                                    navController
                                )
                            }
                            composable("BBSPage") {
                                if (bbsWebView != null) {
                                    BBSPage(
                                        webView = bbsWebView,
                                        isSelected = selectedItemIndex == 1,
                                        cookieFlow = GlobalData.cookieFlow,
                                        navController = navController
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = YamiboColors.primary)
                                    }
                                }
                            }
                            composable("MinePage") {
                                MinePage(
                                    isSelected = selectedItemIndex == 2,
                                    navController = navController
                                )
                            }

                            composable(
                                "ReaderPage/{passageUrl}",
                                arguments = listOf(navArgument("passageUrl") {
                                    type = NavType.StringType
                                })
                            ) {
                                it.arguments?.getString("passageUrl")?.let { url ->
                                    ReaderPage(
                                        url = URLDecoder.decode(url, "utf-8"),
                                        navController = navController
                                    )
                                }
                            }
                        }
                        if (currentRoute != "ReaderPage/{passageUrl}") {
                            BottomNavBar(navController, currentRoute, bottomNavBarVM)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = YamiboColors.primary)
                }
            }
        }
    }
}