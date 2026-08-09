# CE-JEI-Bridge

A Paper plugin + Fabric mod pair that makes [CraftEngine](https://github.com/Xiao-MoMi/CraftEngine)'s
custom items, blocks and recipes display correctly in JEI/Jade on the client - reskinned items show
their real texture instead of the generic vanilla material, and recipe views show the exact item in
every slot instead of "any item of this base type".

## 这是什么

CraftEngine 通过 `item_model` 等数据组件在**服务端到客户端的封包转换阶段**才赋予自定义物品真正的外观，而这个转换只发生在 CraftEngine 自己的网络层拦截的物品/容器封包里。当这些数据被 JEI/Jade 这类客户端模组读取时（它们看到的是原始的 `RecipeManager`/`Ingredient` 数据，不经过 CraftEngine 的转换层），自定义物品就会显示成对应的原版材质，配方格子里也只显示"任意一个同类型物品"而不是具体的皮肤。另外，这个 Minecraft 版本的服务端不再向客户端发送原版配方同步封包，JEI 的配方书完全依赖插件自己重新实现这部分同步。

本仓库分两部分协作解决这个问题：

- **`server/`**（`CraftEngineClientBridge`，Paper 插件）：读取 CraftEngine 的物品/方块/合成台/锻造台配方数据，还原出客户端实际会渲染的外观（`item_model`、自定义名称等），通过插件消息通道把这些"精确外观"数据发给客户端；同时手动重建并重发原版配方同步（因为这个 MC 版本不会自动发送）。
- **`client/`**（`CraftEngineClientMod`，Fabric 模组）：接收上述数据，喂给 JEI 的物品列表和自定义 JEI 展示分类（合成台复用 JEI 自带展示能力加精确材质覆盖；锻造台由于 JEI 没有对应的精确展示接口，改为完全自定义的 `IRecipeCategory` 手动摆放四个格子），并给 Jade 提供方块/物品的 CraftEngine 身份识别。该目录构建 26.x 客户端，基线为 Minecraft 26.2。
- **`client-legacy/`**：独立的 1.21.x Fabric 客户端，基线为 Minecraft 1.21.6；它使用独立的 Yarn/JEI/Jade API，不与 26.x 客户端共用版本相关实现。

两边通过一组插件消息通道（`ceclientbridge:items`/`blocks`/`brewing`/`crafting_display`/`smithing_display`/`hello`）通信，必须配套使用。

## 依赖要求

- 服务端：Paper/ASPaper，Minecraft 26.2（26.x 服务端构建基线），已安装并加载 [CraftEngine](https://craftengine.net/)；开发构建使用 CraftEngine 26.7.4 社区版依赖。
- 26.x 客户端：Fabric Loader 0.19.3、Minecraft 26.2、Fabric API 0.155.0+26.2、JEI 30.16.0.131、Jade 26.2.10，Java 25。
- 1.21.x 客户端：以 Minecraft 1.21.6 为构建基线，声明兼容 `>=1.21.6 <1.22`，使用 Fabric Loader 0.17.2、Fabric API 0.127.1+1.21.6、JEI 19.39.0.368、Jade 19.0.3，Java 21。
- JEI 和/或 Jade 均为可选（装哪个就对哪个生效，都不装也能正常启动）。Gradle 本体使用 JDK 21，26.x 目标通过 Gradle toolchain 使用 Java 25。

仓库的 [CraftEngine 26.7.4 社区版开发依赖](https://github.com/TH2403y/CE-JEI-Bridge/releases/tag/ce-dependencies-26.7.4)
由 GitHub Actions 下载并作为 Paper 插件的 `compileOnly` 依赖使用，不会被打包进桥接插件。

## 构建

### server/（CraftEngineClientBridge，26.x）

1. 把你自己拥有授权的 CraftEngine 插件 jar 放进 `server/libs/`（见 `server/libs/README.md`），文件名需要和 `server/build.gradle.kts` 里 `compileOnly(files("libs/..."))` 引用的一致，不一致就改这一行。
2. ```
   cd server
   ./gradlew clean shadowJar -Ptarget=26.x
   ```
   产物在 `server/build/libs/CraftEngineClientBridge-*.jar`，丢进服务器 `plugins/` 目录。

GitHub Actions 会使用仓库 Release 中的 CraftEngine 26.7.4 社区版依赖自动构建该插件；本地构建仍需准备对应的 `server/libs/` JAR。

### client/（CraftEngineClientMod，26.x）

```
cd client
./gradlew clean build -Ptarget=26.x
```

### client-legacy/（1.21.x）

```powershell
cd client
./gradlew -p ../client-legacy clean build
```

产物在 `client-legacy/build/libs/ceclientmod-1.21.x-*.jar`，丢进对应的 `.minecraft/mods/` 目录。

### protocol/

```powershell
cd client
./gradlew -p ../protocol protocolTest handshakeTest
```

产物在 `client/build/libs/ceclientmod-26.x-*.jar`，丢进对应的 `.minecraft/mods/` 目录。

## 已知限制

- 锻造台的盔甲纹饰配方（trim）不在精确展示范围内——它的产物是运行时按基础盔甲+染料动态合成的，没有单一固定结果可以展示。
- JEI 的精确展示配方是连接时一次性注册的（`IRecipeManager` 没有对应的移除接口），服务端 CraftEngine 热重载后新增/修改的配方要玩家重新连接才会在 JEI 里刷新。
- 26.x 服务端和客户端以 Minecraft 26.2 构建，1.21.x 客户端以 1.21.6 构建；声明的版本范围不是每个补丁版本都经过实机验证的承诺。
- 协议里读的部分 NMS 字段在其他版本可能对不上。

## 许可证

本仓库自身代码使用 [MIT 许可证](LICENSE)。CraftEngine 是独立的第三方商业插件，不包含在本仓库内、也不受本许可证覆盖，需要自行购买并提供。
