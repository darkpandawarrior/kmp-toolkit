# Decisions log

Settled choices for this repo, one line each, so a new session does not re-open them. Read before proposing an architectural change, and append a line when a decision is made.

- 2026-07-14: The eight standalone KMP libraries were consolidated into this one monorepo with natural module paths (:common, :mvi-core, :security). Why: consumers collapse several per-leaf submodules into one, and the published artifact coordinates under com.siddharth.kmp stay unchanged. (source: 0b59101, PaymentsLab c50b237)
- 2026-09-21: The Gradle configuration cache is on, and problems=warn is deliberately not set. Why: the audit came back clean and warm incremental assemble dropped from about 21-28s to about 7s, while the default fail mode keeps a new incompatibility a red build at the commit that introduces it. (source: e11efe1)
- 2026-09-22: detekt baselines are not kept. All 116 baselined findings were fixed in code and the 18 per-module baseline files deleted. Why: a baseline hides debt, and the pass also found two real defects, including a hardcoded false in PaymentCertificatePinning.isPinningActive(). (source: 462d860)
- 2026-09-28: app-shell no longer carries an unconditional play-services-location dependency, so location is bound per flavor. Why: the noGms runtime classpath must not carry Google Play services. (source: 5f9a4ca)
- 2026-10-04: ML Kit and Google Pay moved out of :ai and :designsystem into the opt-in modules ai-mlkit and designsystem-wallet-gms. Why: the base artifacts should not pull Google services into every Android app, and FOSS consumers omit both modules. This is a breaking change for Play consumers, who must add the modules and register their Koin modules. (source: 76cb747, docs/gms-opt-in.md)
