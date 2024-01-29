package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.filter.equalizer.Equalizer;
import com.sedmelluq.discord.lavaplayer.filter.equalizer.EqualizerConfiguration;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.Collections;
import java.util.List;

/**
 * PCM filter factory which creates a single {@link Equalizer} filter for every track. Useful in case the equalizer is
 * the only custom filter used.
 */
public class EqualizerFactoryNew extends EqualizerConfiguration implements PcmFilterFactory {
  /**
   * Creates a new instance no gains applied initially.
   */
  public EqualizerFactoryNew() {
    super(new float[]{0.6f,
      0.6f,
      0.6f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f,
      -0.25f});
  }

  @Override
  public List<AudioFilter> buildChain(AudioTrack track, AudioDataFormat format, UniversalPcmAudioFilter output) {
    if (Equalizer.isCompatible(format)) {
      return Collections.singletonList(new Equalizer(format.channelCount, output, bandMultipliers));
    } else {
      return Collections.emptyList();
    }
  }
}
