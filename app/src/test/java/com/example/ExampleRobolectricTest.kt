package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.IcsRepository
import com.example.model.CourseEvent
import com.example.notification.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Flop!EDT", appName)
    }

    @Test
    fun testCourseEventOngoingLogic() {
        val now = LocalDateTime.of(2026, 9, 14, 10, 30)
        val course = CourseEvent(
            title = "Mathématiques",
            room = "A201",
            teacher = "M. Euler",
            startTime = LocalDateTime.of(2026, 9, 14, 10, 0),
            endTime = LocalDateTime.of(2026, 9, 14, 12, 0)
        )

        assertTrue(course.isOngoing(now))
        assertTrue(course.isCurrentlyOngoing(now))
        assertFalse(course.isPast(now))
        assertFalse(course.isUpcoming(now))
        assertEquals("10h00 - 12h00", course.formattedTimeRange)
        assertEquals("2h", course.durationFormatted)
        assertEquals(0.25f, course.progress(now), 0.01f)
    }

    @Test
    fun testIcsParsingAndNotificationBuild() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannel(context)

        val repository = IcsRepository(context)
        val sampleIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Flop!EDT//FR
            BEGIN:VEVENT
            UID:event-1@flop.org
            SUMMARY:Algorithmique
            LOCATION:B104
            DESCRIPTION:Enseignant: Mme. Lovelace\nGroupe: INFO1
            DTSTART:20260914T083000Z
            DTEND:20260914T103000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:event-2@flop.org
            SUMMARY:Web Dev
            LOCATION:C201
            DESCRIPTION:M. Berners-Lee
            DTSTART:20260914T104500Z
            DTEND:20260914T124500Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = repository.parseIcsStream(sampleIcs.byteInputStream())
        assertEquals(2, parsed.size)
        assertEquals("Algorithmique", parsed[0].title)
        assertEquals("B104", parsed[0].room)
        assertEquals("Mme. Lovelace", parsed[0].teacher)

        // Test building notification when 1st course is ongoing
        val notif = NotificationHelper.buildOngoingNotification(
            context,
            parsed,
            parsed[0].startTime.plusMinutes(15)
        )
        assertNotNull(notif)
    }

    @Test
    fun testCacheAgeAndStaleCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = IcsRepository(context)

        // S'il n'y a pas encore de fichier, isCacheOlderThan retourne true
        val cacheFile = java.io.File(context.cacheDir, IcsRepository.CACHE_FILE_NAME)
        cacheFile.delete()
        assertTrue(repository.isCacheOlderThan(24))

        // Écriture d'un cache récent (maintenant)
        cacheFile.writeText("[]")
        assertFalse(repository.isCacheOlderThan(24))

        // Modification artificielle de la date du fichier il y a 25 heures
        val twentyFiveHoursAgo = System.currentTimeMillis() - (25L * 3600L * 1000L)
        cacheFile.setLastModified(twentyFiveHoursAgo)
        assertTrue(repository.isCacheOlderThan(24))
    }
}
