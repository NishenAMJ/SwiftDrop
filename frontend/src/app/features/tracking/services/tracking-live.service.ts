import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { TrackingConnectionEvent, TrackingStreamState } from '../models/tracking.models';

const RECONNECT_BASE_DELAY_MS = 1_000;
const RECONNECT_MAX_DELAY_MS = 10_000;

@Injectable({ providedIn: 'root' })
export class TrackingLiveService {
  private readonly auth = inject(AuthService);

  /**
   * Uses fetch streaming because the native EventSource API cannot attach the bearer token that
   * the internal SSE endpoint requires. The stream reconnects with bounded backoff until the
   * subscriber unsubscribes.
   */
  connect(trackingNumber: string): Observable<TrackingConnectionEvent> {
    return new Observable((subscriber) => {
      const controller = new AbortController();

      const run = async () => {
        let attempt = 0;
        while (!controller.signal.aborted) {
          if (attempt > 0) {
            subscriber.next({ type: 'reconnecting', attempt });
            await wait(reconnectDelay(attempt), controller.signal);
          }

          try {
            const token = this.auth.token();
            if (!token) {
              throw new Error('A signed-in session is required for live tracking.');
            }

            const response = await fetch(
              `/api/v1/tracking/${encodeURIComponent(trackingNumber)}/stream`,
              {
                headers: {
                  Accept: 'text/event-stream',
                  Authorization: `Bearer ${token}`,
                },
                signal: controller.signal,
              },
            );
            if (!response.ok || !response.body) {
              throw new Error('The live tracking stream is unavailable.');
            }

            attempt = 0;
            subscriber.next({ type: 'connected' });
            await consumeEventStream(response.body, (eventName, data) => {
              if (eventName === 'tracking-update' || eventName === 'tracking-snapshot') {
                subscriber.next({
                  type: 'update',
                  state: JSON.parse(data) as TrackingStreamState,
                });
              }
            });
            attempt = 1;
          } catch (error) {
            if (controller.signal.aborted) {
              return;
            }
            attempt += 1;
          }
        }
      };

      void run();
      return () => controller.abort();
    });
  }
}

async function consumeEventStream(
  stream: ReadableStream<Uint8Array>,
  onEvent: (eventName: string, data: string) => void,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value, { stream: !done });
    // Normalize after concatenation so a CRLF split across network chunks is handled correctly.
    buffer = buffer.replaceAll('\r\n', '\n');

    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      dispatchBlock(block, onEvent);
      boundary = buffer.indexOf('\n\n');
    }

    if (done) {
      return;
    }
  }
}

function dispatchBlock(block: string, onEvent: (eventName: string, data: string) => void): void {
  let eventName = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).trimStart());
    }
  }
  if (data.length > 0) {
    onEvent(eventName, data.join('\n'));
  }
}

function reconnectDelay(attempt: number): number {
  return Math.min(RECONNECT_BASE_DELAY_MS * 2 ** Math.max(0, attempt - 1), RECONNECT_MAX_DELAY_MS);
}

function wait(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal.aborted) {
      resolve();
      return;
    }
    const timeout = window.setTimeout(resolve, milliseconds);
    signal.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timeout);
        resolve();
      },
      { once: true },
    );
  });
}
