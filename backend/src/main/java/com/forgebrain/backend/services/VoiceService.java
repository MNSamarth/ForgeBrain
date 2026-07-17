package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.VoiceResult;

/**
 * Contract for synthesizing narration audio from a storyboard. See renderer/voice-spec.md.
 * The intended Phase 1 provider is Google Cloud Text-to-Speech (docs/CONFIGURATION.md
 * Section 2), reached indirectly — this interface does not depend on any Google Cloud type,
 * consistent with the "replaceable AI providers" principle in docs/ARCHITECTURE.md.
 */
public interface VoiceService {

    /**
     * @param storyboard   supplies every scene's voiceover_text and the estimated durations
     *                     this stage's real measurements get compared against
     * @param voiceProfile which synthetic voice, language, rate, and pitch to render with
     */
    VoiceResult generateVoice(Storyboard storyboard, VoiceResult.VoiceProfile voiceProfile);
}
