#!/bin/zsh
cd -- "${0:A:h}"
python3 tools/crawl_dictionary.py
result=$?
echo ""
if [ "$result" -ne 0 ]; then echo "更新未完成，详情见 collection/crawler/report.json。可修复后重新运行。"; fi
read "?按回车关闭窗口…"
exit "$result"
