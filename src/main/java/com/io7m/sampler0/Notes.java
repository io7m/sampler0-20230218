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

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;

import java.util.Objects;

public final class Notes
{
  /**
   * For a given frequency {@code f}, multiply {@code f} by this value to yield
   * a frequency exactly one semitone higher.
   */

  public static final double ONE_SEMITONE_UP = 1.059462351977181;

  /**
   * For a given frequency {@code f}, multiply {@code f} by this value to yield
   * a frequency exactly one semitone lower.
   */

  public static final double ONE_SEMITONE_DOWN = 0.9438743126810656;

  private final Int2DoubleOpenHashMap frequencies;

  private Notes(
    final Int2DoubleOpenHashMap inFrequencies)
  {
    this.frequencies =
      Objects.requireNonNull(inFrequencies, "frequencies");
  }

  public static Notes create()
  {
    final var frequencies = new Int2DoubleOpenHashMap(129);
    for (int note = 0; note <= 128; ++note) {
      final var exp = (double) (note - 16) / 12.0;
      final var r = StrictMath.pow(2.0, exp);
      final var f = 440.0 * r;
      frequencies.put(note, f);
    }
    return new Notes(frequencies);
  }

  public double frequencyOfNote(
    final int note)
  {
    return this.frequencies.getOrDefault(note, 0.0);
  }
}
