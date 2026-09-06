package com.siddharth.kmp.ai

/**
 * Marks an [OnDeviceLlm] that compiles and satisfies the seam but has no real implementation behind
 * it yet — it reports unavailable unconditionally rather than attempting partial/incorrect work.
 * Distinct from [UnavailableOnDeviceLlm] (a deliberate, permanent "no model on this platform/target"
 * answer): a class carrying this annotation WOULD work once [reason] (its missing native bridge) is
 * built — see the annotated class's own upgrade-path doc comment.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Unimplemented(val reason: String)
