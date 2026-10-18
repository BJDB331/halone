package dev.halone.testkit.engine

import dev.halone.glasses.api.UsbIdentity
import dev.halone.testkit.R
import kotlinx.coroutines.delay

/** Steps every profile shares. Each records an outcome, so a report reads without the audio. */
object CommonSteps {

    suspend fun consent(kit: Kit): Boolean {
        kit.io.say(kit.text(R.string.intro))
        val question = kit.text(R.string.consent_question)
        val answer = kit.io.ask(question, listOf(kit.choice("start", R.string.continue_), kit.choice("stop", R.string.stop)))
        kit.session.answer("consent", question, answer)
        return answer.id == "start"
    }

    /** Waits for any USB device, asking rather than waiting forever. Null if the tester gives up. */
    suspend fun waitForGlasses(kit: Kit, profiles: List<Profile>): UsbIdentity? {
        fun pick(): UsbIdentity? {
            val devices = kit.usbDevices()
            return devices.firstOrNull { d -> profiles.any { it !is GenericProfile && it.matches(d) } }
                ?: devices.firstOrNull()
        }

        val found = retrying(MAX_PLUG_ATTEMPTS) { attempt ->
            val prompt = kit.text(R.string.plug_in)
            if (kit.io.waitUntil(prompt, PLUG_TIMEOUT_MS, skip = null) { pick() != null }) return@retrying true
            val question = kit.text(R.string.plug_in_question)
            val answer = kit.io.ask(
                question,
                listOf(
                    kit.choice("yes", R.string.plugged_in_yes),
                    kit.choice("wait", R.string.plugged_in_not_yet),
                    kit.choice("stop", R.string.stop),
                ),
            )
            kit.session.answer("plugged_in", question, answer, attempt)
            if (answer.id == "stop") return null
            false
        }
        if (!found) {
            kit.session.outcome("glasses", "not-found")
            kit.snapshot("no-glasses")
            return null
        }
        return pick()
    }

    suspend fun checkDisplay(kit: Kit): Boolean {
        val appeared = kit.io.waitUntil(kit.text(R.string.display_waiting), DISPLAY_TIMEOUT_MS, skip = null) {
            kit.externalDisplay() != null
        }
        if (appeared) {
            val question = kit.text(R.string.display_question)
            val answer = kit.io.ask(
                question,
                listOf(kit.choice("yes", R.string.yes), kit.choice("mirror", R.string.display_mirror), kit.choice("no", R.string.no)),
            )
            kit.session.answer("display_readable", question, answer)
            kit.session.outcome("display", if (answer.id == "yes") "card-readable" else "card-${answer.id}")
            return answer.id == "yes"
        }

        val question = kit.text(R.string.display_none_question)
        val answer = kit.io.ask(
            question,
            listOf(
                kit.choice("black", R.string.display_none_black),
                kit.choice("phone", R.string.display_none_phone),
                kit.choice("other", R.string.display_none_other),
            ),
        )
        kit.session.answer("display_none", question, answer)
        kit.io.say(kit.text(R.string.display_tip))
        val late = kit.io.waitUntil(kit.text(R.string.display_waiting), DISPLAY_TIMEOUT_MS, kit.choice("skip", R.string.skip)) {
            kit.externalDisplay() != null
        }
        kit.session.outcome("display", if (late) "appeared-after-tip" else "absent")
        kit.snapshot("display-check")
        return late
    }

    /**
     * Has the tester switch modes in the glasses' menu, recording every distinct
     * mode list the phone sees on the way. Signatures are mode lists, not ids,
     * because each switch re-adds the display with new ids.
     */
    suspend fun tourDisplayModes(kit: Kit) {
        val initial = kit.externalDisplay()?.signature
        if (initial == null) {
            kit.io.say(kit.text(R.string.modes_no_display))
            kit.session.outcome("display_modes", "no-display")
            return
        }
        kit.io.say(kit.text(R.string.modes_intro))
        val seen = linkedSetOf(initial)
        kit.snapshot("display-initial")
        kit.session.outcome("display_state_1", initial)

        suspend fun observe(): String? {
            val now = kit.externalDisplay()?.signature ?: return null
            if (seen.add(now)) {
                delay(SETTLE_MS)
                kit.snapshot("display-state-${seen.size}")
                kit.session.outcome("display_state_${seen.size}", now)
            }
            return now
        }

        val switched = switchMode(kit, "3d", R.string.modes_3d) { observe().let { it != null && it != initial } }
        if (switched) switchMode(kit, "2d-again", R.string.modes_2d) { observe() == initial }
        val rated = switchMode(kit, "refresh", R.string.modes_refresh) { observe().let { it != null && it != initial } }
        if (rated) switchMode(kit, "refresh-back", R.string.modes_refresh_back) { observe() == initial }
    }

    private suspend fun switchMode(kit: Kit, label: String, prompt: Int, reached: suspend () -> Boolean): Boolean {
        val done = kit.io.waitUntil(kit.text(prompt), MODE_TIMEOUT_MS, kit.choice("skip", R.string.skip)) { reached() }
        kit.io.say(kit.text(if (done) R.string.modes_changed else R.string.modes_no_change))
        kit.session.outcome("display_mode_$label", if (done) "reached" else "skipped")
        return done
    }

    suspend fun closing(kit: Kit) {
        val question = kit.text(R.string.free_text_question)
        kit.io.askText(question, kit.choice("skip", R.string.free_text_skip))
            ?.takeIf { it.isNotBlank() }
            ?.let { kit.session.note("tester: $it") }
        kit.snapshot("end")
        kit.io.say(kit.text(R.string.thanks))
    }

    private const val MAX_PLUG_ATTEMPTS = 4
    private const val PLUG_TIMEOUT_MS = 20_000L
    private const val DISPLAY_TIMEOUT_MS = 15_000L
    private const val MODE_TIMEOUT_MS = 90_000L
    private const val SETTLE_MS = 2_000L
}

/** Any glasses we have no script for: displays, descriptors and answers still help. */
object GenericProfile : Profile {
    override val id = "generic"
    override val steps = 4

    override fun matches(device: UsbIdentity) = true

    override suspend fun run(kit: Kit, glasses: UsbIdentity) {
        kit.io.progress(1, steps)
        CommonSteps.checkDisplay(kit)
        kit.io.progress(2, steps)
        CommonSteps.tourDisplayModes(kit)
        kit.io.progress(3, steps)
        CommonSteps.closing(kit)
        kit.io.progress(4, steps)
    }
}

/** Consent, glasses, then whichever profile recognises them. False when nothing was gathered. */
class Runner(private val kit: Kit, private val profiles: List<Profile>) {

    suspend fun run(): Boolean {
        if (!CommonSteps.consent(kit)) return false
        kit.snapshot("start")
        val glasses = CommonSteps.waitForGlasses(kit, profiles) ?: return kit.session.files.isNotEmpty()
        val profile = profiles.firstOrNull { it !is GenericProfile && it.matches(glasses) } ?: GenericProfile
        kit.session.profileId = profile.id
        kit.session.outcome("glasses", "%04x:%04x".format(glasses.vendorId, glasses.productId))
        kit.io.say(kit.text(if (profile === GenericProfile) R.string.found_unknown else R.string.found_glasses))
        kit.snapshot("attached")
        profile.run(kit, glasses)
        return true
    }
}
