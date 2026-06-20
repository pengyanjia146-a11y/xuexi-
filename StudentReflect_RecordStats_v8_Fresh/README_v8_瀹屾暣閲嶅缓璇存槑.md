# 学生状态记录 v8 Fresh Clean

这是完整重建版源码。特点：

- 不需要旧项目文件，适合清空仓库后重新上传。
- 包名：`com.yanjia.studentreflect.v8fresh`，避免和旧版本签名冲突。
- 一天连续计时：开始今天 → 切换状态 → 结束今天。
- 状态可自行添加、删除、排序。
- 当前状态读秒、今日连续读秒。
- 息屏不算逃离学习：只有学习状态中实际使用其他 App，才会被系统使用记录被动判定。
- 违规不能主动选择，只能被动生成。
- App 类型使用“打勾式选择”。
- App 类型包含“学习类应用”。
- App 类型和 App 归类规则可导出 JSON、复制 JSON、保存 JSON 文件。
- 总记录可导出 JSON、复制 JSON、保存 JSON 文件。
- 评分包含：学习投入、连续学习链、深度学习、专注纪律、娱乐控制、生活状态、反思复盘。

## GitHub 打包

上传本文件夹中的内容到仓库根目录，确保仓库首页直接看到：

- `.github`
- `app`
- `build.gradle`
- `settings.gradle`

然后进入 Actions，运行：

`Build Android APK v8 Fresh Clean`

成功后下载 artifact：

`student-reflect-v8-fresh-apk`

解压后安装里面的 APK。

## 权限

要自动判断学习期间用了哪个 App，需要在手机设置里打开：

使用情况访问权限 → 学生状态记录 → 允许

如果没有权限，App 仍然可以计时和反思，但违规原因只能通过导入手机使用记录后重新判定。
