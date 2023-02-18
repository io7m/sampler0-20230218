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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public final class EventBuffer
{
  private final HashMap<Integer, LinkedList<EventType>> events;

  public EventBuffer()
  {
    this.events = new HashMap<>(8192);
  }

  public void eventAdd(
    final EventType event)
  {
    final var time = Integer.valueOf(event.timeOffsetInFrames());
    var byArrival = this.events.get(time);
    if (byArrival == null) {
      byArrival = new LinkedList<>();
    }
    byArrival.add(event);
    this.events.put(time, byArrival);
  }

  public List<EventType> eventsTake(
    final int time)
  {
    final var byArrival = this.events.remove(Integer.valueOf(time));
    if (byArrival == null) {
      return List.of();
    }
    return byArrival;
  }
}
