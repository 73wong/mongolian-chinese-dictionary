# 爬虫维护

普通使用 App 无需爬虫。维护者先按 README 恢复数据、构建安装 App，再执行 `./gradlew assembleDebugAndroidTest` 生成导入辅助 APK。

当前脚本默认使用 macOS 的 `~/Library/Android/sdk/platform-tools/adb`。其他系统需调整 ADB 路径。连接一个测试设备；多个设备时在 `tools/crawler-config.json` 的 serial 字段指定目标。

```sh
python3 -u tools/crawl_dictionary.py
# 仅补语音
python3 -u tools/crawl_dictionary.py --audio-only
# 另开终端查看状态
python3 tools/watch_crawler.py
```

Mac 可双击 `UpdateDictionary.command` 或 `WatchCrawler.command`。Control+C 请求停止，保留已提交数据。

网页、队列和备份在 Git 忽略的 `collection/` 中。不要上传：

- `crawler/report.json`：词条扫描结果。
- `crawler/audio-report.json`：语音任务结果。
- `crawler/audio-queue.json`：任务与重试状态。
- `crawler/backups/`：导入前备份，自动保留最近三份及标记 KEEP 的备份。
- `crawler/quarantine/`：待核对的不完整源词条。

扫描完成不代表已证明网站全量完整，也不能用历史报告代替设备真实词条数。
设备词库少于上次快照时程序会停止，需核对设备和数据后再继续。
404/410 暂缓约 24 小时；没有到期任务时程序退出，下次需手动启动。

导入设备不会自动更新对外 APK。发布前必须更新离线资源和 data/manifest.json，重新构建验收，并替换唯一安装包。
