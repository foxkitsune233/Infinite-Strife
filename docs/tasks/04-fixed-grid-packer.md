# 外围任务04：固定格子自动摆放

## 目标与范围

纯Kotlin、确定性的矩形摆放工具，用于6×6宝箱生成和背包整理。支持90°旋转、已有占格障碍；不操作库存或存档，不依赖Android/Java/随机数/时钟。

仅新增主体：`shared/src/commonMain/kotlin/com/example/douqiuqiu/grid/FixedGridPacker.kt`。

仅新增测试：`app/src/test/java/com/example/douqiuqiu/grid/FixedGridPackerTest.kt`。现有app依赖shared及JUnit4.13.2，可直接测试共享类；不修改Gradle或其他文件。

## 接口

```kotlin
data class GridSize(val width: Int, val height: Int)
data class GridRect(val x: Int, val y: Int, val width: Int, val height: Int)
data class PackItem(val id: String, val width: Int, val height: Int, val canRotate: Boolean = true)
data class PackedItem(val id: String, val x: Int, val y: Int, val rotated: Boolean)
data class GridPackResult(val success: Boolean, val placements: List<PackedItem>, val failedItemId: String?)
object FixedGridPacker {
    fun pack(size: GridSize, items: List<PackItem>, occupied: List<GridRect> = emptyList()): GridPackResult
}
```

## 精确规则

- 按输入顺序摆放，不自行按品质或面积排序。逐行扫描候选左上角：y先从0增长，每行x从0增长；同一坐标先试原方向，再试旋转方向。正方形只试原方向，返回rotated=false。
- 先保留所有occupied，再摆物品。不能重叠或越界，边缘相接合法。尺寸和坐标均为格数，与像素无关。
- 全部成功时按输入顺序返回位置，failedItemId=null；任一物品摆不下时success=false、placements为空、failedItemId为第一个失败ID。不要返回半成品，不修改任何输入。
- 本任务采用确定性first-fit，不要求回溯最优解；失败仅表示该算法未找到摆法，不能声称一定不存在可行排列。
- 网格宽高1..64，物品数0..256，物品宽高1..64；ID非空白且唯一。已有障碍必须尺寸正、在网格内且互不重叠；障碍可为空。非法输入require抛IllegalArgumentException。
- 物品大于网格但尺寸仍在合法范围时是正常摆放失败，不是非法参数。用减法判边界，避免x+width等整数溢出。
- 返回列表是独立快照；同样输入每次结果相同。结果坐标始终表示旋转后的左上角，宽高由rotated推导。

## 必须测试

空清单成功；4×2放满8个1×1；第9个失败且无部分结果；3×2可旋转为2×3放入2×3；禁旋转失败；正方形不标旋转；障碍后寻找下一位置；相接不算重叠；输入顺序稳定；失败后输入不变；同坐标原向优先；重复/空白ID、非法尺寸、负坐标、越界/重叠障碍抛错；大于网格物品正常失败；4×2背包与6×6宝箱实际例子。

## 接入与交付

主任务暂时保留已有简单摆放实现，交付后由主任务替换调用，开发者不要修改JourneyRules或InventoryRules。运行`:app:testDebugUnitTest --tests '*FixedGridPackerTest'`。若运行shared检查，只用`:shared:compileKotlinJvm :shared:compileKotlinJs`，不启动浏览器或下载Node环境。
