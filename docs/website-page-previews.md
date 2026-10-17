# 网站页面预览 / Website page previews

入口：漫画详情简介下方的 **页面预览** 一排小图（前 20 张），点小图从那一页开始阅读，**全部** 打开完整网格。
只有成功取得网站逐页缩略图后才显示，其他漫画的详情不变。
目前接入 **绅士漫画（WNACG）**（包括使用同一网页结构的镜像域名）和 **HentaiHand**。
完整网格按网站顺序展示分页缩略图，滚动到底继续加载；多个章节可切换，点缩略图从对应页开始阅读。
打开预览不改阅读进度，也不下载正文原图来生成小图。

## 书源核查（2026-10-17，HentaiHand 当天复核）

| 用户指定书源 | 当前证据与处理 |
| --- | --- |
| 绅士漫画 | 公开网页有 `.gallary_item .pic_box` 逐页链接和 `/data/t/` 小图，已在线核实，接入。 |
| HentaiHand | 扩展读取的 `/api/comics/<章节>/images` 每页同时带 `thumbnail_url`（服务器小图，约原图 1/10，2026-10-17 在线核实），扩展只是没用它。按接口顺序对应阅读器页码，接入。缺任何一页的小图就整组不显示，避免页码错位。 |
| PandaChaika | 当前扩展按 ZIP 目录及字节范围读取正文，已知缩略图字段用于漫画封面，本次跳过。 |
| 哔咔 | 当前页数据是 `media.fileServer` 和 `media.path`；`thumb` 用于漫画封面，未发现可用逐页小图，本次跳过。 |
| 禁漫 | 当前扩展从阅读页取得正文图并处理混淆；已知 thumbnail 逻辑用于封面，未发现可用逐页小图，本次跳过。 |

这代表本次核查与接入范围，并不宣称其他网站永远没有缩略图接口。按用户要求不接入 E-Hentai、ExHentai、nHentai。

参考实现：

- [WNACG](https://github.com/keiyoushi/extensions-source/tree/main/src/zh/wnacg)
- [HentaiHand 页数据](https://github.com/keiyoushi/extensions-source/blob/main/lib-multisrc/hentaihand/src/eu/kanade/tachiyomi/multisrc/hentaihand/Dto.kt)
- [PandaChaika](https://github.com/keiyoushi/extensions-source/tree/main/src/all/pandachaika)
- [哔咔](https://github.com/keiyoushi/extensions-source/tree/main/src/zh/picacomic)
- [禁漫](https://github.com/keiyoushi/extensions-source/tree/main/src/zh/jinmantiantang)

## 接入约束

`WebsitePreviewParser` 只识别网站真实逐页链接与 `/data/t/` 小图，不转换原图地址、不接受封面充当页预览。
分页链接必须属于当前域名和画廊且恰好前进一页。请求使用已安装书源自己的 HTTP client 和 headers，
缩略图加载同样保留书源网络配置。元数据缓存有上限，切换章节会取消旧分页任务。
新书源必须先核实服务器小图和页序映射，再增加解析器及测试，不采用原图回退。

## English

WNACG and HentaiHand show a strip of page previews below the description once actual server thumbnails have been
retrieved; tapping one opens the reader at that page and "All" opens the full grid. The grid paginates on demand and
supports chapter selection. HentaiHand's page list already carries a `thumbnail_url` for every page. PandaChaika,
Picacomic and JM are skipped because no usable separate page-thumbnail interface was established.
Original images are never fetched to manufacture thumbnails, and previewing does not change reading progress.
