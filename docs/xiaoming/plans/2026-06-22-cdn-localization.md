# CDN 依赖本地化 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 将项目中所使用的全部前端第三方 CDN 依赖（JS、CSS 库和 Google Fonts 字体资源）本地化，以大幅提升网站的离线与慢网加载速度。

**架构：** 使用一个 Python 脚本来自动抓取和下载远程的静态库，以及抓取、下载并重构 Google Fonts CSS 文件中的所有字体切片；通过更改 index.html 和 index.js 中的依赖链接指向本地相对路径以适配修改。

**技术栈：** HTML5, ES Module, Python 3, Spring Boot (Maven)

---

### Task 1：编写并执行本地依赖下载脚本

**文件：**
- 创建：`scratch/download_libs.py`
- 测试：不涉及

- [ ] **步骤 1：创建 `scratch/` 临时文件夹并编写依赖下载脚本**

在 `scratch/download_libs.py` 写入以下完整的下载和字体解析处理脚本：

```python
import os
import re
import urllib.request

# 创建文件夹
def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

# 下载文件函数，带自定义 User-Agent
def download_url(url, dest_path):
    print(f"Downloading {url} to {dest_path}...")
    req = urllib.request.Request(
        url, 
        headers={'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
    )
    with urllib.request.urlopen(req) as response:
        with open(dest_path, 'wb') as f:
            f.write(response.read())

STATIC_LIB = "src/main/resources/static/lib"

# 1. 下载基础静态库
ensure_dir(f"{STATIC_LIB}/vue")
ensure_dir(f"{STATIC_LIB}/element-plus")
ensure_dir(f"{STATIC_LIB}/marked")
ensure_dir(f"{STATIC_LIB}/simple-mind-map")

libs = {
    "https://unpkg.com/vue@3/dist/vue.global.js": f"{STATIC_LIB}/vue/vue.global.js",
    "https://unpkg.com/element-plus/dist/index.css": f"{STATIC_LIB}/element-plus/index.css",
    "https://unpkg.com/element-plus/dist/index.full.min.js": f"{STATIC_LIB}/element-plus/index.full.min.js",
    "https://unpkg.com/marked/marked.min.js": f"{STATIC_LIB}/marked/marked.min.js",
    "https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.css": f"{STATIC_LIB}/simple-mind-map/simpleMindMap.css",
    "https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.min.js": f"{STATIC_LIB}/simple-mind-map/simpleMindMap.esm.min.js"
}

for url, dest in libs.items():
    download_url(url, dest)

# 2. Google 字体下载及处理
fonts_dir = f"{STATIC_LIB}/fonts"
fonts_files_dir = f"{fonts_dir}/files"
ensure_dir(fonts_files_dir)

google_font_css_url = "https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
req = urllib.request.Request(
    google_font_css_url,
    headers={'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
)

print(f"Fetching Google Fonts CSS...")
with urllib.request.urlopen(req) as response:
    css_content = response.read().decode('utf-8')

# 正则寻找所有 woff2 链接
woff2_urls = re.findall(r'url\((https://[^\)]+\.woff2)\)', css_content)
print(f"Found {len(woff2_urls)} font files.")

# 下载并替换链接
for url in woff2_urls:
    filename = url.split('/')[-1]
    local_dest = f"{fonts_files_dir}/{filename}"
    download_url(url, local_dest)
    css_content = css_content.replace(url, f"./files/{filename}")

# 保存本地 fonts.css
with open(f"{fonts_dir}/fonts.css", "w", encoding="utf-8") as f:
    f.write(css_content)

print("Dependencies localization done!")
```

- [ ] **步骤 2：运行下载脚本**

在终端运行此脚本以下载所有的静态依赖：
运行：`python3 scratch/download_libs.py`
预期输出：
```text
Downloading https://unpkg.com/vue@3/dist/vue.global.js ...
...
Found X font files.
Downloading https://fonts.gstatic.com/... ...
Dependencies localization done!
```

- [ ] **步骤 3：验证本地静态依赖目录的生成情况**

检查所有目标文件是否均已完整生成且大小正常：
运行：`find src/main/resources/static/lib -type f`
验证：确认列出了 `vue.global.js`, `index.css`, `index.full.min.js`, `marked.min.js`, `simpleMindMap.css`, `simpleMindMap.esm.min.js`, `fonts.css` 以及数个 `.woff2` 字体文件。

---

### Task 2：适配页面引入路径

**文件：**
- 修改：`src/main/resources/static/index.html`
- 修改：`src/main/resources/static/index.js`
- 测试：不涉及

- [ ] **步骤 1：修改 `index.html` 的引入路径**

编辑 `src/main/resources/static/index.html`，定位到 `<head>` 区域，进行如下替换：

将原来的：
```html
    <!-- 引入 Vue 3, Element Plus -->
    <script src="https://unpkg.com/vue@3/dist/vue.global.js"></script>
    <link rel="stylesheet" href="https://unpkg.com/element-plus/dist/index.css" />
    <script src="https://unpkg.com/element-plus"></script>
    <!-- Marked 用于富文本渲染 -->
    <script src="https://unpkg.com/marked/marked.min.js"></script>
    <!-- Google 字体 -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- simple-mind-map 脑图样式 -->
    <link href="https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.css" rel="stylesheet">
```

替换为：
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

- [ ] **步骤 2：修改 `index.js` 中的脑图 ESM 引入路径**

编辑 `src/main/resources/static/index.js` 的第 1 行：

将原来的：
```javascript
import MindMap from 'https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.min.js';
```

替换为：
```javascript
import MindMap from './lib/simple-mind-map/simpleMindMap.esm.min.js';
```

- [ ] **步骤 3：启动 Spring Boot 应用并验证网络加载**

执行：`mvn spring-boot:run`
当应用启动完毕后，在浏览器中打开：`http://localhost:8080/index.html`。
按 `F12` 打开 DevTools -> Network 标签，刷新页面：
1. 观察并确认所有的 js, css, woff2 请求均来自 `http://localhost:8080/lib/...`，且响应状态码不为 404。
2. 确认无任何对外部域名的请求（如 `unpkg.com`、`jsdelivr.net`、`fonts.googleapis.com`）。
3. 检查页面中脑图、Element-Plus 组件能否完全正常显示与交互。
