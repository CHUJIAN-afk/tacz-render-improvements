# 实现说明

本分支目标环境为 Minecraft 1.21.1、NeoForge 21.1.238、Java 21、TaCZ 1.2.0 和 SimpleBedrockModel 2.6.2。后者由 TaCZ 内嵌提供，构建自动提取并以 compileOnly 引用其几何捕获 API。

1.21.1 使用新的 VertexConsumer/ByteBufferBuilder 接口。几何编译器在下一次 addVertex、读取索引边界或 finish 时提交上一顶点，确保最后一个顶点及跨骨骼边界的属性完整。NeoForge 事件、配置和资源重载接口已迁移，光影桥接改用 Iris；核心常驻几何和实例化架构沿用原分支。

## 第一人称

`BedrockModelMixin` 为普通第一人称 cutout 模型取得专用材质层和跟踪 consumer。`HeldGunRenderer` 在首次遇到模型时启动后台局部几何编译，将几何片段编号与动画姿态编号分开。匿名、无子节点、无独立发光和单位缩放的方块叶节点预烘焙到父骨骼坐标；其他节点保留独立姿态。编译不读取具名骨骼的动画姿态，不访问 OpenGL；准备期间原渲染照常执行。

`GeometryCompiler` 预计算切线、UV 中点和三角形索引，保留骨骼、UV、硬边法线、颜色与光照差异，删除退化面。它没有实现有损减面或跨活动骨骼的遮挡删除。

`BedrockPartMixin` 替换已编译部件的 compile，并跳过已烘焙叶节点的矩阵计算；具名骨骼、动画和功能节点仍由 TaCZ 执行。每帧校验叶节点参数，发生改变时采用原渲染。最终矩阵直接写入复用的姿态缓冲，不生成每帧逐顶点偏移数据。

`BedrockAttachmentModelMixin` 接通瞄具局部模型入口，几何与完整模型共享缓存。在模板、深度、镜内画面及材质阶段原有的 flush 位置提交，保留原执行顺序。镜片效果、二维模板、手臂和特效继续由 TaCZ 控制。

顶点缓冲不随可见性变化重建。每组可见部件按 TaCZ 实际提交顺序建立索引缓存，最多保留 32 种组合；动作矩阵不进入缓存键。同一部件在一次渲染中被重复使用时拆分姿态批次，避免后一次矩阵覆盖前一次。

## 物品形式枪械

`ExternalGunItemVboRendererMixin` 接管 TaCZ 原生支持的 GROUND、FIXED 和 THIRD_PERSON_RIGHT_HAND 上下文。通过 `ExternalGunAppearance.GeometryKey` 解析外观，再使用 `DisplayGunGeometry` 和 `GeometryCollector` 捕获一次局部几何。

材质几何常驻本模组的 VBO/EBO；同一外观、材质和缓冲所有者下的提交合并到实例缓冲，通过 `glDrawElementsInstanced` 绘制。每个实例保存模型矩阵、法线矩阵、颜色、光照、overlay 和 Iris 标识。第三人称枪口与特效回调仍逐实例执行。阴影阶段优先选择 TaCZ 已有的远距模型。

Iris 会在实体渲染内部传入临时 lambda buffer source。`EntityRenderDispatcherMixin` 保存实际的渲染缓冲所有者，使临时包装仍能共享实例批次，同时继续由包装处理原有渲染状态。

TaCZ 当前的第三人称副手不绘制枪械，GUI 使用二维图标；本模组保持这些原有表现。改装预览与 TaCZ 展示槽位的特殊上下文保留原路径。

## 光影程序与状态

`RenderPipeline.Layer` 在原材质 clear 之前提交批次。原生 marker 绘制建立当前材质的默认 uniforms；原 `ShaderInstance.apply` 设置光影所需的纹理、图像、混合状态和写入目标。

`ShaderTransform` 按标识符和输入声明改写顶点来源，将原 main 保留为内部入口，并在调用前应用骨骼/实例变换。`ShaderBridge` 保留原片元和几何着色阶段、属性及片元输出绑定、uniform blocks 与 shader storage bindings。Iris 的 entity/item/block 标识按原短整数符号保存。

本模组没有修改光影包文件，也没有替换光影的片元材质逻辑。派生程序失败后记录一次原因，当前提交通过 CPU 几何补画，后续该材质回到原路径，直到管线或资源重载。细分着色阶段直接使用原路径。

绘制后恢复 VAO、数组缓冲、SSBO 绑定及其范围，并通过原 ShaderInstance 清理程序状态。主目标的 stencil 在帧开始时准备，避免首次开镜在光影帧中重建深度模板附件。

## 缓存与兼容边界

资源版本和 Iris 管线身份变化会失效所有几何、可见索引、派生程序与材质层。配置文件重载和退出世界同样释放 GPU 对象。预算淘汰不删除本帧仍使用的模型。

未知第一人称半透明材质、特殊 buffer source 和动态预览仍由原渲染器处理。

本实现保留 TaCZ 原动画状态机与具名骨骼遍历；没有实现 Nanite、屏幕误差 LOD、GPU 动画状态机或跨活动骨骼合并。整体帧率仍可能受光影片元计算、其他实体或 TaCZ 动画开销限制。
