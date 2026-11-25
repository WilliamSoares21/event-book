import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { EventsService } from './events';

// SHA-256 de "abc" em Base64
const sha256Abc = 'ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=';

describe('EventsService.uploadEventImage', () => {
  let service: EventsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EventsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('faz o PUT no R2 com os headers assinados e sem JWT', async () => {
    const file = new File(['abc'], 'capa.png', { type: 'image/png' });
    const publicUrl = new Promise<string>((resolve, reject) =>
      service.uploadEventImage('evt-1', file).subscribe({ next: resolve, error: reject }),
    );

    const presign = await vi.waitFor(() => http.expectOne(`${environment.apiUrl}/events/evt-1/images/upload-url`));
    expect(presign.request.body.contentType).toBe('image/png');
    expect(presign.request.body.sha256Base64).toBe(sha256Abc);
    expect(presign.request.body.sizeBytes).toBe(3);
    const writeId = presign.request.body.writeId;
    presign.flush({ uploadUrl: 'https://r2.example/put', objectKey: 'k.png', publicUrl: 'https://cdn.example/k.png', expiresAt: '' });

    const put = await vi.waitFor(() => http.expectOne('https://r2.example/put'));
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.get('Content-Type')).toBe('image/png');
    expect(put.request.headers.get('x-amz-checksum-sha256')).toBe(sha256Abc);
    expect(put.request.headers.get('x-amz-meta-write-id')).toBe(writeId);
    expect(put.request.headers.has('Authorization')).toBe(false);
    put.flush(null);

    const confirm = await vi.waitFor(() => http.expectOne(`${environment.apiUrl}/events/evt-1/images/confirm`));
    expect(confirm.request.body).toEqual({ objectKey: 'k.png', writeId, sha256Base64: sha256Abc, sizeBytes: 3 });
    confirm.flush({});

    await expect(publicUrl).resolves.toBe('https://cdn.example/k.png');
  });
});
