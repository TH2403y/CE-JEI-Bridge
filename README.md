# CE-JEI-Bridge

A Paper plugin + Fabric mod pair that makes [CraftEngine](https://github.com/Xiao-MoMi/CraftEngine)'s
custom items, blocks and recipes display correctly in JEI/Jade on the client - reskinned items show
their real texture instead of the generic vanilla material, and recipe views show the exact item in
every slot instead of "any item of this base type".

## 这是什么

CraftEngine 通过 `item_model` 等数据组件在**服务端到客户端的封包转换阶段**才赋予自定义物品真正的外观，而这个转换只发生在 CraftEngine 自己的网络层拦截的物品/容器封包里。当这些数据被 JEI/Jade 这类客户端模组读取时（它们看到的是原始的 `RecipeManager`/`Ingredient` 数据，不经过 CraftEngine 的转换层），自定义物品就会显示成对应的原版材质，配方格子里也只显示"任意一个同类型物品"而不是具体的皮肤。另外，这个 Minecraft 版本的服务端不再向客户端发送原版配方同步封包，JEI 的配方书完全依赖插件自己重新实现这部分同步。

本仓库分两部分协作解决这个问题：

- **`server/`**（`CraftEngineClientBridge`，Paper 插件）：读取 CraftEngine 的物品/方块/合成台/锻造台配方数据，还原出客户端实际会渲染的外观（`item_model`、自定义名称等），通过插件消息通道把这些"精确外观"数据发给客户端；同时手动重建并重发原版配方同步。
- **`client/`**（`CraftEngineClientMod`，Fabric 模组）：接收上述数据，喂给 JEI 的物品列表和自定义 JEI 展示分类（合成台复用 JEI 自带展示能力加精确材质覆盖；锻造台由于 JEI 没有对应的精确展示接口，改为完全自定义的 `IRecipeCategory` 手动摆放四个格子），并给 Jade 提供 CraftEngine 方块和家具的真实图标。该目录构建 26.x 客户端，基线为 Minecraft 26.2。
- **`client-legacy/`**：独立的 Minecraft 1.21.11 Fabric 客户端；它使用独立的 Yarn/JEI/Jade API。

## Jade 26.2 如何显示 CraftEngine 方块和家具

客户端在世界里看到的 CraftEngine 方块仍然是音符盒、绊线等原版视觉状态，Jade 因此默认只能显示原版图标和名称。按下面的链路恢复真实外观：

1. 服务端遍历 CraftEngine 已加载物品，从物品行为中的 `BlockItem.block()` 找到它对应的 CraftEngine 方块。
2. 服务端把这个方块的每个客户端视觉 `BlockState` 映射到对应物品经过 CraftEngine S2C 转换后的精确外观，包括基础材质、`custom_model_data`、`item_model` 和名称组件。
3. 26.2 客户端收到 `block_icons` 后，以视觉状态为键缓存重建出的 `ItemStack`。Jade 查询方块时直接调用自己的物品渲染器显示该物品。
4. Jade 的默认名称、方块朝向和无障碍详情由不同提供器生成。桥接在所有 Jade 组件收集完成后的最终回调中，用同一个 `ItemStack.getHoverName()` 替换 `CORE_OBJECT_NAME`，所以普通模式和按住 Shift 的详细模式都会显示 CraftEngine 名称，也不会残留“音符盒（东）”一类伪装方块信息。

家具方面，物品展示测试发现不需要动，没管它， Jade/CraftEngine 实体本身已经正确的名称，不做额外覆盖。（实际上家具类型还挺多的，我懒得一一兼容了，如果有需要可以提ISSUE）

这些 Jade 图标和标题增强目前只在 Minecraft 26.2 客户端实现，`client-legacy/` 的 1.21.11 客户端不包含这套新通道。

## 依赖要求(这里直接放构建时用的东西了)

- 服务端：Paper/ASPaper，Minecraft 26.2，已安装并加载 [CraftEngine](https://craftengine.net/)。
- 26.x 客户端：Fabric Loader 0.19.3，Fabric API 0.155.0+26.2、JEI 30.16.0.131、Jade 26.2.10，Java 25。
- 1.21.11 客户端：仅支持 Minecraft 1.21.11，使用 Fabric Loader 0.19.3、Fabric API 0.141.6+1.21.11、JEI 27.22.0.66、Jade 19.0.3，Java 21。
- JEI 和/或 Jade 均为可选（装哪个就对哪个生效，都不装也能正常启动）。Gradle 本体使用 JDK 21，26.x 目标通过 Gradle toolchain 使用 Java 25。

仓库在 `server/libs/` 中包含社区版 CraftEngine 26.7.4 JAR，作为可复现构建使用的 `compileOnly` 开发依赖。GitHub Actions 会先校验它的 SHA-256，再用它编译 Paper 插件；该依赖不会被打包进 `CraftEngineClientBridge`，也不会作为独立 Release 资产发布。实际运行服务器仍需自行安装与服务器版本匹配的 CraftEngine。

## 构建

### server/（CraftEngineClientBridge，26.x）

1. 仓库自带的 `server/libs/craft-engine-paper-plugin-26.7.4.jar` 可直接用于开发构建。需要改用其他版本时，通过 `-PcraftEngineJar=<jar 路径>` 指定与目标服务器匹配的 CraftEngine JAR。
2. ```
   cd server
   ./gradlew clean shadowJar -Ptarget=26.x
   ```
   产物在 `server/build/libs/CraftEngineClientBridge-*.jar`，丢进服务器 `plugins/` 目录。

GitHub Actions 使用并校验仓库中的社区版开发依赖；发布资产只包含桥接插件、Fabric 模组和校验文件。

### client/（CraftEngineClientMod，26.x）

```
cd client
./gradlew clean build -Ptarget=26.x
```

### client-legacy/（1.21.11）

```powershell
cd client
./gradlew -p ../client-legacy clean build
```

产物在 `client-legacy/build/libs/ceclientmod-1.21.11-*.jar`，丢进对应的 `.minecraft/mods/` 目录。

### protocol/

```powershell
cd client
./gradlew -p ../protocol protocolTest handshakeTest
```

产物在 `client/build/libs/ceclientmod-26.x-*.jar`，丢进对应的 `.minecraft/mods/` 目录。

## 已知限制

- 锻造台的盔甲纹饰配方（trim）不在精确展示范围内——它的产物是运行时按基础盔甲+染料动态合成的，没有单一固定结果可以展示。
- JEI 的精确展示配方是连接时一次性注册的（`IRecipeManager` 没有对应的移除接口），服务端 CraftEngine 热重载后新增/修改的配方要玩家重新连接才会在 JEI 里刷新。
- 26.x 服务端和客户端以 Minecraft 26.2 构建，另一个客户端仅构建并验证 Minecraft 1.21.11；不提供 1.21.6 或其他 1.21.x 补丁版本兼容承诺。
- 协议里读的部分 NMS 字段在其他版本可能对不上。

## 许可证

本仓库自身代码使用 [MIT 许可证](LICENSE)。
CraftEngine 及仓库中的社区版 CraftEngine 开发依赖都是独立的第三方软件，不受本项目 MIT 许可证覆盖；
仓库中的开发 JAR 仅用于 `compileOnly` 构建，运行服务器仍需自行提供并安装合适版本的 CraftEngine。
