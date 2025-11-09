package org.shirakawatyu.yamibo.novel.ui.vm

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.shirakawatyu.yamibo.novel.global.YamiboRetrofit
import org.shirakawatyu.yamibo.novel.network.FavoriteApi
import org.shirakawatyu.yamibo.novel.ui.state.FavoriteState
import org.shirakawatyu.yamibo.novel.util.CookieUtil
import org.shirakawatyu.yamibo.novel.util.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.LocalCacheUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder

class FavoriteVM(private val applicationContext: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoriteState())
    val uiState = _uiState.asStateFlow()

    private val logTag = "FavoriteVM"
    private var allFavorites: List<Favorite> = listOf()

    // 本地缓存工具
    private val localCache by lazy { LocalCacheUtil.getInstance(applicationContext) }

    init {
        Log.i(logTag, "VM创建")
        viewModelScope.launch {
            FavoriteUtil.getFavoriteFlow().collect { fullList ->
                allFavorites = fullList
                val currentUiState = _uiState.value
                _uiState.value = currentUiState.copy(
                    favoriteList = if (currentUiState.isInManageMode) {
                        allFavorites
                    } else {
                        allFavorites.filter { !it.isHidden }
                    }
                )
                // 当收藏列表变化时，也刷新缓存信息
                refreshCacheInfo(localCache.index.value)
            }
        }

        // 监听缓存版本变化
        viewModelScope.launch {
            localCache.index.collect { index ->
                // 每当内存索引变化时，都重新计算统计信息
                if (allFavorites.isNotEmpty()) {
                    Log.i(logTag, "缓存索引已更新, 正在刷新统计信息...")
                    refreshCacheInfo(index)
                }
            }
        }
    }

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
                            favList.forEach { li ->
                                val title = li.text()
                                val url = li.child(1).attribute("href").value
                                Log.i(logTag, url)
                                objList.add(Favorite(title, url))
                            }
                            FavoriteUtil.addFavorite(objList) { filteredList ->
                                viewModelScope.launch(Dispatchers.Main) {
                                    _uiState.value =
                                        _uiState.value.copy(
                                            isRefreshing = false
                                        )
                                }
                            }
                        } else {
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
        val urlEncoded = URLEncoder.encode(url, "utf-8")
        navController.navigate("ReaderPage/$urlEncoded")
    }

    fun moveFavorite(from: Int, to: Int) {
        if (_uiState.value.isInManageMode) return

        val currentUiList = _uiState.value.favoriteList.toMutableList()
        if (from < 0 || from >= currentUiList.size || to < 0 || to >= currentUiList.size || from == to) {
            return
        }

        val item = currentUiList.removeAt(from)
        currentUiList.add(to, item)

        _uiState.value = _uiState.value.copy(favoriteList = currentUiList.toList())

        viewModelScope.launch(Dispatchers.IO) {
            val newOrderedUiUrls = currentUiList.map { it.url }.toSet()
            val newOrderedUiList = currentUiList.toList()
            val hiddenItems = allFavorites.filter { it.isHidden }
            val newListToSave = newOrderedUiList + hiddenItems

            allFavorites = newListToSave
            FavoriteUtil.saveFavoriteOrder(newListToSave)
        }
    }

    fun toggleManageMode() {
        val newState = !_uiState.value.isInManageMode
        val newList = if (newState) {
            allFavorites
        } else {
            allFavorites.filter { !it.isHidden }
        }
        _uiState.value = _uiState.value.copy(
            isInManageMode = newState,
            favoriteList = newList,
            selectedItems = emptySet()
        )
    }

    fun toggleItemSelection(url: String) {
        if (!_uiState.value.isInManageMode) return

        val newSelections = _uiState.value.selectedItems.toMutableSet()
        if (newSelections.contains(url)) {
            newSelections.remove(url)
        } else {
            newSelections.add(url)
        }
        _uiState.value = _uiState.value.copy(selectedItems = newSelections)
    }

    fun hideSelectedItems() {
        val itemsToHide = _uiState.value.selectedItems
        if (itemsToHide.isEmpty()) return

        viewModelScope.launch {
            FavoriteUtil.updateHiddenStatus(itemsToHide, true) {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(selectedItems = emptySet())
                }
            }
        }
    }

    fun unhideSelectedItems() {
        val itemsToUnhide = _uiState.value.selectedItems
        if (itemsToUnhide.isEmpty()) return

        viewModelScope.launch {
            FavoriteUtil.updateHiddenStatus(itemsToUnhide, false) {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(selectedItems = emptySet())
                }
            }
        }
    }

    private fun refreshCacheInfo(index: Map<String, LocalCacheUtil.CacheIndex>) {
        try {
            val cacheInfoMap = mutableMapOf<String, CacheInfo>()
            // 遍历缓存索引
            index.forEach { (url, novelCache) ->
                // 如果在索引中，并且有缓存页面
                if (novelCache.pages.isNotEmpty()) {
                    val totalPages = novelCache.pages.size
                    // 直接从内存中的CachePageInfo对象求和
                    val totalSize = novelCache.pages.values.sumOf { it.fileSize }
                    val pagesWithImages = novelCache.pages.values.count { it.hasImages }

                    cacheInfoMap[url] = CacheInfo(
                        url = url,
                        totalPages = totalPages,
                        totalSize = totalSize,
                        pagesWithImages = pagesWithImages
                    )
                }
            }

            // 更新UI状态
            _uiState.value = _uiState.value.copy(cacheInfoMap = cacheInfoMap)

        } catch (e: Exception) {
            Log.e(logTag, "从内存索引刷新缓存信息失败", e)
            _uiState.value = _uiState.value.copy(cacheInfoMap = emptyMap())
        }
    }
    // ==================== 缓存管理功能 ====================

    // 缓存统计信息
    data class CacheInfo(
        val url: String,
        val totalPages: Int,
        val totalSize: Long,
        val pagesWithImages: Int
    )

    // 刷新缓存信息的复用函数
    fun refreshCacheInfo() {
        refreshCacheInfo(localCache.index.value)
    }

    // 获取所有收藏的缓存信息
    fun getCacheInfo(callback: (Map<String, FavoriteVM.CacheInfo>) -> Unit) {
        refreshCacheInfo(localCache.index.value)
        callback(_uiState.value.cacheInfoMap)
    }

    // 删除指定收藏的所有缓存
    fun deleteFavoriteCache(url: String) {
        viewModelScope.launch {
            try {
                localCache.deleteNovel(url)
                // 不需要回调，localCache.index.collect 会自动触发刷新
            } catch (e: Exception) {
                Log.e(logTag, "删除 $url 的缓存失败", e)
            }
        }
    }

    // 清理所有缓存
    fun clearAllCache() {
        viewModelScope.launch {
            try {
                localCache.clearAllCache()
            } catch (e: Exception) {
                Log.e(logTag, "清除所有缓存失败", e)
            }
        }
    }
}