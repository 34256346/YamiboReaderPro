# 300百合会论坛文学阅读器

为百合会论坛小说提供更好的阅读体验，支持常见小说阅读器的功能。

最好用来看小说，如果你主要用百合会看漫画或浏览论坛帖子，浏览器直接启动网页比app流畅。

系统要求: Android 7.0（may be higher）及以上
<br><br><br><br><br>
关于后续：
<br>
以后大概只会修修bug，不太会有功能更新了。
而且感觉把点子全用上了，因为在仙台告白之前几乎没用过百合会，现在用也几乎只用来看周次，所以也想不到其他的扩展功能了。

## 内容列表

- [已知问题](#已知问题)
- [软件下载](#软件下载)
- [开源许可](#开源许可)
- [如何贡献](#如何贡献)
- [使用许可](#使用许可)

## 已知问题

1. 我没有多个账号，目测换账号登录可能会出问题，出了问题可以把软件数据清除。
2. 如果你觉得加载卡住了，建议重进一次页面或重启软件，不过大概率是网络问题，建议从wifi换为流量试试。
3. 首次从“我的”界面进行登录时，由于论坛规则会先跳转到首页，程序会在跳转到首页后，再次加载“我的”界面，这个过程可能会很慢（参考第2条），建议不要等待加载，直接离开“我的界面”，进入其他界面。

## 软件下载

[Github Release](https://github.com/34256346/YamiboReaderPro/releases)



[度盘](https://pan.baidu.com/s/15EZiwp0Z5z8qrg1Gd4IIlA?pwd=yuri)



## 软件截图

<table>
  <tr>
    <td><img src="screenshots/1.jpg" width="200"></td>
    <td><img src="screenshots/2.jpg" width="200"></td>
    <td><img src="screenshots/3.jpg" width="200"></td>
    <td><img src="screenshots/6.jpg" width="200"></td>
  </tr>
  <tr>
    <td><img src="screenshots/5.jpg" width="200"></td>
    <td><img src="screenshots/4.jpg" width="200"></td>
    <td><img src="screenshots/7.jpg" width="200"></td>
    <td><img src="screenshots/8.jpg" width="200"></td>
  </tr>
  <tr>
    <td><img src="screenshots/9.jpg" width="200"></td>
    <td><img src="screenshots/10.jpg" width="200"></td>
    <td><img src="screenshots/11.jpg" width="200"></td>
    <td><img src="screenshots/12.jpg" width="200"></td>
  </tr>
</table>




## 相比原项目的重大升级

本项目在原 [flben233/YamiboReader](https://github.com/flben233/YamiboReader) 的基础上进行了重构与功能增强，旨在提供更流畅、方便的百合会小说阅读体验。主要升级包括：

1. 阅读体验与性能优化

- 预加载：快读完当前页面时（还差50页时）自动后台加载下一页内容。
- 缓存：基于 `LruCache` 实现已读页面在内存中缓存（最多缓存50mb，周次一页1mb左右，关闭软件会清除）和本地缓存。

2. 全新阅读器界面与个性化设置

- 现代化 UI：弹窗设置升级工具栏，操作更现代。
- 夜间模式：支持深色主题。
- 图片加载开关：可选是否加载帖子图片，提升加载速度。
- 阅读模式：横屏翻页 or 竖直下滑
- 背景颜色改变

3. 章节导航功能

- 自动提取章节目录：识别章节标题（每楼第一行）构建目录。
- 章节列表：打开章节列表，快速跳转。

4. “只看楼主”功能自动化

- 自动启用楼主过滤：首次进入某部小说，阅读器加载时间会较长，会先会进入所有人页面，自动模拟点击“只看楼主”。后续会提取并保存作者id，自动拼接 URL，确保始终只看该作者内容。

5. 收藏夹功能强化

- 拖拽排序：长按收藏项即可自由调整顺序。
- 自动刷新：每次进入收藏夹页面会进行刷新。
- 隐藏作品：可以选择在收藏栏选中作品进行隐藏/显示

6. 算法与稳定性提升

- 分页算法和两端对齐算法：正确处理段落换行，两端对齐，排版更自然。（**Thank you, Gemini!**）

## 开源许可

架构: [Jetpack Compose](https://developer.android.com/jetpack/compose)

网络: [Retrofit](https://github.com/square/retrofit)

网络: [Okhttp3](https://github.com/square/okhttp)

HTML解析: [Jsoup](https://github.com/jhy/jsoup)

JSON解析: [FastJSON2](https://github.com/alibaba/fastjson2)

图片显示: [coil](https://github.com/coil-kt/coil)

拖拽排序: [Reorderable](https://github.com/Calvin-LL/Reorderable)

缓存：[LRUCache](https://github.com/nicklockwood/LRUCache)

## 维护者

[@34256346](https://github.com/34256346)

## 原项目 

本项目 Fork 自 [flben233/YamiboReader](https://github.com/flben233/YamiboReader)。 感谢原作者 [@flben233](https://github.com/flben233) 的出色工作。

## 如何贡献

非常欢迎你的加入！[提一个 Issue](https://github.com/flben233/YamiboReader/issues/new) 或者提交一个 Pull Request。

## 使用许可

[AGPL 3.0](LICENSE) © flben233

