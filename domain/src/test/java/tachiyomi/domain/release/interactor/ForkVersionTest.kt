package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ForkVersionTest {

    @Test
    fun `parses fork and upstream versions`() {
        ForkVersion.parse("v0.21.2-t5") shouldBe ForkVersion(listOf(0, 21, 2), 5)
        ForkVersion.parse("0.21.2-t12") shouldBe ForkVersion(listOf(0, 21, 2), 12)
        ForkVersion.parse("v0.21.2") shouldBe ForkVersion(listOf(0, 21, 2), 0)
        ForkVersion.parse("nightly") shouldBe null
    }

    @Test
    fun `newer build number of the same upstream version is an update`() {
        ForkVersion.isNewer(versionName = "0.21.2-t5", versionTag = "v0.21.2-t6") shouldBe true
        ForkVersion.isNewer(versionName = "0.21.2-t9", versionTag = "v0.21.2-t10") shouldBe true
    }

    @Test
    fun `newer upstream version is an update regardless of build number`() {
        ForkVersion.isNewer(versionName = "0.21.1-t12", versionTag = "v0.21.2-t1") shouldBe true
        ForkVersion.isNewer(versionName = "0.21.9-t3", versionTag = "v0.22.0-t1") shouldBe true
        ForkVersion.isNewer(versionName = "0.21.2", versionTag = "v0.21.2-t1") shouldBe true
    }

    @Test
    fun `same or older version is not an update`() {
        ForkVersion.isNewer(versionName = "0.21.2-t5", versionTag = "v0.21.2-t5") shouldBe false
        ForkVersion.isNewer(versionName = "0.21.2-t5", versionTag = "v0.21.2-t4") shouldBe false
        ForkVersion.isNewer(versionName = "0.21.2-t1", versionTag = "v0.21.1-t30") shouldBe false
        ForkVersion.isNewer(versionName = "0.21.2-t1", versionTag = "v0.21.2") shouldBe false
    }
}
