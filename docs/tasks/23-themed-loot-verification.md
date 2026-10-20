# 外围任务23：主题宝箱与材料保底验收（测试任务）

只新增 `app/src/test/java/com/example/douqiuqiu/inventory/ThemedLootIntegrationTest.kt` 和 `docs/testing/results/23-themed-loot.md`。不得改生产代码、现有20～22测试、配置、Gradle或工作日志。测试由外部执行，主任务只做编译和静态复核。

先读shared/journey/ChestFactory、MaterialProgression、JourneyRules与外围07 MaterialPityTracker。使用真实方法返回的快照；不得通过与生产相同算法计算预期再自比较。夹具含普通1×1物品和5种材料（essence1×1、backpack_fabric1×2、skill_shard1×1、safe_lock3×2、safe_frame3×3），材料均不堆叠，关键材料boundOnAcquire=true。

## 生成与刷新

| ID | 条件与操作 | 预期 |
| --- | --- | --- |
| L01 | clears=0/2/3/9/10分别ensureBatch空列表 | 箱数4/4/6/6/8；至少2品质、含60金币低成本入口；一局仍5战 |
| L02 | clears>=3生成6箱 | 六主题均在，ID唯一；物品摆放不重叠且都在6×6内 |
| L03 | 同seed同快照各生成一次、再对已生成状态ensureBatch | 两次输出一致；已有列表直接返回、不重抽、不再次消费保底 |
| L04 | 鉴定与单箱刷新 | 鉴定仅改揭示/票价；刷新仅换指定箱，保主题/难度，清鉴定；其他箱数据完全保持 |
| L05 | 无适用低价箱领取救济 | 只最后一箱替为白色综合补给、低成本60；免费刷新/商店不重置，不创建未展示的其他箱 |
| L06 | 单箱刷新BACKPACK且待保底；其他未刷新SAFE也有待保底 | 只消耗backpack资格，safe资格保持；不能生成整批后只显示一箱 |
| L07 | batch接近Int.MAX_VALUE | 不返回负序号或重复ID；失败不修改输入、未提交中间状态 |

## 资格与生成消耗

| ID | 条件与操作 | 预期 |
| --- | --- | --- |
| L08 | 首次撤离前、技能栏未到6、未首通安全箱 | 对应类别不eligible，不因普通通关累计miss |
| L09 | 已撤离、背包4×2、仓库织件少于2 | backpack eligible；已有2份则当前无缺额，不消费待保底 |
| L10 | 背包已扩4×3但clears=0 | 下一档需要1通关，暂不eligible；不能只看未满容量就累计 |
| L11 | skillSlots=6/clears=3、仓库刻片少于2 | skills eligible；slots=7须6通关且刻片少于4；slots=8不eligible |
| L12 | 安全箱2×2、clears=1、缺锁芯 | safe eligible；3×2须clears>=5、背包至少4×3、缺框架；3×3不eligible |
| L13 | pending backpack=(4,true)，生成合格BACKPACK箱 | 该箱含织件且guaranteedCategories仅backpack，counter立即变(0,false)；不是通关才消耗 |
| L14 | pending skills=(4,true)，生成合格SKILLS箱；pending safe=(6,true)合格SAFE箱 | 分别一份刻片/当前需要的锁芯或框架；Safe最低难度1，低成本SAFE不消费safe资格 |
| L15 | 待保底但成长未开放、已满或仓库已足额 | 资格保持，不给不需要材料、不被不合格箱消费 |
| L16 | 含待保底的多箱生成或连续刷新同主题 | 一份资格最多一箱强制材料；后续普通随机仍可能出同材料，不能把自然掉落误判为重复保底 |

## 终局计数

| ID | 条件与操作 | 预期 |
| --- | --- | --- |
| L17 | 对应主题、合格难度、已开放且缺材料，通关未带出对应材料 | 该类别miss+1；非对应主题/不合格难度不加；backpack/skills到4、safe到6变pending |
| L18 | 死亡或第三战普通撤离未带出材料 | 不加miss，原计数保留 |
| L19 | 途中获得目标材料放SAFE后死亡 | 新材料实际带回则重置该类别；背包中的新材料死亡丢失则不重置 |
| L20 | 新材料从目标箱通关入库或其他主题奖励撤离带回 | 对应类别均重置，不要求一定来自该材料主题 |
| L21 | 老仓库/带入材料仍持有，当前合格主题通关未获新材料 | 不能靠旧材料重置；在仓库尚未足额且仍有缺额时计数正常加1 |
| L22 | 同一类别同时“通关未获得资格”与“成功带出” | 成功优先，结果0/false |
| L23 | 生成保底箱后刷新、放弃、死亡、普通撤离 | 消费过的资格不返还；之后合格未获得的通关可开始下一轮计数 |
| L24 | 重复奖励/撤离/通关结算操作 | run清除后第二次拒绝；不能重复发材料或再次记录miss |
| L25 | 唯一缺额是safe：clears=6、背包6×4、技能栏8、安全箱3×2、仓库缺框架 | 至少一个SAFE箱难度>=1、票价>=150，能累计和兑现safe保底；仍有60金币综合低价入口。不能让SAFE永远落在第2箱难度0 |

说明：本局新获得物品ID由真实grant/complete以 `${run.id}:reward:*`、`${run.id}:event:*`、`${run.id}:chest:*` 生成。模拟终局夹具时用该前缀，仓库旧物用别的ID；不要把随机UUID伪装成真实本局发放路径后得出错误断言。常规材料概率不以少量抽样检验统计分布；重点验证候选偏向和强制资格机制。

命令：`./gradlew.bat --offline :app:testDebugUnitTest --tests '*ThemedLootIntegrationTest'`。报告每ID通过/失败/未执行、实际tests/failures/errors及最小失败输入，生产问题交主任务修复。不要改外围07来迎合错误调用。
