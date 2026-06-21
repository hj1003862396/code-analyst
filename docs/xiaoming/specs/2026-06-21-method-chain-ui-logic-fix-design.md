# 方法调用链精准分析与前端 UI/交互逻辑修复 设计文档

本文档描述了对 code-db-analyst 中方法调用链分析引擎后端重名类精确匹配逻辑、前端 Vue-D3 响应式代理缺陷以及 XMind 连接折线样式的修复与升级方案。

## 1. 目标

1. **同名接口精准识别**：当分析目标项目中存在同名接口或类（例如多个模块下均有 `ShortLinkService`）时，后端能根据文件 imports 关系与接口实现类的包亲和度进行精确解析匹配，避免加载错误文件导致“子方法未找到”的 Bug。
2. **点击防移交互保护**：防止在中间 SVG 画布上点击节点后，节点产生坐标飘移、弹走或消失的现象。
3. **白板极简高档浅色风格（XMind 风格）**：
   - 界面整体由暗色模式全面升级为**极简高质感浅色白板主题**：背景采用浅灰/纯白，控制面板和卡片使用纯白背景与精致阴影。
   - 连接线采用扁平亮蓝色（`#4F73DF`），使用 90 度直角折线连接。
   - **节点样式重构**：
     - **主卡片（根节点）**：使用实心圆角矩形，底色为蓝色（`#4F73DF`），文字为白色。
     - **分支节点（子方法）**：去除卡片矩形边框与背景色，改用**极简文本 + 底部下划线（Underline）风格**，下划线与连接线颜色一致并无缝汇合，完美还原高品质思维导图视觉。

---

## 2. 系统设计

### 2.1 后端：同名类精确匹配与包亲和度检索

在 [ApiController.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/web/ApiController.java) 中，我们将重构 `findJavaFile` 方法，并实现基于上下文的优先级打分逻辑：

#### A. 完全限定名定位
若入参 `className` 中包含 `.`（如全限定名 `com.omp.marketing.service.shortlink.ShortLinkService`），直接将其点号替换为 `/`，采用后缀匹配定位唯一文件：
```java
private Optional<Path> findJavaFileByFqName(String rootPath, String fqName) {
    String pathSuffix = fqName.replace('.', '/') + ".java";
    try (Stream<Path> walk = Files.walk(Path.of(rootPath))) {
        return walk.filter(p -> p.toString().replace('\\', '/').endsWith(pathSuffix)).findFirst();
    } catch (Exception e) {
        return Optional.empty();
    }
}
```

#### B. 多候选文件评分匹配
若入参为简名（如 `ShortLinkService`），且磁盘上存在多个同名文件，我们将传入调用方文件的物理路径 `sourceFilePath`，并解析其 imports：
- **Score 3**：候选类的 FQ Name 被 `sourceFilePath` 显式 `import`（最优先）。
- **Score 2**：候选类的包与 `sourceFilePath` 属于同一个包。
- **Score 1**：候选类属于 `sourceFilePath` 中通配符导入的包（如 `import xxx.*`）。
- **Score 0**：无任何匹配。

#### C. 接口实现类的包亲和度匹配
当根据接口类全限定名寻其对应的实现类 `ShortLinkServiceImpl` 时，如果找到多个实现类，计算实现类的包路径与接口包路径的**最长公共前缀**，包越亲近分值越高，防止跨模块引用了错误的实现类。

---

### 2.2 前端：极简白板主题与下划线文本节点重构

在 [index.html](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.html) 中：

#### A. 全面升级浅色白板主题 CSS
重构 `:root` 变量与全局样式：
```css
:root {
    --bg-main: #F8FAFC;       /* 浅灰色画布背景 */
    --bg-card: #FFFFFF;       /* 纯白卡片背景 */
    --border-color: #E2E8F0;  /* 边框色 */
    --primary: #4F73DF;       /* 导图核心蓝 */
    --text-main: #0F172A;     /* 主文字色 */
    --text-muted: #64748B;    /* 次要文字色 */
}
```

#### B. 防御 Vue 3 响应式代理破坏
利用 Vue 3 的 `markRaw()` 包裹层次树中的所有 D3 层次节点（如 `rootData` 和选中的 `d3Node`）。这能使得节点以非代理的原始对象格式存在，确保 D3 的内部指针比较和过渡动画计算正确，消除 `NaN` 坐标破坏。

#### C. 重构节点 SVG 结构 (主卡片 vs 下划线子节点)
在 D3 渲染中，通过判断节点深度 `d.depth` 来展示不同风格：
1. **主卡片（根节点，`d.depth === 0`）**：
   - 绘制填充为 `var(--primary)` 的圆角矩形，文字填充为 `#FFFFFF`。
2. **分支节点（子节点，`d.depth > 0`）**：
   - 矩形填充设为 `transparent`，无边框，作为点击热区。
   - 动态获取文本框宽度 `d.width = bbox.width + 20`。
   - 在节点下方绘制一条 `<line>` 元素，起点 `x1 = -10`，终点 `x2 = bbox.width - 10`，高度 `y = 12`，颜色为 `var(--primary)`，厚度为 `2px`。下划线起点恰好与连接线终点交汇。

#### D. 先节点、后连线渲染与边缘坐标计算
1. **渲染节点**：先绘制节点，将测算出的宽度赋值给 `d.width`。
2. **绘制连接线**：
   - 连线起点：`startY = d.source.y - 10 + d.source.width` (父节点右边缘)
   - 连线终点：`endY = d.target.y - 10` (子节点左边缘/下划线起点)
   - 拐点横坐标：`midY = (startY + endY) / 2`
   - 绘制指令：`M startY source.x L midY source.x L midY target.x L endY target.x`

---

## 3. 验证方案

### 3.1 自动化编译
- 执行 Maven 打包命令确保无编译报错：
  ```bash
  JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn clean package -DskipTests
  ```

### 3.2 手动功能验证
- 启动 `code-db-analyst` 本地服务，访问 `http://localhost:8080`。
- 预期：
  1. 页面完全切换为清爽干净的浅色导图白板，背景为纯白，侧边栏为带有柔和投影的纯白框。
  2. 根节点显示为亮蓝色背景的圆角矩形卡片。
  3. 子节点显示为无边框的黑色文本，并且文本正下方绘制有一条精美的蓝色下划线。
  4. 蓝色连接线完美对齐并拼接到下划线的左端点，实现 XMind 导图排版效果。
