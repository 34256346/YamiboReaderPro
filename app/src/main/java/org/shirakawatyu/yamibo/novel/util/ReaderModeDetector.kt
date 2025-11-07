package org.shirakawatyu.yamibo.novel.util

/**
 * 阅读模式检测工具类
 * 用于判断当前URL是否为可转换为阅读模式的帖子页面
 */
class ReaderModeDetector {
    companion object {
        /**
         * 判断URL是否为帖子页面（可转换为阅读模式）
         * @param url 当前页面URL
         * @return 是否可以转换为阅读模式
         */
        fun canConvertToReaderMode(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            
            // 检查是否包含帖子查看的标识
            // mod=viewthread 表示这是一个帖子查看页面
            return url.contains("mod=viewthread") && url.contains("tid=")
        }
        
        /**
         * 从完整URL中提取可用于导航到ReaderPage的路径
         * 例如: https://bbs.yamibo.com/forum.php?mod=viewthread&tid=563621&extra=page%3D1&mobile=2
         * 提取为: forum.php?mod=viewthread&tid=563621&extra=page%3D1&mobile=2
         * 
         * @param fullUrl 完整的URL
         * @return 提取的路径，如果无法提取则返回null
         */
        fun extractThreadPath(fullUrl: String?): String? {
            if (fullUrl.isNullOrBlank()) return null
            
            return try {
                // 移除域名部分，保留路径
                val baseUrl = "https://bbs.yamibo.com/"
                if (fullUrl.startsWith(baseUrl)) {
                    fullUrl.removePrefix(baseUrl)
                } else if (fullUrl.contains("forum.php")) {
                    // 如果URL格式不同，尝试从forum.php开始提取
                    fullUrl.substring(fullUrl.indexOf("forum.php"))
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}