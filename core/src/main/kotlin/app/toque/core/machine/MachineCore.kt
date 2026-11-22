package app.toque.core.machine

import app.toque.core.protocol.Direction

/**
 * Ce que la boucle de pilotage et le service attendent d'un cœur de pilotage, quel que soit le
 * robot : [MachineController] pour le Connect, `SmartController` pour le Smart.
 * Un seul fil d'exécution appelle ces méthodes (voir [MachineLoop]).
 */
interface MachineCore {
    var listener: ((MachineEvent) -> Unit)?
    var transportName: String
    /** Un programme ou un Turbo est en cours (y compris en pause ou en arrêt). */
    val busy: Boolean

    fun onBytes(data: ByteArray, length: Int = data.size)
    fun onTransportUp(name: String)
    fun onTransportDown(reason: String)
    fun onWriteFailed(reason: String)

    /** Appelé à chaque période : met à jour l'état et renvoie la trame à émettre. */
    fun tick(): ByteArray

    /** Trame d'arrêt immédiate (fermeture, plantage). */
    fun stopFrame(): ByteArray

    fun snapshot(): Snapshot

    fun startJob(spec: JobSpec): Verdict
    fun pause(): Verdict
    fun resume(hotBlendConfirmed: Boolean = false): Verdict
    fun stop(): Verdict
    fun clearFinished()
    fun adjust(
        speed: Int? = null, temperatureLevel: Int? = null, direction: Direction? = null,
        totalMs: Long? = null, hotBlendConfirmed: Boolean = false,
    ): Verdict
    fun turboPress(accessory: Accessory = Accessory.BLADE): Verdict
    fun turboRelease()
    fun tare()
    fun acknowledgeFault(): Verdict
}
