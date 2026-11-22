import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CourierDeliveryService } from './courier-delivery.service';

describe('CourierDeliveryService proof of delivery', () => {
  let service: CourierDeliveryService;
  let http: HttpTestingController;
  const request = {
    recipientName: 'Kamala Silva',
    notes: null,
    mediaUrl: null,
    deliveredAt: null,
    collectedAmount: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CourierDeliveryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends proof without a file as plain JSON', () => {
    service.recordProof('assignment-1', request).subscribe();

    const call = http.expectOne('/api/v1/assignments/assignment-1/proof');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    call.flush({});
  });

  it('sends proof with a file as a form: the details as JSON, then the file under its own name', async () => {
    const file = new File([new Uint8Array([1, 2, 3])], 'front-door.jpg', { type: 'image/jpeg' });

    service.recordProof('assignment-1', request, file).subscribe();

    const call = http.expectOne('/api/v1/assignments/assignment-1/proof');
    const form = call.request.body as FormData;
    expect(form).toBeInstanceOf(FormData);
    const details = form.get('proof') as Blob;
    expect(details.type).toBe('application/json');
    expect(JSON.parse(await details.text())).toEqual(request);
    const sent = form.get('file') as File;
    expect(sent.name).toBe('front-door.jpg');
    expect(sent.size).toBe(3);
    call.flush({});
  });

  it('reads the proof file back as a blob from the owner-only address', () => {
    let received: Blob | null = null;

    service.getProofFile('assignment-1').subscribe((blob) => (received = blob));

    const call = http.expectOne('/api/v1/assignments/assignment-1/proof/file');
    expect(call.request.method).toBe('GET');
    expect(call.request.responseType).toBe('blob');
    call.flush(new Blob(['x'], { type: 'image/jpeg' }));
    expect(received).toBeInstanceOf(Blob);
  });

  it('puts a safe identifier in the address even for a hostile id', () => {
    service.getProofFile('a/b?c').subscribe();

    http.expectOne((r) => r.url.startsWith('/api/v1/assignments/') && !r.url.includes('a/b')).flush(new Blob());
  });
});
