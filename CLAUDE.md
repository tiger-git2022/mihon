# 给 Claude 的说明 / Notes for Claude

这是 Tiger 的 Mihon 个人分支，开发分支是 `custom`，`main` 和上游保持一致（加了 OneDrive 同步，配合 [tiger-git2022/ehviewer-onedrive](https://github.com/tiger-git2022/ehviewer-onedrive) 使用）。

- **用中文回复**仓库作者。
- **提交和 PR 标题**写成「中文 / English」，PR 正文中文在前；PR 合进 `custom`。
- **自动合并**：自己开的 PR，只要 CI（Tiger CI）通过（或没有要跑的检查）、没有冲突、审查意见都已处理，就直接合并，不用再问。用 squash 合并，标题保留「中文 / English (#编号)」。只有需要作者拍板的设计问题，或者不打算按审查意见修改的，才先问再合并。
- **合并后检查发布**：推到 `custom` 会由 `.github/workflows/tiger-release.yml` 发布带编号的版本（`v<上游版本>-tN`）。合并后确认这次发布成功；失败就修好，再开 PR、合并。
- 改动尽量放在独立文件里，必须改上游文件时集中改、标 `Fork` 注释，方便合并上游（见 `.github/README.md`）。
