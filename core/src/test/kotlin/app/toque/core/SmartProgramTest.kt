package app.toque.core

import app.toque.core.machine.Clock
import app.toque.core.machine.ControllerConfig
import app.toque.core.machine.JobState
import app.toque.core.machine.MachineController
import app.toque.core.machine.SafetyPolicy
import app.toque.core.protocol.Mcs
import app.toque.core.smart.SmartProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Programmes propres au Smart, comparés à l'écoute guidée du 21/11/2026 (appli d'origine 2.20). */
class SmartProgramTest {

    /** Première trame de chaque programme, telle que l'appli d'origine l'a envoyée. */
    private val original = listOf(
        Triple(SmartProgram.PUREE, 30 to 0, "08020000000000001000000c000000000000001e000000000000000000"),
        Triple(SmartProgram.SMOOTHIE, 60 to 0, "08020000000000001400000d0000000000000100000000000000000000"),
        Triple(SmartProgram.KETTLE, 703 to 100, "08020e0064000000020100030000000000000b2b000000000000000000"),
        Triple(SmartProgram.SLOW_COOK, 60 to 90, "080201005a0000000201000a0000000000000100000000000000000000"),
        Triple(SmartProgram.SOUS_VIDE, 60 to 60, "080201003c000000020100090000000000010000000000000000000000"),
        Triple(SmartProgram.FERMENT, 60 to 40, "08020100280000000201000b0000000000000100000000000000000000"),
        Triple(SmartProgram.RICE, 60 to 100, "08020e0064000000020100010000000000000100000000000000000000"),
        Triple(SmartProgram.EGGS, 480 to 130, "0802010082000000020000080000000000000800000000000000000000"),
        Triple(SmartProgram.CLEANING, 90 to 37, "080201000000000014000006000000000000011e000000000000000000"),
    )

    @Test fun everyProgramStartsLikeTheOriginalApp() {
        for ((p, setting, hex) in original) {
            val (seconds, celsius) = setting
            val b = SmartBench().ready()
            val v = b.c.startJob(p.job(seconds * 1000L, celsius))
            assertTrue("${p.name} : ${v.message}", v.ok)
            b.step(1_000)
            val first = b.tx.first { it.function == p.function && it.opcode == Mcs.OP_CONFIG && (it.speedLevel > 0 || it.temperatureLevel > 0) }
            assertEquals(p.name, Mcs.hex(Mcs.parseHex(hex)), Mcs.hex(first.payload))
            assertEquals(p.name, "", b.snap.faultCode)
        }
    }

    @Test fun shortProgramsRunToTheEndAndStop() {
        for (p in listOf(SmartProgram.PUREE, SmartProgram.SMOOTHIE, SmartProgram.RICE)) {
            val b = SmartBench().ready()
            assertTrue(b.c.startJob(p.job(60_000, p.defaultC)).ok)
            b.step(65_000)
            assertEquals(p.name, JobState.FINISHED.name, b.snap.jobState)
            assertEquals(p.name, "", b.snap.faultCode)
            assertFalse(b.last().running || b.last().function != 0)
            val n = b.tx.count { it.function == p.function }
            assertTrue("${p.name} : $n", n in 299..304)
        }
    }

    @Test fun slowCookStirsThirtySecondsEveryTwoAndAHalfMinutesThenRests() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(SmartProgram.SLOW_COOK.job(15 * 60_000L, 90)).ok)
        b.step(15 * 60_000L + 5_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        val run = b.tx.filter { it.function == Mcs.FN_SLOW_COOK }
        assertTrue(run.all { it.temperatureLevel == 1 && it.reverse })
        val pattern = ArrayList<Pair<Int, Int>>()
        for (t in run) if (pattern.isEmpty() || pattern.last().first != t.speedLevel) pattern += t.speedLevel to 1 else pattern[pattern.size - 1] = t.speedLevel to pattern.last().second + 1
        assertEquals(listOf(2, 0, 2, 0, 2, 0, 2, 0), pattern.map { it.first })
        assertTrue("$pattern", pattern.filter { it.first == 2 }.all { it.second in 148..152 })
        // Après 10 min, plus de brassage : 5 min au repos chaud.
        assertTrue("$pattern", pattern.last().second in 2_090..2_110)
        // Compte à rebours = temps restant à chaque changement de mouvement.
        assertEquals(900, run.first().seconds)
        assertEquals(870, run.first { it.speedLevel == 0 }.seconds)
    }

    @Test fun sousVideWaitsForTheTemperatureBeforeCounting() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(SmartProgram.SOUS_VIDE.job(10 * 60_000L, 50)).ok)
        b.step(30_000)
        assertEquals("pas de minuteur", 0L, b.c.elapsedMs)
        assertEquals("compte à rebours de préchauffage", 3600, b.last().seconds)
        assertTrue("attente", b.snap.waitingStop)
        // Le modèle monte d'environ 0,3 °C/s : 50 °C en un peu plus de 90 s depuis 22 °C.
        b.step(120_000)
        assertTrue("minuteur ${b.c.elapsedMs} à ${b.board.modelTemperature}", b.c.elapsedMs > 0)
        assertTrue("compte à rebours ${b.last().seconds}", b.last().seconds in 540..600)
        b.step(10 * 60_000L)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        assertTrue("température ${b.board.modelTemperature}", b.board.modelTemperature in 48.0..51.0)
    }

    @Test fun cleaningChangesDirectionOnlyAfterAConfirmedStop() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(SmartProgram.CLEANING.job(90_000, 37)).ok)
        b.step(100_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        val speeds = b.tx.map { if (it.function == Mcs.FN_SHORT_CLEANING) it.speedLevel * (if (it.reverse) -1 else 1) else 0 }
        val blocks = ArrayList<Int>()
        for (s in speeds) if (blocks.isEmpty() || blocks.last() != s) blocks += s
        assertEquals(listOf(0, 20, 0, -8, 0, 20, 0), blocks)
    }

    @Test fun boundsAndRefusals() {
        val b = SmartBench().ready()
        assertFalse(b.c.startJob(SmartProgram.SOUS_VIDE.job(60_000, 95)).ok)
        assertFalse(b.c.startJob(SmartProgram.PUREE.job(600_000)).ok)
        assertTrue(b.c.startJob(SmartProgram.PUREE.job(30_000)).ok)
        b.step(1_000)
        assertFalse(b.c.adjust(speed = 10).ok)
        // Le Connect refuse les programmes du Smart.
        val clock = object : Clock { override fun nowMs() = 0L }
        val connect = MachineController(clock, ControllerConfig())
        assertTrue(connect.startJob(SmartProgram.PUREE.job(30_000)).violations.any { it.code == "smart_only" })
    }

    @Test fun hotContentNeedsConfirmationForFastPrograms() {
        val b = SmartBench()
        b.board.startTemperature = 82.0
        b.board.reset()
        b.ready()
        val v = b.c.startJob(SmartProgram.SMOOTHIE.job(30_000))
        assertFalse(v.ok)
        assertTrue(v.has(SafetyPolicy.HOT_BLEND))
        assertTrue(b.c.startJob(SmartProgram.SMOOTHIE.job(30_000).copy(hotBlendConfirmed = true)).ok)
        // Les programmes lents ne sont pas concernés.
        b.c.stop(); b.step(600); b.c.clearFinished()
        assertTrue(b.c.startJob(SmartProgram.KETTLE.job(120_000, 100)).ok)
    }
}
