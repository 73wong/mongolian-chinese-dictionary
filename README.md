# 蒙汉词典 · Mongolian–Chinese Dictionary

Android 8.0（API 26）及以上的传统蒙古文—汉语离线词典。

- 53,412 条词条，25,911 条词条带离线语音。
- 传统蒙古文竖排、转写、音标和西里尔蒙古文（按来源数据提供）。
- 前段、完全、后段和部分匹配；可选转写模糊查询。
- 收藏、学习计划、日历统计、相关短语分组、词库导入导出。
- 中英文界面及输入指南，文字可长按复制。

## 下载使用

从本仓库 **Releases** 下载 `mongolian-chinese-dictionary.apk`。
APK 已内置词库和语音，普通用户无需另下数据包或运行爬虫。
个人学习记录保存在设备中，不随 APK 自动同步。

## 从源码构建

需要 JDK 21、Android SDK Platform 35、Python 3.9+；首次 Gradle 构建需要网络。
以下命令适用于 macOS/Linux。

从对应版本 Release 下载 `dictionary-data.zip`，在项目根目录执行：

```sh
python3 tools/restore_data.py /path/to/dictionary-data.zip
chmod +x gradlew
./gradlew assembleDebug
```

恢复脚本校验完整数据包、词库及每个音频的 SHA-256。
用 Android Studio 打开项目配置 SDK/JDK，或先设置 `ANDROID_HOME`。
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。
未恢复离线数据时构建会明确报错，避免生成缺少数据的版本。

完整词库和语音通过 Releases 公开提供；源码仓库不重复存放大量音频。

## 验证

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e offlineOnly true org.example.mongoldictionary.test/org.example.mongoldictionary.RenderingChecks
```

Android 验收使用独立空白数据目录，覆盖完整词库、`mand` 14 条、`mend` 11 条、内置语音引用及离线音频解码。

## 维护资料

- [爬虫说明](docs/crawler-guide.md)
- [导入格式](docs/import-guide.md)、[CSV 模板](examples/new-entries-template.csv)
- [学习功能](docs/study-guide.md)
- [数据清单](data/manifest.json)、[缺失语音](data/missing-audio.csv)
- [第三方来源与许可说明](THIRD_PARTY_NOTICES.md)

350 条有来源语音链接的词条尚缺录音。2026-10-13 逐条匹配实时页面并检查 700 个 MP3/WAV 地址，均返回 404；正常语音对照返回 200。缺失项没有标为完成，也没有用相近词或合成音替代。

当前版本 `1.0.1-offline`（14）。现有 APK 使用调试签名；长期发布前应配置并保存正式签名密钥，后续升级必须保持签名一致。密钥和密码不得提交。
应用源码的整体许可证尚未指定，第三方数据及字体不因此获得新的许可。
