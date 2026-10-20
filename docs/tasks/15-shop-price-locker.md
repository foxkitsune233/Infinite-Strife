# 外围任务15：商店价格锁定计算

只新增 `shared/src/commonMain/kotlin/com/example/douqiuqiu/shop/ShopPriceLocker.kt` 与 `app/src/test/java/com/example/douqiuqiu/shop/ShopPriceLockerTest.kt`。不改SupplyRules、HomeEconomyRules、配置、UI或存档，不调用14。

```kotlin
data class ShopPriceInput(val definitionId: String, val basePrice: Int, val stageFactor: Double = 1.0, val demandFactor: Double = 1.0)
data class LockedShopPrice(val definitionId: String, val buyPrice: Int, val baseResalePrice: Int)
object ShopPriceLocker {
    fun lock(inputs: List<ShopPriceInput>): List<LockedShopPrice>
}
```

一次货架生成时调用，返回后由上层保存整批价格，购买不重算。此工具不读钱包、次数、近期交易，不自行设计需求模型。

规则：总系数是stageFactor*demandFactor，限0.8..1.2；两个输入各自须为finite且>0。基准价为非负Int。buyPrice=基准价*限幅系数，四舍五入至整数（正数.5向上）；基准价0仍为0，正价至少1。结果超过Int.MAX_VALUE时require抛IllegalArgumentException，不饱和到上限。baseResalePrice=floor(basePrice*40/100)，使用Long中间值，不受本轮买价波动影响。回收星级/堆叠倍率由上层处理，此处仅返回一份一星基础物品回收价。

输出按ID字典序，无重复ID、空白ID；输入独立。两个合法有限因子乘积溢出时不直接得到Infinity后截断，先安全判定其大于上限则取1.2，小于下限则取0.8（例如两个Double.MAX_VALUE应得到上限，而非意外报错）。可以用分支除法比较，再在中间区间做乘法。

至少覆盖：默认价；上下限；.5取整；0价与1价；锁价不随后续输入集合修改变化；原价回收不随调价变；Int大值的Long回收；合法因子极大/极小；结果价格溢出拒绝；NaN/Infinity/非正因子/负价/重复ID拒绝。验收 `:app:testDebugUnitTest --tests '*ShopPriceLockerTest' :shared:compileKotlinJs`。

依据：阶段与需求伪价格调控、0.8～1.2限制、刷新锁价、基础价格40%回收。不是实际市场或动态交易系统。
