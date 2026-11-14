package co.qavi.core.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityListTest {
    private val qavi = PermissionCommands.accessibilityService("co.qavi")
    private val qaviLong = "co.qavi/co.qavi.a11y.QaviAccessibilityService"
    private val other = "com.example.reader/.ReaderService"
    private val other2 = "org.sample.keys/org.sample.keys.KeyService"

    @Test
    fun emptyOrUnsetValues() {
        listOf(null, "", "   ", "null", "null\n", ":", " : ").forEach { raw ->
            assertEquals("raw=$raw", emptyList<String>(), AccessibilityList.entries(raw))
            assertFalse(AccessibilityList.contains(raw, qavi))
            assertEquals(qavi, AccessibilityList.with(raw, qavi))
            assertEquals("", AccessibilityList.without(raw, qavi))
        }
    }

    @Test
    fun appendKeepsOtherServicesInOrder() {
        assertEquals("$other:$qavi", AccessibilityList.with(other, qavi))
        assertEquals("$other:$other2:$qavi", AccessibilityList.with("$other:$other2\n", qavi))
    }

    @Test
    fun alreadyPresentIsNotDuplicated() {
        assertEquals("$other:$qavi", AccessibilityList.with("$other:$qavi", qavi))
        // 系统里存的是完整类名写法,也算已经有了
        assertEquals("$qaviLong:$other", AccessibilityList.with("$qaviLong:$other", qavi))
        assertTrue(AccessibilityList.contains(qaviLong, qavi))
    }

    @Test
    fun removeKeepsOthersAndHandlesBothSpellings() {
        assertEquals("$other:$other2", AccessibilityList.without("$other:$qavi:$other2", qavi))
        assertEquals(other, AccessibilityList.without("$qaviLong:$other", qavi))
        assertEquals(other, AccessibilityList.without(other, qavi))
        assertEquals("", AccessibilityList.without(qavi, qavi))
    }

    @Test
    fun strayColonsAndSpacesAreCleanedUp() {
        assertEquals("$other:$other2:$qavi", AccessibilityList.with(":$other:: $other2 :", qavi))
        assertEquals("$other:$other2", AccessibilityList.without("$other::$qavi:$other2:", qavi))
    }

    @Test
    fun differentServiceOfSamePackageIsNotConfused() {
        val sibling = "co.qavi/.a11y.OtherService"
        assertFalse(AccessibilityList.contains(sibling, qavi))
        assertEquals(sibling, AccessibilityList.without("$sibling:$qavi", qavi))
    }
}
