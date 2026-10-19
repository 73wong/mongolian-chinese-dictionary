# 蒙汉词典 · Mongolian–Chinese Dictionary

适用于 Android 8.0（API 26）及以上版本的传统蒙古文—汉语离线词典。

支持离线查询、语音播放、收藏与背单词，提供中英文界面。

## 功能

- **离线词库**：53,412 条词条，其中 25,911 条带离线语音。
- **多种文字信息**：传统蒙古文竖排、罗马字母转写、国际音标和西里尔蒙古文，按来源数据提供。
- **搜索**：前段一致、完全一致、后段一致和部分一致，可选转写模糊查询。
- **学习**：收藏、学习计划、日历统计及相关短语分组。
- **数据管理**：词库导入与导出。
- **语言与指南**：中英文界面及对应的输入指南。
- **文本复制**：支持长按复制，包括传统蒙古文。

## 下载与安装

前往 [Releases](https://github.com/73wong/mongolian-chinese-dictionary/releases) 页面，下载：

**`mongolian-chinese-dictionary.apk`**

APK 已内置完整词库和已有语音。普通用户只需下载安装 APK，无需另下数据包，也无需运行爬虫。

个人收藏和学习记录保存在设备中，不会通过 GitHub 或 APK 自动同步。更新前建议备份个人数据；覆盖安装需使用相同的应用签名。

### 发布附件说明

| 文件 | 用途 |
| --- | --- |
| `mongolian-chinese-dictionary.apk` | 安卓安装包，包含离线词库和已有语音。 |
| `dictionary-data.zip` | 供开发者恢复构建所需的词库与语音数据。 |
| `release-manifest.json` | 本次发布的信息清单。 |
| `SHA256SUMS` | 用于核对下载文件的完整性。 |

## 数据完整性与已知限制

音标、西里尔蒙古文和语音按采集到的来源数据提供，并非每条词条都有全部字段。

当前有 **350 条带来源语音链接的词条未能取得录音**。2026-10-13 的核查逐条匹配了实时页面，并检查了对应的 700 个 MP3/WAV 地址，均返回 HTTP 404；正常语音对照返回 HTTP 200。

这些缺失项仍保留在清单中，没有用相近词的录音或合成语音替代。HTTP 404 表示核查时链接不可用，不足以确定录音从未上传。

详情见：

- [数据清单](data/manifest.json)
- [缺失语音清单](data/missing-audio.csv)

采集和格式转换过程中可能存在遗漏或错误，欢迎反馈。

## 从源码构建

### 环境要求

- JDK 21
- Android SDK Platform 35
- Python 3.9 或以上
- 首次 Gradle 构建需要网络，以下载相关依赖

以下命令适用于 macOS / Linux。

### 恢复离线数据

从对应版本的 [Release](https://github.com/73wong/mongolian-chinese-dictionary/releases) 下载 `dictionary-data.zip`。

在项目根目录执行，并将示例路径替换为实际下载路径：

```sh
python3 tools/restore_data.py /path/to/dictionary-data.zip
```

恢复脚本会校验完整数据包、词库及每个音频文件的 SHA-256。

完整词库和语音通过 Releases 单独提供，源码仓库不重复存放大量音频文件。资料的公开可下载性不代表授予任意再分发许可，使用前请查看第三方来源与许可说明。

### 构建 APK

用 Android Studio 打开项目并配置 SDK / JDK；通过终端构建时，也可先设置 `ANDROID_HOME`。

```sh
chmod +x gradlew
./gradlew assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

未恢复离线数据时，构建会明确报错，避免生成缺少词库或语音数据的版本。

## 验证

运行 Python 测试：

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
```

连接已开启 USB 调试的安卓设备，或启动安卓模拟器，然后执行：

```sh
./gradlew assembleDebug assembleDebugAndroidTest

adb install -r app/build/outputs/apk/debug/app-debug.apk

adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am instrument -w -e offlineOnly true org.example.mongoldictionary.test/org.example.mongoldictionary.RenderingChecks
```

离线验收使用独立的空白数据目录，覆盖：

- 完整词库加载。
- `mand` 前缀查询的 14 条结果。
- `mend` 前缀查询的 11 条结果。
- 内置语音文件引用。
- 离线音频解码。

如果安装提示签名不兼容，请先备份个人数据并核对签名，不要直接卸载已有应用。

## 维护资料

- [爬虫说明](docs/crawler-guide.md)
- [词库导入格式](docs/import-guide.md)
- [CSV 导入模板](examples/new-entries-template.csv)
- [学习功能说明](docs/study-guide.md)
- [数据清单](data/manifest.json)
- [缺失语音清单](data/missing-audio.csv)
- [第三方来源与许可说明](THIRD_PARTY_NOTICES.md)

爬虫属于数据维护工具，普通用户使用 App 不需要运行它。

## 数据来源与致谢

本应用的词条、释义及已有语音主要采集自以下网站，并经过整理，用于离线查询和学习：

**[数据采集页面](http://hkuri.cneas.tohoku.ac.jp/p01/mongol/list?groupId=12)**

网站域名：`hkuri.cneas.tohoku.ac.jp`

感谢原词典的编纂者、资料提供者、语音录制者，以及网站开发和维护人员。他们的工作是本应用内容的基础。

本项目负责数据采集与整理，以及 Android 应用的开发，提供离线查询、收藏和学习记录等功能。原始词典内容并非本项目原创。

本应用为独立项目，并非来源网站的官方应用，也不代表原作者或相关机构；本声明不表示已获得其认可或背书。

## 许可与第三方资料

应用源码的整体许可证尚未指定。

词条、释义、语音、字体和指南图片等第三方资料的相关权利归各自权利人所有。本项目不因采集、整理或格式转换而取得原始资料的所有权，也不向使用者授予额外的第三方资料使用许可。

来源标注与致谢不等于转载或再分发授权。完整词库和语音的再分发授权尚未确认，使用和再分发前应核实相关权利人的许可条件。

字体等第三方组件的现有许可与说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 及对应资源目录中的许可文件。

## 1.1.0 更新内容

- 重做学习首页、学习卡片与本轮结束页。
- 每轮最多 10 个词，支持暂停后继续。
- 新词先展示含义；复习先回忆再揭示答案。
- 学习卡片聚焦回忆、查看答案和评分；不适合的词可在词表中暂停。
- 保留评分撤销、日历统计与现有学习记录。

## 1.0.2 更新内容

- 设置页的数据来源改为实际抓取页面链接。
- 来源链接支持点击打开浏览器、长按复制。
- 删除设置页底部多余的说明文字，中英文界面同步调整。
- 词库与语音数据未变，可继续使用上一版的 `dictionary-data.zip`。

## 版本与签名

当前版本：**`1.1.0-offline`**  
Android versionCode：**`16`**

当前 APK 使用调试签名。长期发布前应配置并妥善保存正式签名密钥，后续覆盖升级需保持签名一致。

签名密钥、密码及其他凭据不得提交到 GitHub。

## 问题反馈

如发现以下问题，欢迎提交 [GitHub Issue](https://github.com/73wong/mongolian-chinese-dictionary/issues)：

- 词条、释义或转写错误。
- 传统蒙古文字形或竖排显示异常。
- 语音缺失、播放异常或与词条不对应。
- 搜索、导入导出或学习功能异常。
- 来源标注遗漏或资料使用相关问题。

反馈时请尽量提供词条转写、操作步骤、应用版本和截图，避免上传个人隐私信息。

## English Summary

**Mongolian–Chinese Dictionary** is an offline Android dictionary for Android 8.0 and later.

It includes 53,412 entries, with offline audio available for 25,911 entries. Features include traditional Mongolian vertical text, transliteration, search, favorites, study records, calendar statistics, and Chinese / English interfaces.

IPA and Cyrillic Mongolian are included where available in the source data.

### Download

Download `mongolian-chinese-dictionary.apk` from [Releases](https://github.com/73wong/mongolian-chinese-dictionary/releases).

The APK includes the dictionary and available audio. Regular users do not need the separate data archive or crawler tools.

### Version 1.0.2

The Settings page now links directly to the data collection page. Tap the link to open it in a browser or long-press to copy it. The redundant footer text has been removed in both interface languages.

The dictionary and audio data are unchanged; the previous `dictionary-data.zip` remains compatible.

### Sources and acknowledgements

The entries, definitions, and available audio were primarily collected from the [source dictionary website](http://hkuri.cneas.tohoku.ac.jp/p01/mongol/list?groupId=12) hosted at `hkuri.cneas.tohoku.ac.jp`.

We thank the dictionary compilers, material and audio contributors, and website developers and maintainers. Their work forms the foundation of this app’s content.

This is an independent project, not an official app of the source website. It does not represent or imply endorsement by the original authors or associated institutions.

### Rights and limitations

Rights to third-party materials remain with their respective rights holders. Acknowledgement does not grant permission to use or redistribute those materials. Authorization to redistribute the complete dictionary and audio has not yet been confirmed.

The project-wide source code license has not yet been specified. See [Third-party notices](THIRD_PARTY_NOTICES.md) for the available attribution and licensing information.

Some source audio links are unavailable. Missing recordings have not been replaced with recordings of similar words or synthesized speech.

The current APK is debug-signed. Personal favorites and study records are stored on the device and are not automatically synchronized.

Please report errors, missing attribution, or concerns through [GitHub Issues](https://github.com/73wong/mongolian-chinese-dictionary/issues).
