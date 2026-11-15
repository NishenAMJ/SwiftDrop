import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthUser } from '../../../core/auth/auth.models';
import { exampleUser, problem } from '../../../testing/auth-fixtures';
import { UsersPage } from './users-page';

const isList = (r: { url: string; method: string }) => r.method === 'GET' && r.url === '/api/v1/users';

function pageOf(content: AuthUser[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

describe('UsersPage', () => {
  let fixture: ComponentFixture<UsersPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [UsersPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UsersPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function type(id: string, value: string) {
    const input = page.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function button(text: string): HTMLButtonElement {
    return [...page.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
  }

  async function openForm() {
    button('Add a dispatcher').click();
    await fixture.whenStable();
  }

  async function fillAndSubmit() {
    type('dispatcher-fullName', 'Sunil Fernando');
    type('dispatcher-email', 'sunil@swiftdrop.lk');
    type('dispatcher-initialPassword', 'Start-pass-1');
    page.querySelector<HTMLFormElement>('#new-dispatcher form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('lists users with roles and status as text', async () => {
    http.expectOne(isList).flush(
      pageOf([
        { ...exampleUser, roles: ['CUSTOMER', 'MERCHANT'], authProviders: ['PASSWORD', 'GOOGLE'] },
        { ...exampleUser, id: 'd', fullName: 'New Dispatcher', roles: ['DISPATCHER'], mustChangePassword: true },
      ]),
    );
    await fixture.whenStable();

    const rows = [...page.querySelectorAll('tbody tr')];
    expect(rows[0].textContent).toContain('Customer');
    expect(rows[0].textContent).toContain('Merchant');
    expect(rows[0].textContent).toContain('Password, Google');
    expect(rows[0].textContent).toContain('Active');
    expect(rows[1].textContent).toContain('Must change password');
  });

  it('searches with the chosen filters', async () => {
    http.expectOne(isList).flush(pageOf([]));
    type('users-filter-q', ' nimal ');
    const role = page.querySelector<HTMLSelectElement>('#users-filter-role')!;
    role.value = 'COURIER';
    role.dispatchEvent(new Event('change'));
    page.querySelector('form[role="search"]')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = http.expectOne(isList);
    expect(request.request.params.get('q')).toBe('nimal');
    expect(request.request.params.get('role')).toBe('COURIER');
    expect(request.request.params.has('status')).toBe(false);
    request.flush(pageOf([]));
    await fixture.whenStable();
    expect(page.textContent).toContain('No users match these filters');
  });

  it('creates a dispatcher account and explains the initial password', async () => {
    http.expectOne(isList).flush(pageOf([]));
    await openForm();
    expect(button('Cancel adding a dispatcher').getAttribute('aria-expanded')).toBe('true');
    await fillAndSubmit();

    const request = http.expectOne('/api/v1/dispatchers');
    expect(request.request.body).toEqual({
      fullName: 'Sunil Fernando',
      email: 'sunil@swiftdrop.lk',
      phoneNumber: undefined,
      initialPassword: 'Start-pass-1',
    });
    request.flush(
      { ...exampleUser, fullName: 'Sunil Fernando', roles: ['DISPATCHER'], mustChangePassword: true },
      { status: 201, statusText: 'Created' },
    );
    http.expectOne(isList).flush(pageOf([]));
    await fixture.whenStable();

    expect(page.querySelector('#new-dispatcher form')).toBeNull();
    expect(page.querySelector('[role="status"]')?.textContent).toContain(
      'Dispatcher account created for Sunil Fernando',
    );
  });

  it('marks an email that is already registered', async () => {
    http.expectOne(isList).flush(pageOf([]));
    await openForm();
    await fillAndSubmit();

    http
      .expectOne('/api/v1/dispatchers')
      .flush(problem('EMAIL_ALREADY_REGISTERED', { status: 409 }), { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(page.querySelector('#dispatcher-email')!.getAttribute('aria-invalid')).toBe('true');
    expect(page.querySelector('#dispatcher-email-error')?.textContent).toContain('already exists');
  });

  it('checks the password rule before sending', async () => {
    http.expectOne(isList).flush(pageOf([]));
    await openForm();
    type('dispatcher-fullName', 'Sunil Fernando');
    type('dispatcher-email', 'sunil@swiftdrop.lk');
    type('dispatcher-initialPassword', 'onlyletters');
    page.querySelector<HTMLFormElement>('#new-dispatcher form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectNone('/api/v1/dispatchers');
    expect(page.querySelector('#dispatcher-initialPassword-error')?.textContent).toContain(
      'at least one letter and one number',
    );
  });
});
