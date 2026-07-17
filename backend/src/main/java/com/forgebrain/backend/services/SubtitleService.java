package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VoiceResult;

/**
 * Contract for reconciling the storyboard's estimated subtitle timing with the voice stage's
 * real, measured audio timing. See renderer/subtitle-spec.md Section 4 for the two
 * reconciliation methods this must choose between per scene.
 */
public interface SubtitleService {

    SubtitleResult generateSubtitles(Storyboard storyboard, VoiceResult voiceResult);
}
