package org.shirakawatyu.yamibo.novel.bean

/**
 * 收藏数据类，用于存储用户收藏的小说信息
 *
 * @param title 帖子的标题
 * @param url 帖子的链接地址
 * @param lastPage 最后阅读的页码，默认为0
 * @param lastView 最后阅读的帖子页数，默认为1
 * @param lastChapter 最后阅读的章节名称，可为空，默认为null
 * @param authorId 帖子作者ID，可为空，默认为null
 */
data class Favorite(
    var title: String,
    var url: String,
    var lastPage: Int = 0,
    var lastView: Int = 1,
    var lastChapter: String? = null,
    var authorId: String? = null,
    var isHidden: Boolean = false
)
