package app.toque.core.smart

import app.toque.core.i18n.tr
import app.toque.core.machine.Clock
import app.toque.core.machine.Transport
import app.toque.core.protocol.Mcs
import app.toque.core.protocol.McsProtocolException
import app.toque.core.protocol.McsStreamParser
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Base moteur du Smart simulée, calquée sur ce que la base du robot de référence a montré
 * (firmware 3090, écoutes du 21/11/2026) :
 *  - une réponse de 53 octets (en-tête 01 0F 00 00) par commande reçue, avec la même séquence ;
 *  - opcode, niveau de température, vitesse, fonction et compte à rebours renvoyés en écho ;
 *  - aucun décompte du temps ;
 *  - température réelle par paliers de la table, 0 sous 37 °C (montée d'environ 0,3 °C/s, comme
 *    les 37 → 55 °C observés en 63 s à 70 °C avec 500 ml) ;
 *  - poids en grammes, figé pendant une cuisson, état 4 en surcharge ;
 *  - bits 0x80 (couvercle ouvert) et 0x40 (bol absent).
 * La physique reste volontairement simple : de quoi tester le pilotage, pas la cuisson.
 */
class SmartSimBoard(private val clock: Clock) {
    private val parser = McsStreamParser()
    private var lastMs = clock.nowMs()

    private var function = 0
    private var speedLevel = 0
    private var temperatureLevel = 0
    private var reverse = false
    private var countdown = 0
    private var turbo = 0
    private var exact = 0
    private var temp = 22.0
    private var tareOffset = 0
    private var frozenWeight = 0

    @Volatile var lidOpen = false
    @Volatile var bowlPresent = true
    @Volatile var errorCode = 0
    @Volatile var returnCode = 0
    @Volatile var contentWeightG = 0
    /** La base ne répond plus. */
    @Volatile var silent = false
    /** La base ignore les commandes et renvoie toujours le dernier réglage en marche reçu. */
    @Volatile var ignoreCommands = false
    /**
     * Comme la vraie base (21/11/2026) : couvercle ouvert ou bol absent, elle coupe le moteur et la
     * chauffe d'elle-même et le montre dans sa réponse (vitesse, fonction, température à 0).
     */
    @Volatile var interlock = true
    /** Température de départ du contenu (°C). */
    @Volatile var startTemperature = 22.0

    var received = 0L
        private set

    @Synchronized
    fun reset() {
        function = 0; speedLevel = 0; temperatureLevel = 0; reverse = false; countdown = 0
        temp = startTemperature
        lastMs = clock.nowMs()
        parser.reset()
    }

    /** Température réelle du modèle (°C), pour les tests. */
    val modelTemperature: Double @Synchronized get() = temp

    @Synchronized
    fun feed(bytes: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>(1)
        for (raw in parser.feed(bytes, bytes.size, clock.nowMs())) {
            val f = try {
                Mcs.parse(raw)
            } catch (e: McsProtocolException) {
                continue
            }
            if (f.opcode !in Mcs.SENDABLE_OPCODES) continue
            advance()
            received++
            val p = f.payload
            if (f.opcode == Mcs.OP_CONFIG && !ignoreCommands) {
                temperatureLevel = p[2].toInt().coerceIn(0, Mcs.MAX_TEMPERATURE_LEVEL)
                speedLevel = p[8].toInt().coerceIn(0, Mcs.MAX_SPEED_LEVEL)
                reverse = p[9].toInt() != 0
                function = p[11].toInt() and 0xFF
                turbo = p[10].toInt() and 0xFF
                exact = ((p[3].toInt() and 0xFF) shl 8) or (p[4].toInt() and 0xFF)
                countdown = (p[17].toInt() and 0xFF) * 3600 + (p[18].toInt() and 0xFF) * 60 + (p[19].toInt() and 0xFF)
            }
            if (f.opcode == Mcs.OP_SCALE && (p[13].toInt() and 0xFF) == 1 && function == 0) tareOffset = contentWeightG
            if (!silent) out += reply(f.opcode, f.sequence)
        }
        return out
    }

    private fun heating(): Boolean = function != Mcs.FN_STOP && function != Mcs.FN_TURBO && temperatureLevel > 0 &&
        errorCode == 0 && !lidOpen && bowlPresent

    private fun advance() {
        val now = clock.nowMs()
        val dt = (now - lastMs).coerceIn(0, 1000) / 1000.0
        lastMs = now
        temp = if (heating()) {
            // Programmes à température exacte (mijoter, sous-vide…) : la base régule sur cette valeur.
            val goal = (if (exact > 0) exact else Mcs.celsiusForLevel(temperatureLevel)).toDouble()
            if (temp < goal) minOf(goal, temp + 0.3 * dt) else maxOf(goal, temp - 0.02 * dt)
        } else maxOf(startTemperature.coerceAtMost(22.0), temp - 0.02 * dt)
    }

    private fun reportedTemperature(): Int = if (temp < 37.0) 0 else Mcs.celsiusForLevel(Mcs.levelAtMost(temp.toInt()))

    private fun reply(opcode: Int, sequence: Int): ByteArray {
        val p = ByteArray(Mcs.RX_PAYLOAD_SIZE + 5)
        val echoOpcode = opcode
        p[0] = (echoOpcode ushr 8).toByte(); p[1] = echoOpcode.toByte()
        p[2] = (3090 ushr 8).toByte(); p[3] = 3090.toByte()
        var state = 0
        if (lidOpen) state = state or 0x80
        if (!bowlPresent) state = state or 0x40
        p[4] = state.toByte()
        p[7] = errorCode.toByte()
        p[8] = returnCode.toByte()
        val cut = interlock && (lidOpen || !bowlPresent)
        p[9] = (if (cut) 0 else temperatureLevel).toByte()
        val t = reportedTemperature()
        p[12] = (t ushr 8).toByte(); p[13] = t.toByte()
        p[17] = (if (cut) 0 else speedLevel).toByte()
        p[18] = (if (reverse && !cut) 1 else 0).toByte()
        // Comme la vraie base : fonction 1 en réponse à toutes les fonctions sauf 0 et 198.
        p[20] = (if (cut) 0 else Mcs.echoFunction(function)).toByte()
        p[19] = (if (cut || function != Mcs.FN_TURBO) 0 else turbo).toByte()
        val cooking = function != 0
        if (!cooking) frozenWeight = contentWeightG - tareOffset
        val w = frozenWeight
        p[22] = (w shr 24).toByte(); p[23] = (w shr 16).toByte(); p[24] = (w shr 8).toByte(); p[25] = w.toByte()
        p[26] = (if (contentWeightG > 5000) 4 else if (cooking) 2 else 0).toByte()
        p[31] = (countdown / 3600).toByte(); p[32] = (countdown / 60 % 60).toByte(); p[33] = (countdown % 60).toByte()
        val out = ByteArray(8 + p.size + 1)
        out[0] = Mcs.HEADER.toByte()
        out[1] = ((out.size - 1) ushr 8).toByte(); out[2] = (out.size - 1).toByte()
        Mcs.BOARD_HEADER.copyInto(out, 3)
        out[7] = sequence.toByte()
        p.copyInto(out, 8)
        out[out.size - 1] = Mcs.checksum(out).toByte()
        return out
    }
}

class SmartSimTransport(val board: SmartSimBoard) : Transport {
    override val name: String get() = tr("Démo Smart (base simulée)")
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var closed = false
    private var pending: ByteArray? = null
    private var pendingAt = 0

    override fun open() {
        closed = false
        queue.clear()
        board.reset()
    }

    override fun write(frame: ByteArray) {
        if (closed) throw IOException(tr("fermé"))
        for (r in board.feed(frame)) queue.put(r)
    }

    override fun read(buffer: ByteArray): Int {
        while (true) {
            if (closed) return -1
            val p = pending
            if (p != null) {
                val n = minOf(buffer.size, p.size - pendingAt)
                System.arraycopy(p, pendingAt, buffer, 0, n)
                pendingAt += n
                if (pendingAt >= p.size) pending = null
                return n
            }
            val next = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (next.isEmpty()) return -1
            pending = next
            pendingAt = 0
        }
    }

    override fun close() {
        closed = true
        queue.offer(ByteArray(0))
    }
}
