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

public final class EnvelopeD
{
  private final int decayFrames;
  private int time;

  public EnvelopeD()
  {
    this.decayFrames = 11000;
    this.time = this.decayFrames;
  }

  public void trigger()
  {
    this.time = 0;
  }

  public float evaluate()
  {
    final var timeF = (double) this.time;
    final var maxF = (double) this.decayFrames;

    final var now = 1.0 - (timeF / maxF);
    this.time = Math.min(this.decayFrames, this.time + 1);
    return (float) now;
  }
}
