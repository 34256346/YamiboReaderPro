// novel/ui/vm/FavoriteVM.kt

package org.shirakawatyu.yamibo.novel.ui.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.FavoriteApi
import org.shirakawatyu.yamibo.novel.ui.state.FavoriteState
import org.shirakawatyu.yamibo.novel.util.CookieUtil
import org.shirakawatyu.yamibo.novel.util.FavoriteUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder

class FavoriteVM : ViewModel() {
    private val _uiState = MutableStateFlow(FavoriteState())
    val uiState = _uiState.asStateFlow()

    private val logTag = "FavoriteVM"
    private var allFavorites: List<Favorite> = listOf()

    init {
        Log.i(logTag, "VM创建")
        // [MODIFIED]
        // 启动一个协程来收集来自 DataStore 的收藏列表 Flow。
        // 这将自动处理来自 DataStore 的所有更新（包括来自 ReaderVM 的保存）。
        viewModelScope.launch {
            FavoriteUtil.getFavoriteFlow().collect { fullList ->
                allFavorites = fullList
                val currentUiState = _uiState.value
                // 根据新列表和当前的管理模式更新UI状态
                _uiState.value = currentUiState.copy(
                    favoriteList = if (currentUiState.isInManageMode) {
                        allFavorites
                    } else {
                        allFavorites.filter { !it.isHidden }
                    }
                )
            }
        }
    }

    // [REMOVED]
    // 此函数不再需要，因为 init 中的 Flow collector 会自动处理
    // 本地数据的加载和更新。
    /*
    fun loadFavorites() {
        viewModelScope.launch {
            FavoriteUtil.getFavorite {
                allFavorites = it
                val currentUiState = _uiState.value
                // 根据当前是否在管理模式，刷新UI列表
                _uiState.value = currentUiState.copy(
                    favoriteList = if (currentUiState.isInManageMode) allFavorites else allFavorites.filter { !it.isHidden }
                )
            }
        }
    }
    */

    fun refreshList(showLoading: Boolean = true) {
        if (showLoading) {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
        }
        CookieUtil.getCookie {
            Log.i(logTag, it)
            val favoriteApi = YamiboRetrofit.getInstance().create(FavoriteApi::class.java)
            favoriteApi.getFavoritePage().enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val respHTML = response.body()?.string()
                        if (respHTML != null) {
                            val parse = Jsoup.parse(respHTML)
                            val favList = parse.getElementsByClass("sclist")
                            val objList = ArrayList<Favorite>()
                            // 遍历解析出的收藏条目，提取标题和链接构造Favorite对象
                            favList.forEach { li ->
                                val title = li.text()
                                val url = li.child(1).attribute("href").value
                                Log.i(logTag, url)
                                objList.add(Favorite(title, url))
                            }
                            // [MODIFIED]
                            // 将新的收藏列表保存至本地。
                            // UI 将通过 init 中的 Flow collector 自动更新。
                            FavoriteUtil.addFavorite(objList) { filteredList ->
                                // [MODIFIED]
                                // 我们不再需要在这里手动更新 allFavorites 和 uiState，
                                // 因为 Flow collector 会处理。
                                // 我们只需要确保在主线程上关闭刷新指示器。
                                // allFavorites = filteredList (由 flow 处理)
                                // val currentUiState = _uiState.value (由 flow 处理)
                                viewModelScope.launch(Dispatchers.Main) {
                                    _uiState.value =
                                        _uiState.value.copy(
                                            // favoriteList = ... (由 flow 处理)
                                            isRefreshing = false
                                        )
                                }
                            }
                        } else {
                            // 出错时停止加载
                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(isRefreshing = false)
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    t.printStackTrace()
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isRefreshing = false)
                    }
                }
            })
        }
    }

    fun clickHandler(url: String, navController: NavController) {
        // ... (此函数保持不变)
        val urlEncoded = URLEncoder.encode(url, "utf-8")
        navController.navigate("ReaderPage/$urlEncoded")
    }

    //拖拽排序功能
    fun moveFavorite(from: Int, to: Int) {
        // ... (此函数保持不变)
        // [修改] 拖拽只应在非管理模式下工作
        if (_uiState.value.isInManageMode) return // 管理模式下禁用拖拽

        val currentUiList = _uiState.value.favoriteList.toMutableList()
        if (from < 0 || from >= currentUiList.size || to < 0 || to >= currentUiList.size || from == to) {
            return // 无效的移动
        }
        // 移动项目
        val item = currentUiList.removeAt(from)
        currentUiList.add(to, item)

        // 立即更新UI状态
        _uiState.value = _uiState.value.copy(favoriteList = currentUiList.toList())

        // [修改] 在后台更新 allFavorites 的顺序并保存
        viewModelScope.launch(Dispatchers.IO) {
            // 我们需要重建 allFavorites 的顺序
            // 1. 获取所有已排序的 UI (非隐藏) 项目
            val newOrderedUiUrls = currentUiList.map { it.url }.toSet()
            val newOrderedUiList = currentUiList.toList()

            // 2. 获取所有隐藏的项目
            val hiddenItems = allFavorites.filter { it.isHidden }

            // 3. 组合成新的完整列表 (已排序的 + 隐藏的)
            // 注意：这会把所有隐藏的项目放到列表末尾
            val newListToSave = newOrderedUiList + hiddenItems

            // 4. 更新VM的内部状态
            allFavorites = newListToSave

            // 5. 保存这个新顺序
            FavoriteUtil.saveFavoriteOrder(newListToSave)
        }
    }

    // 切换管理模式
    fun toggleManageMode() {
        // ... (此函数保持不变)
        val newState = !_uiState.value.isInManageMode
        val newList = if (newState) {
            allFavorites // 进入管理模式，显示所有
        } else {
            allFavorites.filter { !it.isHidden } // 退出管理模式，只显示未隐藏的
        }
        _uiState.value = _uiState.value.copy(
            isInManageMode = newState,
            favoriteList = newList,
            selectedItems = emptySet() // 切换模式时清空选项
        )
    }

    // 在管理模式下切换项目选中状态
    fun toggleItemSelection(url: String) {
        // ... (此函数保持不变)
        if (!_uiState.value.isInManageMode) return

        val newSelections = _uiState.value.selectedItems.toMutableSet()
        if (newSelections.contains(url)) {
            newSelections.remove(url)
        } else {
            newSelections.add(url)
        }
        _uiState.value = _uiState.value.copy(selectedItems = newSelections)
    }

    // 隐藏选中的项目
    fun hideSelectedItems() {
        // ... (此函数保持不变)
        val itemsToHide = _uiState.value.selectedItems
        if (itemsToHide.isEmpty()) return

        viewModelScope.launch {
            FavoriteUtil.updateHiddenStatus(itemsToHide, true) {
                // 操作完成后（在IO线程回调）
                viewModelScope.launch(Dispatchers.Main) {
                    // [MODIFIED]
                    // 我们不再需要手动更新 allFavorites 和 uiState，
                    // Flow collector 会自动处理。
                    // 只需要清空选中项。
                    /*
                    // 更新 allFavorites 内存
                    allFavorites = allFavorites.map {
                        if (itemsToHide.contains(it.url)) it.copy(isHidden = true) else it
                    }
                    // 更新UI (仍在管理模式，favoriteList 保持为 allFavorites)
                    _uiState.value = _uiState.value.copy(
                        favoriteList = allFavorites,
                        selectedItems = emptySet()
                    )
                    */
                    _uiState.value = _uiState.value.copy(selectedItems = emptySet())
                }
            }
        }
    }

    // 取消隐藏选中的项目
    fun unhideSelectedItems() {
        // ... (此函数保持不变)
        val itemsToUnhide = _uiState.value.selectedItems
        if (itemsToUnhide.isEmpty()) return

        viewModelScope.launch {
            FavoriteUtil.updateHiddenStatus(itemsToUnhide, false) {
                // 操作完成后（在IO线程回调）
                viewModelScope.launch(Dispatchers.Main) {
                    // [MODIFIED]
                    // 同上，Flow collector 会自动处理。
                    // 只需要清空选中项。
                    /*
                    // 更新allFavorites内存
                    allFavorites = allFavorites.map {
                        if (itemsToUnhide.contains(it.url)) it.copy(isHidden = false) else it
                    }
                    // 更新UI (仍在管理模式，favoriteList保持为allFavorites)
                    _uiState.value = _uiState.value.copy(
                        favoriteList = allFavorites,
                        selectedItems = emptySet()
                    )
                    */
                    _uiState.value = _uiState.value.copy(selectedItems = emptySet())
                }
            }
        }
    }
}