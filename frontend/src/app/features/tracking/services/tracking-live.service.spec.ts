import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { filter, firstValueFrom } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { TrackingLiveService } from './tracking-live.service';

describe('TrackingLiveService', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('attaches the bearer token and parses a tracking update', async () => {
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          new TextEncoder().encode(
            'event: tracking-update\n' +
              'data: {"trackingNumber":"SWD-2026-100001","status":"IN_TRANSIT",' +
              '"safeLocationLabel":"Western Route","updatedAt":"2026-11-14T10:00:00Z"}\n\n',
          ),
        );
        controller.close();
      },
    });
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(
        new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
      );
    TestBed.configureTestingModule({
      providers: [
        TrackingLiveService,
        {
          provide: AuthService,
          useValue: { token: () => 'signed-jwt', isSignedIn: signal(true), user: signal(null) },
        },
      ],
    });

    const update = await firstValueFrom(
      TestBed.inject(TrackingLiveService)
        .connect('SWD-2026-100001')
        .pipe(filter((event) => event.type === 'update')),
    );

    expect(update).toEqual({
      type: 'update',
      state: {
        trackingNumber: 'SWD-2026-100001',
        status: 'IN_TRANSIT',
        safeLocationLabel: 'Western Route',
        updatedAt: '2026-11-14T10:00:00Z',
      },
    });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/tracking/SWD-2026-100001/stream',
      expect.objectContaining({
        headers: {
          Accept: 'text/event-stream',
          Authorization: 'Bearer signed-jwt',
        },
      }),
    );
  });
});
