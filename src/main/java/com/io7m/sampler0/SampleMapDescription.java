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

import com.io7m.jsamplebuffer.api.SampleBufferException;
import com.io7m.jsamplebuffer.api.SampleBufferRateConverterType;
import com.io7m.jsamplebuffer.api.SampleBufferType;
import com.io7m.jsamplebuffer.vanilla.SampleBufferDouble;
import com.io7m.jsamplebuffer.xmedia.SXMSampleBuffers;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;

public record SampleMapDescription(
  Int2ObjectSortedMap<Path> filesByNote)
{
  public static SampleMapDescription empty()
  {
    return new SampleMapDescription(Int2ObjectSortedMaps.emptyMap());
  }

  public SampleMap load(
    final SampleBufferRateConverterType converter,
    final int sampleRate)
    throws SampleBufferException
  {
    try {
      if (this.filesByNote.isEmpty()) {
        return SampleMap.empty();
      }

      final var sampleBuffersByNote =
        new Int2ObjectRBTreeMap<SampleBufferType>();

      for (final var entry : this.filesByNote.int2ObjectEntrySet()) {
        final var sampleBuffer =
          SXMSampleBuffers.sampleBufferOfFile(
            entry.getValue(),
            SampleBufferDouble::createWithHeapBuffer
          );

        final SampleBufferType outputBuffer;
        final var currentRate = (int) sampleBuffer.sampleRate();
        if (currentRate != sampleRate) {
          outputBuffer = converter.convert(
            SampleBufferDouble::createWithHeapBuffer,
            sampleBuffer,
            sampleRate
          );
        } else {
          outputBuffer = sampleBuffer;
        }

        sampleBuffersByNote.put(entry.getIntKey(), outputBuffer);
      }

      final var sampleEntriesByNote =
        new Int2ObjectRBTreeMap<SampleMapEntry>();

      for (final var entry : sampleBuffersByNote.int2ObjectEntrySet()) {
        final var note = entry.getIntKey();
        final var sample = entry.getValue();

        sampleEntriesByNote.put(note, new SampleMapEntry(sample, 1.0));

        {
          var rate = 1.0;
          for (int noteBefore = note - 1; noteBefore >= 0; --noteBefore) {
            final var previous = sampleBuffersByNote.get(noteBefore);
            if (previous != null) {
              break;
            }
            rate = rate * Notes.ONE_SEMITONE_DOWN;
            sampleEntriesByNote.put(noteBefore, new SampleMapEntry(sample, rate));
          }
        }

        {
          var rate = 1.0;
          for (int noteAfter = note + 1; noteAfter <= 127; ++noteAfter) {
            final var next = sampleBuffersByNote.get(noteAfter);
            if (next != null) {
              break;
            }
            rate = rate * Notes.ONE_SEMITONE_UP;
            sampleEntriesByNote.put(noteAfter, new SampleMapEntry(sample, rate));
          }
        }
      }

      return new SampleMap(
        sampleEntriesByNote,
        this
      );
    } catch (final IOException
                   | UnsupportedAudioFileException
                   | SampleBufferException e) {
      throw new SampleBufferException(e);
    }
  }
}
