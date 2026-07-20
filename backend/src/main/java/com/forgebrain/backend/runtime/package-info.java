/**
 * The ForgeBrain Runtime: the single coordinator that drives {@link
 * com.forgebrain.backend.job.ReelJobService} across a whole batch of reels — {@link
 * com.forgebrain.backend.runtime.ForgeBrainRuntime}, its mutable in-progress {@link
 * com.forgebrain.backend.runtime.RuntimeState}, and the immutable {@link
 * com.forgebrain.backend.runtime.RuntimeReport} one run produces. See backend/README.md's
 * "Runtime" section.
 */
package com.forgebrain.backend.runtime;
