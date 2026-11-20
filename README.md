# Tacz渲染改进

本模组适配 Minecraft 1.21.1、NeoForge 21.1.238 及以上的 21.1.x、Java 21 和 TaCZ 1.2.0。模组版本为 `1.21.1.1`，不包含枪包模型和贴图。

第一人称枪体和普通 cutout 附件使用常驻局部网格、固定骨骼编号和 GPU 顶点骨骼变换。每帧更新姿态，避免重复生成和上传整枪顶点；符合条件的静态匿名方块预烘焙到父骨骼，瞄具局部模型共用几何缓存。TaCZ 的动画、手臂、瞄具、枪口、弹壳和粒子逻辑继续执行。

掉落物、物品展示框和第三人称右手枪械按外观、材质和显示上下文缓存，合并兼容实例后通过硬件实例化绘制。附件、变体、弹药可见状态和 LOD 参与外观键，位置、光照等属于实例数据。特殊预览保留 TaCZ 原有渲染。

光影使用 Iris，保留原材质程序和渲染目标，为顶点程序增加骨骼或实例输入。GPU 加速需要实际 OpenGL 4.3 或以上上下文，无法接管时使用原有路径。

模组配置界面提供独立开关，配置文件为 `config/tacz_render_improvements-client.toml`：

```toml
optimizeItemGuns = true
optimizeHeldGuns = true
meshBudgetMiB = 256
```

使用 Java 21 构建：

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE '.jdks/jbr-21.0.11'
.\gradlew.bat build
```

产物：`build/libs/tacz-render-improvements-1.21.1.1.jar`。TaCZ 通过已发布的 Modrinth 版本解析；构建自动提取 TaCZ 内嵌的 SimpleBedrockModel 作为编译依赖，无需本地开发 jar。

[实现说明](TECHNICAL_GUIDE.md)

## 许可证

本项目采用 [GPL-3.0-only](LICENSE)，与 [TaCZ 上游代码](https://github.com/MCModderAnchor/TACZ/blob/1.21.1/readme.md#license) 的许可证一致。TaCZ 美术资源另受 CC BY-NC-ND 4.0 约束，不随本项目分发。
