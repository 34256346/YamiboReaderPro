package org.shirakawatyu.yamibo.novel

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.ui.page.BBSPage
import org.shirakawatyu.yamibo.novel.ui.page.FavoritePage
import org.shirakawatyu.yamibo.novel.ui.page.MinePage
import org.shirakawatyu.yamibo.novel.ui.page.ReaderPage
import org.shirakawatyu.yamibo.novel.ui.theme._300文学Theme
import org.shirakawatyu.yamibo.novel.ui.widget.BottomNavBar
import java.net.URLDecoder


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cookies")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        GlobalData.dataStore = applicationContext.dataStore
        GlobalData.displayMetrics = resources.displayMetrics

        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

@Composable
fun App() {
    val navController = rememberNavController()
    val stateOwner = LocalViewModelStoreOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    _300文学Theme {
        Box(contentAlignment = Alignment.TopCenter) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween) {
                    NavHost(
                        modifier = Modifier.weight(1f),
                        navController = navController,
                        startDestination = "FavoritePage"
                    ) {
                        composable("FavoritePage") {
                            FavoritePage(
                                viewModel(stateOwner!!),
                                navController
                            )
                        }
                        composable("BBSPage") { BBSPage() }
                        composable("MinePage") { MinePage() }
                        composable(
                            "ReaderPage/{passageUrl}",
                            arguments = listOf(navArgument("passageUrl") {
                                type = NavType.StringType
                            })
                        ) {
                            it.arguments?.getString("passageUrl")
                                ?.let { it1 ->
                                    ReaderPage(url = URLDecoder.decode(it1, "utf-8"))
                                }
                        }
                    }
                    if (currentRoute != "ReaderPage/{passageUrl}") {
                        BottomNavBar(navController)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    App()
}