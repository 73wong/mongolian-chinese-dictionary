# 蒙汉词典 · Mongolian–Chinese Dictionary

面向 Android 的传统蒙古文—汉语离线词典，支持查词、真人录音播放、收藏与单词复习。适用于 **Android 8.0（API 26）及以上版本**，提供中文和英文界面。

[下载安装](https://github.com/73wong/mongolian-chinese-dictionary/releases) · [问题反馈](https://github.com/73wong/mongolian-chinese-dictionary/issues) · [English](#english)

## 下载与使用

在 [Releases](https://github.com/73wong/mongolian-chinese-dictionary/releases) 中选择一个发布版本，下载 `mongolian-chinese-dictionary.apk` 并安装。

**APK 内置词库和已有语音，安装后即可离线使用。** 普通用户无需下载开发数据包，也不需要运行爬虫。

收藏和学习记录保存在设备中，不会通过 GitHub 自动同步。更新前可在 App 的数据管理中备份；覆盖安装需要保持应用签名一致。

> 本文介绍 1.1.2 的功能与构建方式。下载安装包时，请以对应 Release 的说明为准。

## 主要功能

- **多文字查词**：传统蒙古文竖排、罗马字母转写、国际音标、西里尔蒙古文与汉语释义；字段按来源数据提供。
- **搜索与筛选**：支持全部字段搜索，以及前段、完全、后段和部分匹配；全文范围使用部分匹配。转写可开启模糊查询。
- **离线语音**：有录音的词条可直接播放，播放按钮位于蒙古文旁边。
- **收藏与学习**：学习计划、分轮复习、暂停继续、评分撤销、日历统计和相关短语分组。
- **完整阅读**：学习答案按传统蒙古文、转写、音标、西里尔蒙古文、释义与例句的顺序展示。
- **导入与备份**：支持词库导入导出；文字可长按复制，包括传统蒙古文。
- **双语支持**：中文、英文界面及对应的输入指南。
- **词条纠错**：在 App 中填写并预览反馈，再前往 GitHub Issues 提交；支持本地草稿。

转写模糊查询仅合并 `o/u`、`O/U`、`t/d`、`k/g`，不合并 `a/e`。它与系统输入法的键位布局是不同概念。

## 1.1.2 更新内容

- 简化搜索首页与筛选面板，加入居中的传统蒙古文标题和 `Monggol Hitad Toli`。
- 使用预构建的 SQLite 索引，按需读取词条，减少启动及页面切换时整库解析的开销。
- 将部分词表枚举与相关短语处理移到后台，减少界面阻塞。
- 合并重复的 `mori(n)` 词条，并迁移对应收藏与学习记录。
- 修正 11 条以 `cengk`、`cengg` 开头的词条音标，将相应的 `tʃiŋ…` 改为 `tʃəŋ…`；缺失音标的词条未补造音标。

版本字段为 `1.1.2-offline`（versionCode `18`）。历史发布内容见 [Releases](https://github.com/73wong/mongolian-chinese-dictionary/releases)。

## 词库与语音

| 项目 | 数量 / 说明 |
| --- | --- |
| 当前源码应用修正规则后的词条 | 53,413 条 |
| 原始数据包中的词条 | 53,412 条 |
| 原始数据清单中带离线语音的词条 | 25,911 条 |
| 原始数据清单中的音频文件 | 25,903 个，部分词条共用录音 |

原始数据包与当前词条数可能不同，因为构建时会应用补充、纠错和重复项合并规则。[数据清单](data/manifest.json) 描述的是原始数据包，不是每次源码修订后的统计。

并非所有词条都有音标、西里尔蒙古文或语音。对于 350 条带来源链接但缺失录音的词条，2026-10-13 的核查记录显示，对应的 700 个 MP3/WAV 地址返回 HTTP 404。该结果仅表示当时链接不可用，不能据此认定录音从未上传。

缺失录音未用相近词录音或 AI 合成语音替代，详情见[缺失语音清单](data/missing-audio.csv)。

## 从源码构建

需要 JDK 21、Android SDK Platform 35、Python 3.9+。首次 Gradle 构建需要联网下载依赖。以下命令适用于 macOS / Linux。

### 1. 恢复离线数据

下载 [dictionary-data.zip（v1.1.0）](https://github.com/73wong/mongolian-chinese-dictionary/releases/download/v1.1.0/dictionary-data.zip)。当前源码继续复用此原始数据包，修正规则随源码维护。

在项目根目录执行：

```sh
python3 tools/restore_data.py /path/to/dictionary-data.zip
```

将路径替换为下载文件的实际位置。恢复脚本会核对数据包、词库和音频文件的 SHA-256。

### 2. 构建安装包

在 Android Studio 中配置 SDK / JDK，或在终端中配置 `ANDROID_HOME`，然后执行：

```sh
chmod +x gradlew
./gradlew assembleDebug
```

输出文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

构建会自动运行 `tools/build_dictionary_db.py`，应用词条修正并生成 SQLite 数据库和校验文件。生成的数据库无需手动提交到 GitHub。未恢复离线数据时无法完成构建。

首次打开 App 时需要将数据库复制到应用私有存储；后续启动复用数据库。索引会增加安装体积和存储占用，技术细节见[运行时数据库说明](docs/runtime-database.md)。

### 发布附件

| 文件 | 用途 |
| --- | --- |
| `mongolian-chinese-dictionary.apk` | 普通用户安装使用 |
| `dictionary-data.zip` | 开发者恢复构建数据，可由多个版本复用 |
| `release-manifest.json` | 发布信息清单（如该版本提供） |
| `SHA256SUMS` | 下载文件校验值（如该版本提供） |

## 开发验证

Python 工具测试：

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
```

启动模拟器或连接测试设备后，构建并安装测试 APK：

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

运行索引一致性检查：

```sh
adb shell am instrument -w -e indexedOnly true org.example.mongoldictionary.test/org.example.mongoldictionary.RenderingChecks
```

该检查覆盖编译词库与旧加载器的逐字段一致性、搜索结果、学习词条映射，以及导入后的索引更新。将 `indexedOnly` 替换为 `searchUiOnly`、`navigationOnly` 或 `studyOnly`，可分别运行搜索界面、导航和学习回归检查。

建议使用独立模拟器或测试设备。部分历史测试仍包含旧版本固定词条数，需随数据版本核对预期值。模拟器调试性能不能替代真机发布版测量。

## 反馈与维护

发现词条、字形、音标、语音或功能问题时，可以使用 App 内的词条反馈，或直接提交 [GitHub Issue](https://github.com/73wong/mongolian-chinese-dictionary/issues)。提交 Issue 需要 GitHub 账号和网络连接。

请尽量提供词条转写、错误内容、建议更正、应用版本；显示或操作问题可附截图与复现步骤。

- [学习功能](docs/study-guide.md)
- [词库导入格式](docs/import-guide.md) · [CSV 模板](examples/new-entries-template.csv)
- [爬虫维护说明](docs/crawler-guide.md)
- [运行时数据库](docs/runtime-database.md)
- [原始数据清单](data/manifest.json) · [缺失语音](data/missing-audio.csv)
- [第三方来源与许可](THIRD_PARTY_NOTICES.md)

## 数据来源与致谢

词条、释义及已有语音主要采集自[原词典网站](http://hkuri.cneas.tohoku.ac.jp/p01/mongol/list?groupId=12)，域名为 `hkuri.cneas.tohoku.ac.jp`。

感谢原词典编纂者、资料提供者、语音录制者，以及网站开发和维护人员。本项目负责数据整理与 Android 应用开发，原始词典内容并非本项目原创。

本应用为独立项目，并非来源网站的官方应用，不代表原作者或相关机构的认可或背书。

## 许可与签名

应用源码的整体许可证尚未指定。词条、释义、语音、字体和指南图片等第三方资料的权利归各自权利人所有；公开下载、来源标注与致谢不等于再分发授权。完整词库与语音的再分发授权尚未确认，相关说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

现有发布说明中的 APK 使用调试签名。长期发布应妥善管理签名密钥，覆盖升级需要保持签名一致。密钥、密码及其他凭据不要提交到仓库。

## English

**Mongolian–Chinese Dictionary** is an offline dictionary and vocabulary study app for Android 8.0 or later, with Chinese and English interfaces.

### Download

Get `mongolian-chinese-dictionary.apk` from [Releases](https://github.com/73wong/mongolian-chinese-dictionary/releases). The APK includes dictionary data and available recordings; regular users do not need the data archive or crawler tools.

This README describes version 1.1.2. Refer to each Release for its included changes.

### Features and data

- Vertical traditional Mongolian, Roman transliteration, IPA, Cyrillic Mongolian, definitions and examples, where available.
- Search filters, optional transliteration equivalences, offline audio, favorites, study rounds and calendar statistics.
- Dictionary import/export, long-press text copying, bilingual input guides and entry feedback through GitHub Issues.
- The current compiled dictionary has **53,413 entries** after corrections and duplicate merging. The original archive contains 53,412 entries, with audio references for 25,911 entries across 25,903 audio files.

Missing recordings have not been replaced with similar-word recordings or synthetic speech. Favorites and study progress stay on the device and are not automatically synchronized.

### Build

Requirements: JDK 21, Android SDK Platform 35 and Python 3.9+. Download the [original data archive](https://github.com/73wong/mongolian-chinese-dictionary/releases/download/v1.1.0/dictionary-data.zip), then run from the repository root:

```sh
python3 tools/restore_data.py /path/to/dictionary-data.zip
chmod +x gradlew
./gradlew assembleDebug
```

Gradle generates the indexed runtime database automatically. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [runtime database documentation](docs/runtime-database.md) for architecture and validation details.

### Acknowledgements and rights

The content was primarily collected from the [source dictionary website](http://hkuri.cneas.tohoku.ac.jp/p01/mongol/list?groupId=12). We thank the dictionary compilers, material and audio contributors, and website developers and maintainers.

This is an independent project, not an official app of the source website. Third-party rights remain with their holders. The project-wide source license has not been specified, and authorization to redistribute the complete dictionary and audio has not yet been confirmed. See [third-party notices](THIRD_PARTY_NOTICES.md).

Report corrections and app problems through [GitHub Issues](https://github.com/73wong/mongolian-chinese-dictionary/issues).
