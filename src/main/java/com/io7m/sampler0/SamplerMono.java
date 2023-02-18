/*
 * Copyright © 2022 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.sampler0;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Objects;

public final class SamplerMono implements SamplerType
{
  private final EventBuffer events;
  private final AudioContextType context;
  private volatile SampleMap samples;
  private volatile SampleState samplePlaying;
  private volatile double pitchBend;
  private final double frame[] = new double[2];

  private static final class SampleState
  {
    private final SampleMapEntry sample;
    private boolean done;
    private double positionReal;
    private int position;

    SampleState(
      final SampleMapEntry inSample)
    {
      this.sample = Objects.requireNonNull(inSample, "sample");
      this.position = 0;
      this.positionReal = 0.0;
      this.done = false;
    }

    public void evaluate(
      final double[] frame,
      final double pitchBend)
    {
      if (this.done) {
        Arrays.fill(frame, 0.0);
        return;
      }

      final var sampleBuffer = this.sample.sample();
      sampleBuffer.frameGetExact(this.position, frame);

      final double rateScale =
        PitchBend.pitchBendToRate(pitchBend, 24);

      final var newPositionReal =
        this.positionReal + (this.sample.playbackRate() * rateScale);

      final var newPosition =
        (int) newPositionReal;

      if (newPosition >= sampleBuffer.frames()) {
        this.done = true;
      }

      this.positionReal = newPositionReal;
      this.position = newPosition;
    }
  }

  public SamplerMono(
    final AudioContextType inContext)
  {
    this.context =
      Objects.requireNonNull(inContext, "context");
    this.events =
      new EventBuffer();
    this.samples =
      SampleMap.empty();

    this.loadSamples(this.samples.description());
  }

  @Override
  public void loadSamples(
    final SampleMapDescription map)
  {
    this.context.executeIO(() -> {
      final var newMap = map.load(
        this.context.sampleRateConverter(),
        this.context.sampleRate().get().intValue()
      );
      this.samples = newMap;
      return newMap;
    });
  }

  @Override
  public void onProcess(
    final FloatBuffer bufferL,
    final FloatBuffer bufferR,
    final int frames)
  {
    for (int index = 0; index < frames; ++index) {
      final var events = this.events.eventsTake(index);
      for (final var event : events) {
        if (event instanceof EventType.NoteOn eventNoteOn) {
          this.samplePlaying =
            new SampleState(this.samples.sampleForNote(eventNoteOn.note()));
        }
        if (event instanceof EventType.NoteOff eventNoteOff) {
          this.samplePlaying = null;
        }
        if (event instanceof EventType.PitchBend eventPitchBend) {
          this.pitchBend = eventPitchBend.value();
        }
      }

      final var playing = this.samplePlaying;
      if (playing != null) {
        final var channels = playing.sample.sample().channels();
        playing.evaluate(this.frame, this.pitchBend);

        bufferL.put(index, (float) this.frame[0]);
        if (channels == 2) {
          bufferR.put(index, (float) this.frame[1]);
        } else {
          bufferR.put(index, (float) this.frame[0]);
        }
      } else {
        bufferL.put(index, 0.0f);
        bufferR.put(index, 0.0f);
      }
    }
  }

  @Override
  public void onEvent(
    final EventType event)
  {
    this.events.eventAdd(event);
  }
}
