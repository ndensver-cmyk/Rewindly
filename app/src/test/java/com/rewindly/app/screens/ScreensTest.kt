package com.rewindly.app.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.importer.RawPlay
import com.rewindly.app.stories.SceneView
import com.rewindly.app.stories.StoryGenerator
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.home.HomeScreen
import com.rewindly.app.ui.importdata.ImportScreen
import com.rewindly.app.ui.settings.SettingsScreen
import com.rewindly.app.ui.statistics.StatisticsScreen
import com.rewindly.app.ui.history.HistoryScreen
import com.rewindly.app.ui.theme.RewindlyTheme
import com.rewindly.app.ui.theme.mono
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** Renderiza las pantallas a PNG para revisar el diseño. Se ejecuta con -Pscreens=true. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h2000dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var vm: AppViewModel

    @Before fun setup() {
        assumeTrue(System.getProperty("rewindly.screens") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = AppViewModel(app)
        runBlocking {
            val repo = vm.repo
            if (repo.dataRange() == null) {
                val rnd = Random(7)
                val artists = listOf("Bad Bunny", "Karol G", "Feid", "Rosalía", "Tame Impala", "Arctic Monkeys", "J Balvin", "Mon Laferte", "Bomba Estéreo", "Daft Punk")
                val now = System.currentTimeMillis()
                val plays = (0 until 1800).map { i ->
                    val a = artists[(rnd.nextDouble().let { it * it } * artists.size).toInt()]
                    val t = now - rnd.nextLong(0, 60L * 86_400_000)
                    RawPlay("Canción ${rnd.nextInt(1, 14)} de $a", a, "Álbum de $a", null, t, rnd.nextLong(20_000, 240_000), skipped = rnd.nextInt(10) == 0)
                }
                val id = repo.newBatch(Sources.ZIP_EXTENDED, "my_spotify_data.zip")
                repo.insertPlays(plays, Sources.ZIP_EXTENDED, id)
            }
        }
        vm.settings.spotifyRequestedAt = System.currentTimeMillis() - 8L * 86_400_000
    }

    private fun shot(name: String, theme: String = "dark", content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { RewindlyTheme(theme) { Box(Modifier.fillMaxSize().background(mono.bg)) { content() } } }
        rule.waitForIdle()
        Thread.sleep(1500)
        rule.mainClock.advanceTimeBy(3000)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun home() = shot("01_home") { HomeScreen(vm, {}, {}) }
    @Test fun homeLight() = shot("01_home_light", "light") { HomeScreen(vm, {}, {}) }
    @Test fun import() = shot("02_import") { ImportScreen(vm) }
    @Test fun stats() { vm.refreshStats(); Thread.sleep(2500); shot("03_stats") { StatisticsScreen(vm) } }
    @Test fun history() = shot("04_history") { HistoryScreen(vm) }
    @Test fun settings() = shot("05_settings") { SettingsScreen(vm) }

    private fun story(i: Int) {
        val stats = runBlocking { val r = vm.repo.dataRange()!!; vm.repo.computeStats(r.second - 30L * 86_400_000, r.second + 1) }
        val scenes = StoryGenerator.generate(stats)
        val idx = i.coerceAtMost(scenes.lastIndex)
        rule.mainClock.autoAdvance = false
        rule.setContent { RewindlyTheme { SceneView(scenes[idx], visible = true) } }
        rule.mainClock.advanceTimeBy(4000)
        rule.onRoot().captureRoboImage("build/screens/06_story_$i.png")
    }
    @Test fun story0() = story(0)
    @Test fun story1() = story(1)
    @Test fun story2() = story(2)
    @Test fun story3() = story(3)
    @Test fun story5() = story(5)
}
