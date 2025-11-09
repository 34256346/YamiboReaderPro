package org.shirakawatyu.yamibo.novel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cookies")

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        GlobalData.dataStore = applicationContext.dataStore
        GlobalData.displayMetrics = resources.displayMetrics
        window.setBackgroundDrawable(0xfffcf4cf.toInt().toDrawable())
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun App() {
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
                    val context = LocalContext.current.applicationContext
                    val bbsWebView = remember {
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(Color.TRANSPARENT)
                            setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                            settings.apply {
                                javaScriptEnabled = true
                                useWideViewPort = true
                            }
                            webViewClient = GlobalData.webViewClient
                            webChromeClient = GlobalData.webChromeClient
                        }
                    }
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
                                BBSPage(
                                    bbsWebView,
                                    isSelected = selectedItemIndex == 1,
                                    cookieFlow = GlobalData.cookieFlow,
                                    navController = navController
                                )
                            }
                            composable("MinePage") {
                                MinePage(
                                    isSelected = selectedItemIndex == 2
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

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    App()
}