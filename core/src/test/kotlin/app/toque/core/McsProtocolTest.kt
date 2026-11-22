package app.toque.core

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.protocol.Mcs
import app.toque.core.protocol.McsCommand
import app.toque.core.protocol.McsLayout
import app.toque.core.protocol.McsProtocolException
import app.toque.core.protocol.McsSequence
import app.toque.core.protocol.McsStreamParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Protocole du Monsieur Cuisine Smart, contre les jeux de référence du Monsieur Cuisine
 * Developer SDK (MIT) : 900 enveloppes, 900 commandes, 1000 trames d'état décodées (appli 1.2.14.15),
 * et 403 commandes produites par le constructeur de trames de l'appli 2.20.2.2 du robot de référence.
 */
class McsProtocolTest {
    private fun rows(name: String): List<Map<String, Any?>> {
        val text = javaClass.getResourceAsStream("/mcs/$name")!!.bufferedReader().use { it.readText() }
        return Json.parse(text).list().map { it.obj() }
    }

    private fun hexOf(raw: ByteArray) = Mcs.hex(raw).replace(" ", "").lowercase()

    private fun assertRefused(block: () -> Unit) {
        try {
            block()
            fail("valeur acceptée à tort")
        } catch (e: McsProtocolException) {
        }
    }

    @Test fun sequenceSkips0x66AndWraps() {
        assertEquals(0x67, McsSequence(0x65).next())
        assertEquals(0, McsSequence(255).next())
        val s = McsSequence()
        val seen = (1..512).map { s.next() }
        assertFalse(0x66 in seen)
        assertEquals(1, seen[0])
    }

    @Test fun envelopes900() {
        val r = rows("envelope-reference-900.json")
        assertEquals(900, r.size)
        for ((i, row) in r.withIndex()) {
            val raw = Mcs.pack(Mcs.parseHex(row.str("payload_hex")!!), row.int("sequence")!!)
            assertEquals("cas $i", row.str("frame_hex"), hexOf(raw))
        }
    }

    @Test fun commands600() {
        val r = rows("conformance-900.json")
        assertEquals(900, r.size)
        var manual = 0
        var operation = 0
        var otaSkipped = 0
        for ((i, row) in r.withIndex()) {
            val a = row["arguments"].obj()
            val seq = a.int("sequence") ?: 1
            val raw = when (row.str("kind")) {
                "manual" -> {
                    manual++
                    Mcs.encodeManual(
                        a.int("speed") ?: 0, a.int("temperature") ?: 0, a.int("seconds") ?: 0,
                        a.bool("reverse") ?: false, seq, McsLayout.APP_1_2_14,
                    )
                }
                "operation" -> {
                    operation++
                    Mcs.encode(
                        McsCommand(
                            opcode = a.int("opcode") ?: Mcs.OP_CONFIG,
                            speedLevel = a.int("speed_level") ?: 0,
                            reverse = a.bool("reverse") ?: false,
                            temperatureLevel = a.int("temperature_level") ?: 0,
                            constantTemperature = a.int("constant_temperature") ?: 0,
                            function = a.int("function") ?: 0,
                            seconds = a.int("seconds") ?: 0,
                            scaleMode = a.int("scale_mode") ?: 0,
                            turboLock = a.int("turbo_lock") ?: 0,
                            button = a.int("button") ?: 0,
                            layout = if (a.bool("legacy") == true) McsLayout.LEGACY else McsLayout.APP_1_2_14,
                        ),
                        seq,
                    )
                }
                else -> { otaSkipped++; continue } // Toque n'émet jamais de trame de mise à jour.
            }
            assertEquals("cas $i", row.str("hex"), hexOf(raw))
            val f = Mcs.parse(raw)
            assertEquals(seq, f.sequence)
        }
        assertEquals(300, manual)
        assertEquals(300, operation)
        assertEquals(300, otaSkipped)
    }

    @Test fun app220Commands403() {
        val r = rows("app-2.20.2.2-commands.json")
        assertEquals(403, r.size)
        for ((i, row) in r.withIndex()) {
            val a = row["args"].obj()
            val seq = row.int("seq")!!
            val raw = when (row.str("kind")) {
                "idle" -> Mcs.encodeStop(seq)
                "query" -> Mcs.encodeQuery(seq)
                "tare" -> Mcs.encodeTare(seq)
                else -> Mcs.encode(
                    McsCommand(
                        speedLevel = a.int("speed_level")!!,
                        reverse = a.int("reverse") == 1,
                        temperatureLevel = a.int("temperature_level")!!,
                        constantTemperature = a.int("constant_temperature") ?: 0,
                        function = a.int("function")!!,
                        seconds = (a["millis"] as Number).toLong().div(1000).toInt(),
                    ),
                    seq,
                )
            }
            assertEquals("cas $i", row.str("hex"), hexOf(raw))
            assertEquals(38, raw.size)
        }
    }

    /** Trames réelles du robot de référence : nos encodages reproduisent ses commandes, ses réponses se décodent. */
    @Test fun robotCapture() {
        val text = javaClass.getResourceAsStream("/mcs/robot-capture-2026-11-21.json")!!.bufferedReader().use { it.readText() }
        val d = Json.parse(text).obj()
        val tx = d["tx"].list().map { Mcs.parseHex(it as String) }
        val rx = d["rx"].list().map { Mcs.parseHex(it as String) }
        assertEquals(260, tx.size)
        assertEquals(1778, rx.size)
        fun u(b: Byte) = b.toInt() and 0xFF
        for ((i, raw) in tx.withIndex()) {
            val f = Mcs.parse(raw)
            val p = f.payload
            val again = Mcs.encode(
                McsCommand(
                    opcode = f.opcode, temperatureLevel = u(p[2]), constantTemperature = u(p[4]),
                    speedLevel = u(p[8]), reverse = u(p[9]) == 1, turboLock = u(p[10]), function = u(p[11]),
                    scaleMode = u(p[13]), seconds = u(p[17]) * 3600 + u(p[18]) * 60 + u(p[19]), button = u(p[24]),
                ),
                f.sequence,
            )
            assertArrayEquals("commande $i", raw, again)
        }
        var weighed = false
        var lid = false
        var bowlAbsent = false
        var overload = false
        for ((i, raw) in rx.withIndex()) {
            assertEquals(53, raw.size)
            val t = Mcs.decodeTelemetry(raw)
            assertEquals("réponse $i", 3090, t.firmwareVersion)
            assertTrue("réponse $i : ${t.warnings}", t.warnings.isEmpty())
            assertEquals(5, t.extraBytes.size)
            assertEquals(0, t.weightDecimals)
            if (t.weightGrams == 990.0) weighed = true
            if (t.lidOpen) lid = true
            if (t.cupNtcAbsent) bowlAbsent = true
            if (t.scaleOverload) overload = true
        }
        assertTrue(weighed && lid && bowlAbsent && overload)
        // Le flux brut, coupé n'importe où, redonne les mêmes trames.
        val stream = rx.take(50).fold(ByteArray(0)) { a, b -> a + b }
        val parser = McsStreamParser()
        val out = ArrayList<ByteArray>()
        var pos = 0
        while (pos < stream.size) {
            val n = minOf(7, stream.size - pos)
            parser.feed(stream.copyOfRange(pos, pos + n), nowMillis = 0, out = out)
            pos += n
        }
        assertEquals(50, out.size)
        for (k in 0 until 50) assertArrayEquals(rx[k], out[k])
    }

    /** Chauffe réelle à 70 °C : la température remonte par paliers de la table, et 0 sous 37 °C. */
    @Test fun robotCaptureHeating() {
        val text = javaClass.getResourceAsStream("/mcs/robot-capture-2026-11-21-chauffe.json")!!.bufferedReader().use { it.readText() }
        val rx = Json.parse(text).obj()["rx"].list().map { Mcs.parseHex(it as String) }
        val seen = sortedSetOf<Int>()
        for (raw in rx) {
            val t = Mcs.decodeTelemetry(raw)
            assertTrue(t.warnings.isEmpty())
            assertEquals(0, t.returnedTemperature)
            assertTrue("${t.actualTemperature} hors table", t.actualTemperature == 0 || Mcs.levelForCelsius(t.actualTemperature) > 0)
            seen += t.actualTemperature
        }
        assertEquals(sortedSetOf(0, 37, 40, 45, 50, 55), seen)
    }

    @Test fun telemetry1000() {
        val r = rows("android-rx-vectors.json")
        assertEquals(1000, r.size)
        for ((i, row) in r.withIndex()) {
            val d = row["fields"].obj()
            val t = Mcs.decodeTelemetry(Mcs.parseHex(row.str("hex")!!))
            val c = "cas $i"
            assertEquals(c, d.int("firmware_version"), t.firmwareVersion)
            assertEquals(c, d.int("state_raw"), t.stateRaw)
            assertEquals(c, d.str("state_remarks")!!.toInt(16), t.stateRemarks)
            assertEquals(c, d.bool("lid_open"), t.lidOpen)
            assertEquals(c, d.bool("cup_ntc_absent"), t.cupNtcAbsent)
            assertEquals(c, d.bool("temperature_hl_bit"), t.temperatureHighBit)
            assertEquals(c, d.bool("motor_clockwise_bit"), t.motorClockwiseBit)
            assertEquals(c, d.bool("scale_stable_bit"), t.scaleStable)
            assertEquals(c, d.int("error"), t.errorCode)
            assertEquals(c, d.str("error_name"), t.errorName)
            assertEquals(c, d.int("return_code"), t.returnCode)
            assertEquals(c, d.int("temperature_level"), t.temperatureLevel)
            assertEquals(c, d.int("returned_temperature"), t.returnedTemperature)
            assertEquals(c, d.int("actual_temperature"), t.actualTemperature)
            assertEquals(c, d.int("temperature_unit"), t.temperatureUnit)
            assertEquals(c, d.int("speed_level"), t.speedLevel)
            assertEquals(c, d.int("ui_speed"), t.uiSpeed)
            assertEquals(c, d.int("direction_raw"), t.directionRaw)
            assertEquals(c, d.int("direction_signed"), t.directionSigned)
            assertEquals(c, d.int("turbo_key"), t.turboKey)
            assertEquals(c, d.int("function"), t.function)
            assertEquals(c, d.int("weight_raw"), t.weightRaw)
            assertEquals(c, d.int("weight_decimal_point"), t.weightDecimals)
            assertEquals(c, d.int("weight_unit"), t.weightUnit)
            assertEquals(c, d.int("scale_state"), t.scaleState)
            assertEquals(c, d.int("calibration_step"), t.calibrationStep)
            val hms = d["countdown_hms"].list().map { (it as Number).toInt() }
            assertEquals(c, hms[0] * 3600 + hms[1] * 60 + hms[2], t.countdownSeconds)
            assertEquals(c, (d["countdown_ms"] as Number).toLong(), t.countdownMillis)
            assertEquals(c, d.int("light_raw"), t.lightRaw)
            assertEquals(c, d.int("button_raw"), t.buttonRaw)
        }
    }

    @Test fun stopAndTareAreMinimal() {
        val stop = Mcs.parse(Mcs.encodeStop(7))
        assertEquals(Mcs.TX_SIZE, stop.raw.size)
        assertEquals(Mcs.OP_CONFIG, stop.opcode)
        // Tout à zéro après l'opcode : ni moteur, ni chauffe, ni minuterie, fonction « stop ».
        for (i in 2 until stop.payload.size) assertEquals("octet $i", 0, stop.payload[i].toInt())
        val tare = Mcs.parse(Mcs.encodeTare(8))
        assertEquals(Mcs.OP_SCALE, tare.opcode)
        assertEquals(1, tare.payload[13].toInt())
    }

    @Test fun manualLayout() {
        val f = Mcs.parse(Mcs.encodeManual(speed = 3, celsius = 100, seconds = 3723, reverse = true, sequence = 1))
        val p = f.payload
        assertEquals(29, p.size)
        assertEquals(Mcs.levelForCelsius(100), p[2].toInt())
        assertEquals(6, p[8].toInt()) // vitesse 3 → niveau brut 6
        assertEquals(1, p[9].toInt())
        assertEquals(Mcs.FN_MANUAL, p[11].toInt())
        assertEquals(listOf(1, 2, 3), listOf(p[17].toInt(), p[18].toInt(), p[19].toInt())) // 1 h 02 min 03 s
    }

    @Test fun refusesOutOfRangeAndForbiddenOpcodes() {
        assertRefused { Mcs.encodeManual(11, 0, 0, false, 1) }
        assertRefused { Mcs.encodeManual(1, 38, 0, false, 1) }
        assertRefused { Mcs.encodeManual(1, 0, Mcs.MAX_MANUAL_SECONDS + 1, false, 1) }
        assertRefused { Mcs.encode(McsCommand(speedLevel = 21), 1) }
        assertRefused { Mcs.encode(McsCommand(temperatureLevel = 21), 1) }
        assertRefused { Mcs.encode(McsCommand(seconds = Mcs.MAX_TIMER_SECONDS + 1), 1) }
        assertRefused { Mcs.encode(McsCommand(opcode = 0x0805), 1) } // sens inconnu
        assertRefused { Mcs.encode(McsCommand(opcode = 0xFE03), 1) } // mise à jour du microcontrôleur
        assertRefused { Mcs.encodeStop(256) }
    }

    @Test fun parseRejectsDamage() {
        val raw = Mcs.encodeStop(1)
        assertRefused { Mcs.parse(raw.copyOf(raw.size - 1)) }
        assertRefused { Mcs.parse(raw + byteArrayOf(0)) }
        val bad = raw.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertRefused { Mcs.parse(bad) }
        // Une commande n'est pas une trame d'état.
        assertRefused { Mcs.decodeTelemetry(raw) }
    }

    private fun sampleRx(i: Int): ByteArray = Mcs.parseHex(rows("android-rx-vectors.json")[i].str("hex")!!)

    @Test fun streamFragmentsAtEverySplit() {
        val rx = sampleRx(0)
        for (split in 0..rx.size) {
            val p = McsStreamParser()
            val out = p.feed(rx.copyOfRange(0, split), nowMillis = 0) + p.feed(rx.copyOfRange(split, rx.size), nowMillis = 0)
            assertEquals("coupure $split", 1, out.size)
            assertArrayEquals(rx, out[0])
        }
        // Octet par octet.
        val p = McsStreamParser()
        val out = ArrayList<ByteArray>()
        for (b in rx) p.feed(byteArrayOf(b), nowMillis = 0, out = out)
        assertEquals(1, out.size)
    }

    @Test fun streamNoiseCorruptionAndConcatenation() {
        val a = sampleRx(1)
        val b = sampleRx(2)
        val bad = a.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val p = McsStreamParser()
        val out = p.feed(byteArrayOf(1, 2, 3) + bad + a + b, nowMillis = 0)
        assertEquals(2, out.size)
        assertArrayEquals(a, out[0])
        assertArrayEquals(b, out[1])
        assertTrue(p.invalidFrames > 0)
        assertEquals(0, p.bufferedBytes)
    }

    @Test fun streamIncompletePrefixExpires() {
        val rx = sampleRx(3)
        val p = McsStreamParser(timeoutMillis = 500)
        // Un début de trame plausible (longueur 200) jamais complété, suivi d'une vraie trame.
        assertEquals(0, p.feed(byteArrayOf(0xAA.toByte(), 0, 199.toByte()) + rx, nowMillis = 0).size)
        assertEquals(0, p.expire(nowMillis = 400).size)
        val out = p.expire(nowMillis = 600)
        assertEquals(1, out.size)
        assertArrayEquals(rx, out[0])
    }

    @Test fun streamRejectsAbsurdLengths() {
        val rx = sampleRx(4)
        val p = McsStreamParser()
        // Longueur déclarée 0xFFFF : rejetée tout de suite, la vraie trame passe.
        val out = p.feed(byteArrayOf(0xAA.toByte(), 0xFF.toByte(), 0xFF.toByte()) + rx, nowMillis = 0)
        assertEquals(1, out.size)
        assertArrayEquals(rx, out[0])
    }
}
