package com.example.douqiuqiu.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * 64 号外部验证表「自动化建议」第 3、4 条：
 * 包版本 `0.1.4-AAAA-BBBB-CCCC` 与 `0.1.4-DDDD-EEEE-FFFF` 归一化为同一已读键；
 * `0.1.10` 排在 `0.1.9` 后，避免字符串字典序导致未来版本过滤错误。
 *
 * 设备级项目（VL01–VL10，依赖 SharedPreferences 与真实弹窗）由外部执行。
 */
class VersionLogTest {
    @Test fun semanticVersionIgnoresBuildIndex() {
        assertEquals("0.1.4", VersionLog.semanticVersion("0.1.4-AAAA-BBBB-CCCC"))
        assertEquals("0.1.4", VersionLog.semanticVersion("0.1.4-DDDD-EEEE-FFFF"))
        assertEquals("仅构建索引不同的两个包应归一化为同一已读键",
            VersionLog.semanticVersion("0.1.4-AAAA-BBBB-CCCC"),
            VersionLog.semanticVersion("0.1.4-DDDD-EEEE-FFFF"))
        assertEquals("0.1.5", VersionLog.semanticVersion("0.1.5"))
        assertEquals("空版本名应回退为 0.0.0", "0.0.0", VersionLog.semanticVersion(null))
    }

    @Test fun notAfterComparesNumericallySoTenFollowsNine() {
        assertTrue("0.1.10 应排在 0.1.9 后", VersionLog.notAfter("0.1.9", "0.1.10"))
        assertFalse("0.1.9 不应排在 0.1.10 后", VersionLog.notAfter("0.1.10", "0.1.9"))
        assertTrue("同版本应视为不晚于", VersionLog.notAfter("0.1.10", "0.1.10"))
        assertTrue("0.1.3 应不晚于 0.1.3", VersionLog.notAfter("0.1.3", "0.1.3"))
        assertTrue("0.1.3 应不晚于 0.1.4", VersionLog.notAfter("0.1.3", "0.1.4"))
        assertFalse("0.1.4 不应不晚于 0.1.3", VersionLog.notAfter("0.1.4", "0.1.3"))
        assertTrue("数值比较下 0.2 应不晚于 0.10", VersionLog.notAfter("0.2", "0.10"))
    }

    @Test fun visibleHistoryExcludesFutureVersions() {
        // VL02 的自动化部分：0.1.4 包显示 0.1.3 与 0.1.4，不显示 0.1.5。
        assertTrue(VersionLog.notAfter("0.1.3", "0.1.4"))
        assertTrue(VersionLog.notAfter("0.1.4", "0.1.4"))
        assertFalse("未来版本不应出现在日志中", VersionLog.notAfter("0.1.5", "0.1.4"))
        // VL06：0.1.5 包应显示 0.1.5 且高亮为当前版本。
        assertTrue(VersionLog.notAfter("0.1.5", "0.1.5"))
        assertFalse("0.1.6 及以后不应显示", VersionLog.notAfter("0.1.6", "0.1.5"))
    }
}
