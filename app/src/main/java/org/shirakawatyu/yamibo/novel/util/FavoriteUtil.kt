package org.shirakawatyu.yamibo.novel.util

import androidx.datastore.preferences.core.stringPreferencesKey
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.shirakawatyu.yamibo.novel.global.GlobalData

class FavoriteUtil {
    companion object {
        private val key = stringPreferencesKey("yamibo_favorite")

        /**
         * 提供一个 Flow，用于实时监听收藏列表的变化。
         */
        fun getFavoriteFlow(): Flow<List<Favorite>> {
            val dataStore =
                GlobalData.dataStore ?: throw IllegalStateException("DataStore not initialized")
            return dataStore.data
                .map { preferences ->
                    val jsonString = preferences[key]
                    if (jsonString != null) {
                        try {
                            jsonToHashMap(jsonString).values.toList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
        }

        /**
         * (不是给论坛账号添加新的收藏）合并网络收藏和本地收藏。
         * 1. 将网络上新增的收藏项放在顶部。
         * 2. 保留本地已有的收藏项的顺序（包括用户手动排序后的顺序）。
         * 3. 移除在网络上已被删除的收藏项。
         */
        fun addFavorite(favorites: List<Favorite>, callback: (list: List<Favorite>) -> Unit) {
            // ... (此函数保持不变)
            // 获取本地存储的旧收藏Map
            getFavoriteMap { oldMap ->

                val networkMap = favorites.associateBy { it.url }
                val finalOrderedMap = LinkedHashMap<String, Favorite>()

                // 添加新项目：遍历网络列表，如果项目不在旧Map中，则它是新项目
                for (netFav in favorites) {
                    if (!oldMap.containsKey(netFav.url)) {
                        finalOrderedMap[netFav.url] = netFav
                    }
                }

                // 添加旧项目：遍历本地的旧Map
                for (oldEntry in oldMap.entries) {
                    val url = oldEntry.key
                    val oldFavData = oldEntry.value

                    // 检查这个旧项目是否仍然存在于网络列表中
                    if (networkMap.containsKey(url)) {
                        // 存在，获取网络版本
                        val netFavData = networkMap[url]!!

                        // 创建更新后的 Favorite，
                        // 保留本地的阅读进度 (lastPage, lastChapter等)，
                        // 但更新网络上的信息 (如 title)
                        val updatedFav = oldFavData.copy(
                            title = netFavData.title
                        )
                        finalOrderedMap[url] = updatedFav
                    }
                }

                // 保存这个合并后的新顺序的Map
                DataStoreUtil.addData(JSON.toJSONString(finalOrderedMap), key)

                // 返回新列表给VM
                callback(finalOrderedMap.values.toList())
            }
        }

        /**
         * 保存用户手动排序后的列表。
         */
        fun saveFavoriteOrder(orderedList: List<Favorite>) {
            // ... (此函数保持不变)
            val favMap = LinkedHashMap<String, Favorite>()
            // 按照列表的新顺序重新构建 LinkedHashMap
            orderedList.forEach { fav ->
                favMap[fav.url] = fav
            }
            // addData 会在 IO 协程中执行
            DataStoreUtil.addData(JSON.toJSONString(favMap), key)
        }


        fun updateFavorite(favorite: Favorite) {
            // ... (此函数保持不变)
            DataStoreUtil.getData(key, callback = {
                val favMap = jsonToHashMap(it)
                favMap[favorite.url] = favorite
                DataStoreUtil.addData(JSON.toJSONString(favMap), key)
            })
        }

        fun updateHiddenStatus(urls: Set<String>, isHidden: Boolean, onComplete: () -> Unit) {
            // ... (此函数保持不变)
            DataStoreUtil.getData(key, callback = { data ->
                val favMap = jsonToHashMap(data)
                var changed = false
                urls.forEach { url ->
                    favMap[url]?.let { fav ->
                        if (fav.isHidden != isHidden) {
                            fav.isHidden = isHidden
                            changed = true
                        }
                    }
                }
                // 只有在状态实际发生变化时才写入DataStore
                if (changed) {
                    DataStoreUtil.addData(JSON.toJSONString(favMap), key, onComplete)
                } else {
                    onComplete()
                }
            }, onNull = onComplete) // 如果数据为空，也调用onComplete
        }

        fun getFavoriteMap(callback: (map: Map<String, Favorite>) -> Unit) {
            // ... (此函数保持不变)
            DataStoreUtil.getData(key, callback = {
                val favMap = jsonToHashMap(it)
                callback(favMap)
            }, onNull = {
                // 确保onNull时返回一个空的LinkedHashMap
                callback(LinkedHashMap())
            })
        }

        fun getFavorite(callback: (list: List<Favorite>) -> Unit) {
            // ... (此函数保持不变)
            getFavoriteMap {
                callback(it.values.toList())
            }
        }

        private fun jsonToHashMap(text: String): LinkedHashMap<String, Favorite> {
            // ... (此函数保持不变)
            val jsonObject: JSONObject = JSON.parseObject(text)
            val map = LinkedHashMap<String, Favorite>()
            jsonObject.values.forEach {
                val obj = it as JSONObject
                val fav = Favorite(
                    obj["title"] as String,
                    obj["url"] as String,
                    obj["lastPage"] as Int,
                    obj["lastView"] as Int,
                    obj["lastChapter"] as? String,
                    obj["authorId"] as? String,
                    obj["isHidden"] as? Boolean ?: false
                )
                map[fav.url] = fav
            }
            return map
        }
    }
}