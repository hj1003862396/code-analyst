# CDN 依赖本地化设计规格

为了解决网站在离线或慢网环境下由于加载外部 CDN 依赖而导致打开缓慢甚至无法渲染的问题，本设计方案旨在将页面中的所有第三方 CDN JS、CSS 库和 Google Fonts 字体资源全部本地化。

## 用户审查

> [!NOTE]
> - 所有的第三方库将统一下载到 `src/main/resources/static/lib` 目录下。
> - Google Fonts（Outfit 和 JetBrains Mono）将通过脚本自动下载其全部 `.woff2` 文件，并通过本地 `fonts.css` 文件以 `@font-face` 相对路径方式引入，保证纯本地无网加载。

## 方案设计

### 1. 本地目录结构设计

将在 `src/main/resources/static/lib` 目录下建立子文件夹管理资源：

```text
src/main/resources/static/lib/
├── vue/
│   └── vue.global.js                   # Vue 3
├── element-plus/
│   ├── index.css                       # Element Plus 样式
│   └── index.full.min.js               # Element Plus 组件库
├── marked/
│   └── marked.min.js                   # Marked MD渲染库
├── simple-mind-map/
│   ├── simpleMindMap.css               # 脑图样式
│   └── simpleMindMap.esm.min.js        # 脑图核心（ESM）
└── fonts/
    ├── fonts.css                       # 本地字体引入 CSS
    └── files/                          # 下载的 .woff2 字体资源
```

### 2. 依赖下载与字体解析脚本

由于涉及多个静态库以及 Google Fonts 复杂的字体切片，我们将提供一个 Python 脚本（`download_libs.py`）来处理下载：
- **静态库**：直接下载其最新版本 CDN 链接对应的文件。
- **Google 字体**：
  1. 使用现代浏览器 User-Agent 头（如 Chrome）向 Google Fonts API 发送 HTTP 请求，拉取针对 `.woff2` 的 CSS 内容。
  2. 正则解析 CSS 内容中的所有 `url(https://fonts.gstatic.com/s/.../*.woff2)` 链接。
  3. 将这些字体文件下载至 `/static/lib/fonts/files/` 目录下，并以它们原本的文件名（如哈希字符串）保存。
  4. 将 CSS 中的所有远程 `fonts.gstatic.com` 路径更新为本地相对路径 `./files/`，并保存为 `fonts.css`。

### 3. 代码适配

#### 3.1 修改 [index.html](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.html)

替换 head 中所有的 CDN 引入：

```html
<!-- 引入 Vue 3, Element Plus -->
<script src="./lib/vue/vue.global.js"></script>
<link rel="stylesheet" href="./lib/element-plus/index.css" />
<script src="./lib/element-plus/index.full.min.js"></script>
<!-- Marked 用于富文本渲染 -->
<script src="./lib/marked/marked.min.js"></script>
<!-- Google 字体 -->
<link rel="stylesheet" href="./lib/fonts/fonts.css">

<!-- simple-mind-map 脑图样式 -->
<link href="./lib/simple-mind-map/simpleMindMap.css" rel="stylesheet">
```

同时，移除原先 the `preconnect` 节点，减少无效的网络解析。

#### 3.2 修改 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js)

将 `simple-mind-map` 的 ESM 引入替换为本地路径：

```javascript
import MindMap from './lib/simple-mind-map/simpleMindMap.esm.min.js';
```

## 验证计划

### 自动验证 (脚本/运行)
- 启动 Spring Boot 后端项目并在浏览器中打开。
- 打开 DevTools 的 Network 面板，刷新页面并确认：
  - 没有任何对外部 CDN（`unpkg.com`, `jsdelivr.net`, `googleapis.com`, `gstatic.com`）的网络请求。
  - 所有 JS、CSS 和 `.woff2` 字体资源均来自 `http://localhost:8080/lib/...`，且响应状态码为 `200` 或 `304`。

### 手动验证
- 检查页面排版是否正常（特别是 Outfit 字体和 JetBrains Mono 字体渲染是否生效）。
- 检查脑图是否能够正常加载与渲染，操作是否流畅。
- 检查右侧/左侧分析入口的 Element-Plus 组件样式 and 交互是否正常。
