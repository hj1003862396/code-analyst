# 引入 Simple-Mind-Map 并进行静态资源拆分 设计文档

本文档描述了将现有 D3.js 树图引擎升级为 Simple-Mind-Map (思维导图) 引擎的架构设计与实现方案，同时涵盖对后端 DTO/Entity 过滤逻辑的升级，以及前端 `index.html` 的结构、样式与逻辑拆分。

## 1. 目标
1. **高交互性脑图展示**：引入 `simple-mind-map`，替换当前的 D3.js 树图，支持右侧延伸的逻辑树、节点动态选中、拖动及精确缩放。
2. **DTO/Entity 自动忽略**：后端过滤掉 DTO、Entity 等数据承载类的通用方法（如 getter/setter/builder），使调用链聚焦于业务逻辑。
3. **前端资源拆分**：将现有的单 HTML 文件拆分为 `index.html`、`index.css` 和 `index.js`，提升代码可读性与维护性。

---

## 2. 后端设计：DTO & Entity 自动过滤
在 `JavaSourceParser.java` 中升级 [isIgnoredCall](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/parser/JavaSourceParser.java#L70-L94) 过滤方法。

### 规则
如果变量的类名以以下常见数据模型后缀（不区分大小写）结尾，则不分析其方法调用：
* `DTO`, `Entity`, `VO`, `PO`, `Req`, `Resp`, `Request`, `Response`, `Param`, `Params`, `Query`, `Dto`, `Vo`, `Po`

### 实现方案
```java
private boolean isIgnoredCall(String objectName, String objectType, String methodName) {
    if (objectName == null) return false;
    String lowerName = objectName.toLowerCase();
    
    // 过滤日志和标准流
    if (lowerName.equals("log") || lowerName.equals("logger") || lowerName.equals("system.out") || lowerName.equals("system.err") || lowerName.equals("out") || lowerName.equals("err")) {
        return true;
    }
    
    if (objectType != null) {
        String cleanType = objectType.replaceAll("<.*>", "");
        
        // 过滤标准 JDK 类型调用
        if (cleanType.equals("String") || cleanType.equals("List") || cleanType.equals("Map") || cleanType.equals("Set") 
                || cleanType.equals("ArrayList") || cleanType.equals("HashMap") || cleanType.equals("HashSet") 
                || cleanType.equals("Collections") || cleanType.equals("Objects") || cleanType.equals("Arrays") 
                || cleanType.equals("Optional") || cleanType.equals("Stream") || cleanType.equals("Collectors") 
                || cleanType.equals("Logger") || cleanType.equals("LoggerFactory") || cleanType.equals("System")
                || cleanType.equals("BigDecimal") || cleanType.equals("Integer") || cleanType.equals("Long") 
                || cleanType.equals("Boolean") || cleanType.equals("Double") || cleanType.equals("Character")) {
            return true;
        }
        
        // 新增：过滤 DTO, Entity, VO, PO, Req, Resp, Query 等类型
        String lowerType = cleanType.toLowerCase();
        if (lowerType.endsWith("dto") || lowerType.endsWith("entity") || lowerType.endsWith("vo") || lowerType.endsWith("po") 
                || lowerType.endsWith("req") || lowerType.endsWith("resp") || lowerType.endsWith("request") || lowerType.endsWith("response")
                || lowerType.endsWith("param") || lowerType.endsWith("params") || lowerType.endsWith("query")) {
            return true;
        }
    }
    
    return false;
}
```

---

## 3. 前端设计：资源拆分与 Simple-Mind-Map 集成

### 3.1 文件结构变化
* [MODIFY] [index.html](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.html) (只保留 HTML 骨架与 Vue 挂载模板)
* [NEW] [index.css](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.css) (存储所有 CSS 代码)
* [NEW] [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) (基于 ESM `<script type="module">` 导入，管理 Vue & Simple-Mind-Map)

### 3.2 index.html 引入 CDN 资源
在 `<head>` 中引入 `simple-mind-map` 的样式与外部文件，并引用拆分出来的本地 css 和 js：
```html
<!-- simple-mind-map 样式 -->
<link href="https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.css" rel="stylesheet">
<!-- 本地 CSS -->
<link rel="stylesheet" href="./index.css">

<!-- 用 div 容器替换原 SVG 画布 -->
<div id="mindMapContainer"></div>

<!-- 本地 JS (使用 module 模式) -->
<script type="module" src="./index.js"></script>
```

### 3.3 index.js 初始化与交互
```javascript
import MindMap from 'https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.min.js';

// 初始化 Vue 应用
const { createApp, ref, onMounted, markRaw } = Vue;

createApp({
    setup() {
        const entry = ref({
            className: 'com.omp.marketing.intf.web.ShortLinkController',
            methodName: 'detail'
        });
        const selectedNode = ref(null);
        const leftCardCollapsed = ref(false);
        const rightDrawerCollapsed = ref(false);
        const zoomPercent = ref(100);
        const dragMode = ref(false);

        let mindMapInstance = null;
        let rawTreeData = null; // 本地保存的后端树结构 JSON，用于匹配与增量修改

        // 递归映射后端节点到 Simple-Mind-Map 支持的格式
        const transformNode = (backendNode) => {
            if (!backendNode) return null;
            const text = backendNode.isMapper ? `💾 ${backendNode.label}` : backendNode.label;
            const simpleNode = {
                data: {
                    text: text,
                    id: backendNode.id,
                    className: backendNode.className,
                    methodName: backendNode.methodName,
                    isMapper: backendNode.isMapper,
                    dbOperations: backendNode.dbOperations || []
                },
                children: []
            };
            if (backendNode.children) {
                simpleNode.children = backendNode.children.map(c => transformNode(c));
            }
            return simpleNode;
        };

        // 递归在后端树结构中查找对应 ID 的节点
        const findNodeInTree = (node, id) => {
            if (node.id === id) return node;
            if (node.children) {
                for (const child of node.children) {
                    const found = findNodeInTree(child, id);
                    if (found) return found;
                }
            }
            return null;
        };

        // 选定一个节点进行数据获取与增量展开
        const selectNode = async (nodeData) => {
            selectedNode.value = nodeData;
            rightDrawerCollapsed.value = false;

            // 如果节点没有子节点，且不是 mapper，则增量加载
            if (!nodeData.children && !nodeData.isMapper) {
                try {
                    const res = await fetch('/api/tree/expand', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            className: nodeData.className,
                            methodName: nodeData.methodName
                        })
                    });
                    if (res.ok) {
                        const children = await res.json();
                        if (children && children.length) {
                            // 在 rawTreeData 中找到该节点并注入子节点
                            const rawNode = findNodeInTree(rawTreeData, nodeData.id);
                            if (rawNode) {
                                rawNode.children = children;
                                nodeData.children = children; // 同步给 Vue template 的 selectedNode

                                // 转换为 simple-mind-map 数据并重载
                                const mapData = transformNode(rawTreeData);
                                mindMapInstance.setData(mapData);
                            }
                        }
                    }
                } catch (e) {
                    ElementPlus.ElMessage.error('加载子节点失败：' + e.message);
                }
            }
        };

        const initTree = async () => {
            try {
                const res = await fetch('/api/tree/initialize', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(entry.value)
                });
                if (!res.ok) {
                    ElementPlus.ElMessage.error('初始化失败，请检查后端服务。');
                    return;
                }
                rawTreeData = await res.json();
                
                // 渲染 MindMap
                const mapData = transformNode(rawTreeData);
                if (mindMapInstance) {
                    mindMapInstance.setData(mapData);
                } else {
                    mindMapInstance = new MindMap({
                        el: document.getElementById('mindMapContainer'),
                        data: mapData,
                        layout: 'logicalStructure', // 右侧逻辑结构
                        theme: 'classic',
                        readonly: true // 设置为只读防止双击编辑文本
                    });

                    // 监听节点点击激活事件
                    mindMapInstance.on('node_active', (nodeInstance) => {
                        if (nodeInstance) {
                            const data = nodeInstance.getData().data;
                            selectNode(data);
                        }
                    });

                    // 监听缩放修改事件，同步 zoomPercent
                    mindMapInstance.on('scale_change', (scale) => {
                        zoomPercent.value = Math.round(scale * 100);
                    });
                }
                
                await selectNode(rawTreeData);
                leftCardCollapsed.value = true;
            } catch (e) {
                ElementPlus.ElMessage.error('无法初始化脑图：' + e.message);
            }
        };

        // 钻取分析逻辑
        const drillDown = (child) => {
            selectNode(child);
            // 并在 mindMap 实例中高亮并激活对应的节点
            if (mindMapInstance && mindMapInstance.renderer && mindMapInstance.renderer.root) {
                const findAndActive = (nodeInst) => {
                    if (nodeInst.getData().data.id === child.id) {
                        nodeInst.active();
                        mindMapInstance.execCommand('GO_TARGET_NODE', nodeInst);
                        return true;
                    }
                    if (nodeInst.children) {
                        for (const c of nodeInst.children) {
                            if (findAndActive(c)) return true;
                        }
                    }
                    return false;
                };
                findAndActive(mindMapInstance.renderer.root);
            }
        };

        // 缩放操作 API 对接
        const zoomIn = () => {
            if (mindMapInstance) {
                let scale = mindMapInstance.view.scale + 0.1;
                if (scale > 3) scale = 3;
                mindMapInstance.view.setScale(scale);
            }
        };

        const zoomOut = () => {
            if (mindMapInstance) {
                let scale = mindMapInstance.view.scale - 0.1;
                if (scale < 0.2) scale = 0.2;
                mindMapInstance.view.setScale(scale);
            }
        };

        const zoomReset = () => {
            if (mindMapInstance) {
                mindMapInstance.reset();
            }
        };

        return {
            entry,
            selectedNode,
            initTree,
            leftCardCollapsed,
            rightDrawerCollapsed,
            zoomPercent,
            zoomIn,
            zoomOut,
            zoomReset,
            drillDown
        };
    }
}).use(ElementPlus).mount('#app');
```

---

## 4. 验证方案

### 4.1 编译测试
* 运行 Maven 打包，确保后端 Java 源码无编译报错：
  `mvn clean package -DskipTests`

### 4.2 交互验证
1. 打开浏览器访问 `http://localhost:8082`。
2. 确认网页展现出 XMind 风格的高级思维导图。
3. 双击、拖拽并体验流畅的节点缩放与平移。
4. 确认子方法中的 DTO/Entity 调用被自动屏蔽。
5. 在左侧面板中展开不同的子调用，脑图能够动态增加节点、展开并高亮选中的节点。
