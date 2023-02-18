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

import com.io7m.jattribute.core.AttributeReadableType;
import com.io7m.jattribute.core.AttributeType;
import com.io7m.jattribute.core.Attributes;
import com.io7m.jsamplebuffer.api.SampleBufferRateConverterFactoryType;
import com.io7m.jsamplebuffer.api.SampleBufferRateConverterType;
import com.io7m.jsamplebuffer.xmedia.SXMSampleBufferRateConverters;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import org.jaudiolibs.jnajack.Jack;
import org.jaudiolibs.jnajack.JackClient;
import org.jaudiolibs.jnajack.JackException;
import org.jaudiolibs.jnajack.JackMidi;
import org.jaudiolibs.jnajack.JackPort;
import org.jaudiolibs.jnajack.JackStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.jaudiolibs.jnajack.JackOptions.JackNoStartServer;
import static org.jaudiolibs.jnajack.JackPortFlags.JackPortIsInput;
import static org.jaudiolibs.jnajack.JackPortFlags.JackPortIsOutput;
import static org.jaudiolibs.jnajack.JackPortFlags.JackPortIsPhysical;
import static org.jaudiolibs.jnajack.JackPortType.AUDIO;
import static org.jaudiolibs.jnajack.JackPortType.MIDI;

public final class Main
{
  private static final Logger LOG =
    LoggerFactory.getLogger(Main.class);

  private Main()
  {

  }

  private static final class AudioContext implements AudioContextType
  {
    private final AttributeType<Integer> sampleRate;
    private final AttributeType<Integer> bufferSize;
    private final ExecutorService ioExecutor;
    private final SampleBufferRateConverterType sampleRateConverter;

    AudioContext(
      final Attributes inAttributes,
      final SampleBufferRateConverterFactoryType converters,
      final ExecutorService inIOExecutor,
      final int inSampleRate,
      final int inBufferSize)
    {
      this.ioExecutor =
        inIOExecutor;
      this.sampleRate =
        inAttributes.create(Integer.valueOf(inSampleRate));
      this.bufferSize =
        inAttributes.create(Integer.valueOf(inBufferSize));
      this.sampleRateConverter =
        converters.createConverter();
    }

    @Override
    public SampleBufferRateConverterType sampleRateConverter()
    {
      return this.sampleRateConverter;
    }

    @Override
    public <T> CompletableFuture<T> executeIO(
      final IOOperationType<T> operation)
    {
      final var future = new CompletableFuture<T>();
      this.ioExecutor.execute(() -> {
        final var timeThen = Instant.now();
        try {
          future.complete(operation.execute());
        } catch (final Throwable e) {
          LOG.error("error: ", e);
          future.completeExceptionally(e);
        } finally {
          final var timeNow = Instant.now();
          LOG.debug("i/o operation took {}", Duration.between(timeThen, timeNow));
        }
      });
      return future;
    }

    @Override
    public AttributeReadableType<Integer> sampleRate()
    {
      return this.sampleRate;
    }

    @Override
    public AttributeReadableType<Integer> bufferSize()
    {
      return this.bufferSize;
    }
  }

  public static void main(
    final String[] args)
    throws JackException
  {
    final var jack =
      Jack.getInstance();

    final var status =
      EnumSet.noneOf(JackStatus.class);

    final var client =
      jack.openClient("sampler0", EnumSet.of(JackNoStartServer), status);

    final var outL =
      client.registerPort("outL", AUDIO, JackPortIsOutput);
    final var outR =
      client.registerPort("outR", AUDIO, JackPortIsOutput);
    final var inM =
      client.registerPort("inM", MIDI, JackPortIsInput);

    final var executors =
      Executors.newFixedThreadPool(4, r -> {
        final var thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("com.io7m.sampler0.io." + thread.getId());
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
      });

    final var context =
      new AudioContext(
        Attributes.create(throwable -> LOG.error("error: ", throwable)),
        new SXMSampleBufferRateConverters(),
        executors,
        client.getSampleRate(),
        client.getBufferSize()
      );

    client.setBuffersizeCallback((c, buffersize) -> {
      context.bufferSize.set(Integer.valueOf(buffersize));
    });

    client.setSampleRateCallback((c, sampleRate) -> {
      context.sampleRate.set(Integer.valueOf(sampleRate));
    });

    final var samplers =
      new SamplersPoly();
    final var sampler =
      samplers.createSampler(context);

    final var sampleDescriptions = new Int2ObjectRBTreeMap<Path>();
    sampleDescriptions.put(60, Paths.get("60.wav"));
    sampleDescriptions.put(61, Paths.get("61.wav"));
    sampleDescriptions.put(62, Paths.get("62.wav"));
    sampler.loadSamples(new SampleMapDescription(sampleDescriptions));

    client.setProcessCallback((c, nframes) -> {
      try {
        final var eventCount =
          JackMidi.getEventCount(inM);
        final var event =
          new JackMidi.Event();

        for (int index = 0; index < eventCount; ++index) {
          JackMidi.eventGet(event, inM, index);
          final var size = event.size();
          final var data = new byte[size];
          event.read(data);

          final var parsedEvent = parseEvent(data, event.time());
          if (parsedEvent == null) {
            continue;
          }
          sampler.onEvent(parsedEvent);
        }
      } catch (final JackException e) {
        throw new RuntimeException(e);
      }

      final var bufferL = outL.getFloatBuffer();
      final var bufferR = outR.getFloatBuffer();
      sampler.onProcess(bufferL, bufferR, nframes);
      return true;
    });

    autoconnect(jack, client, List.of(outL, outR));

    client.activate();

    while (true) {
      try {
        Thread.sleep(1_000L);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static EventType parseEvent(
    final byte[] data,
    final int time)
  {
    if (data.length > 0) {
      final var status = (data[0] & 0b11110000) >>> 4;
      final var channel = (data[0] & 0b00001111);

      if (status == 9) {
        final var note = (int) data[1] & 0xff;
        final var velo = (int) data[2] & 0xff;
        final var velF = (float) velo / 127.0f;
        return new EventType.NoteOn(time, note, velF);
      }

      if (status == 8) {
        final var note = (int) data[1] & 0xff;
        return new EventType.NoteOff(time, note);
      }

      if (status == 14) {
        final var lsb = (int) data[1];
        final var msb = (int) data[2];
        final var val = (msb << 8) | lsb;
        final var valD = ((double) val) / 32768.0;
        final var valS = (valD * 2.0) - 1.0;
        return new EventType.PitchBend(time, valS);
      }
    }
    return null;
  }

  private static void autoconnect(
    final Jack jack,
    final JackClient client,
    final List<JackPort> outputs)
    throws JackException
  {
    final var physical =
      jack.getPorts(
        client,
        null,
        AUDIO,
        EnumSet.of(JackPortIsInput, JackPortIsPhysical)
      );

    final var count = Math.min(outputs.size(), physical.length);
    for (int index = 0; index < count; index++) {
      jack.connect(client, outputs.get(index).getName(), physical[index]);
    }
  }
}
