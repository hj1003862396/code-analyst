# 恢复关系线箭头 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在前端代码关系图中恢复指向目标节点的黑色三角形箭头，并保持与手柄拖拽和节点移动的联动。

**架构：** 在 G6 Graph 的 `defaultEdge` 样式配置中配置 `endArrow`，依靠 G6 引擎的渲染管道在连线终点自动计算并绘制匹配的三角形箭头。

**技术栈：** HTML5, CSS3, JavaScript, Vue.js, AntV G6 (Graph Library)

---

### Task 1：在 `src/main/resources/static/index.js` 中配置 `endArrow` 样式

**文件：**
- 修改：`src/main/resources/static/index.js:252-260`

- [ ] **步骤 1：定位 `initGraph` 函数中的 `defaultEdge` 配置项**
  定位至 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js#L252-L260)，找到 `defaultEdge` 的定义。

- [ ] **步骤 2：添加 `endArrow` 属性定义**
  在 `defaultEdge.style` 对象中，增加 `endArrow` 字段。具体修改代码如下：
  ```javascript
  defaultEdge: {
      type: 'custom-polyline',
      style: {
          radius: 10,
          offset: 20,
          stroke: '#000000',
          lineWidth: 2,
          endArrow: {
              path: G6.Arrow.triangle(8, 10, 0),
              fill: '#000000',
              stroke: '#000000',
              lineWidth: 1
          }
      }
  },
  ```

- [ ] **步骤 3：编译项目确保代码无异常**
  运行：`mvn clean compile`
  预期：编译成功，无任何错误或警告。

- [ ] **步骤 4：启动服务进行功能和交互验证**
  运行：`mvn spring-boot:run`
  预期：Spring Boot 正常启动在 8080 端口。
  打开浏览器访问 `http://localhost:8080`，依次执行：
  1. 点击左下角“加载调用链”并展开节点。
  2. 确认每一条生成的圆角折线尾部都带有指向目标节点的黑色三角形箭头。
  3. 拖拽连线上的 3 个蓝色手柄，确认折线更新时，箭头能够实时跟随终点，且始终准确贴合目标节点左侧锚点。
  4. 拖拽任意节点，确认连线和箭头联动重绘顺滑无卡顿。
