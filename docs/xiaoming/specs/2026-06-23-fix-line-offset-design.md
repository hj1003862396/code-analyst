# 2026-06-23 G6 关系图连线偏移与视觉关联问题修复设计规格文档

本文档定义了修复调用链分析关系图中连线（Edge）位置向下偏移、节点卡片间距重合，以及由此产生的非预期节点连线关联错觉的具体修改设计。

---

## 1. 变更背景与原因分析

当前系统使用 AntV G6 (v4.8.x) 结合 Dagre 布局引擎绘制有向无环图（DAG），但存在两个关联问题：
1. **连线向下偏移与重叠**：连线的起点和终点没有紧贴卡片的左右两侧中心边缘，且卡片上下排列过于拥挤甚至重合。
2. **非预期关联视觉错觉**：由于折叠状态的 sibling 节点（如 `memberInvoice`）恰好处于被展开的节点下方，且由于上述偏移量，上游展开方法引出的多条线在向下弯曲时重合在该未展开节点的右侧位置，导致用户产生“点击展开某节点，未展开的其他节点也连线并自动关联”的错觉。

### 原因：
G6 的 Dagre 布局计算和 `getAnchorPoints` 锚点定位在解析节点位置坐标时，使用了节点在 `nodes` 数据模型中的 `size` 属性。由于之前在 Vue setup 的 `nodes` 列表中未声明节点的 `size`（宽、高），布局引擎只能回退使用 G6 内部极小的默认节点尺寸（30x30）。
这使得 Dagre 生成的层级布局和连线控制点全部基于 30x30 的虚拟尺寸进行运算。而在最终的 `registerNode` 阶段，实际渲染的 DOM 卡片是 `300x120`（无 Body）或 `300x240`（有 Body），导致视觉卡片与底层计算边界严重不符，产生巨大的偏移量。

---

## 2. 设计与改动细节

### 2.1 G6 全局 Graph 配置修改
在 `index.js` 的 `initGraph` 方法中，为 `defaultNode` 增加默认的 size 配置，保证新加入但尚未动态赋值的节点有统一的尺寸预期。
```javascript
defaultNode: {
    type: 'custom-node',
    size: [300, 120]
}
```

### 2.2 节点数据模型动态赋值
由于卡片根据是否包含“方法简介（remarks）”或“SQL 语句（dbOperations）”来动态决定高度（`120` 或 `240`），必须在节点被推入 `nodes` 响应式列表之前，就在数据层直接算好并存入 `size: [300, height]`，确保 Dagre 引擎能按真实的尺寸参与布局演算。

#### 2.2.1 根节点初始化 (initTree)
```javascript
const hasBody = rootData.remarks || (rootData.isMapper && rootData.dbOperations && rootData.dbOperations.length > 0);
const height = hasBody ? 240 : 120;

nodes.push({
    id: rootData.id,
    className: rootData.className,
    methodName: rootData.methodName,
    isMapper: rootData.isMapper,
    dbOperations: rootData.dbOperations,
    remarks: rootData.remarks,
    isRoot: true,
    collapsed: true,
    size: [300, height] // 声明实际大小
});
```

#### 2.2.2 子节点展开 (expandNodeChildren)
```javascript
apiChildren.forEach(child => {
    let existingNode = nodes.find(n => n.id === child.id);
    if (!existingNode) {
        const hasBody = child.remarks || (child.isMapper && child.dbOperations && child.dbOperations.length > 0);
        const height = hasBody ? 240 : 120;
        nodes.push({
            id: child.id,
            className: child.className,
            methodName: child.methodName,
            isMapper: child.isMapper,
            dbOperations: child.dbOperations,
            remarks: child.remarks,
            collapsed: true,
            size: [300, height] // 声明实际大小
        });
    }
    ...
});
```

### 2.3 注册自定义节点
在 `G6.registerNode('custom-node')` 的 `draw` 方法中，`height` 计算依然遵循 `hasBody ? 240 : 120`，并与 `cfg.size`（即 `[300, height]`）完美匹配。

---

## 3. 验证计划

### 自动验证测试
* 执行 `mvn test` 确认后端解析器与用例全部通过，保持无回归错误。

### 手动功能验证
1. 启动应用加载默认方法调用链，点击根节点的 `+` 按钮。
2. 确认生成的子节点中，`chargingInvoice` 与 `memberInvoice` 分布在上下两侧，间距合理，没有重合或遮挡。
3. 点击展开 `chargingInvoice`，确认连线从它的 `-` 按钮（右侧中心点）发出，并准确连接到右侧所有子卡片的左侧中心点。
4. 验证当 `memberInvoice` 保持在折叠状态（`+` 按钮显示）时，其右侧不再出现任何多余的、指向 `saveInvoice` 或其他节点的连线弯折轨迹，彻底解决视觉上的异常关联问题。
