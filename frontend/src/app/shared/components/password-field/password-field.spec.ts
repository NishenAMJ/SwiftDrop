import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { PasswordField } from './password-field';

@Component({
  imports: [PasswordField, ReactiveFormsModule],
  template: `
    <label for="pw">Password</label>
    <app-password-field label="new password">
      <input id="pw" class="sd-input" type="password" [formControl]="password" autocomplete="new-password" />
    </app-password-field>
  `,
})
class Host {
  readonly password = new FormControl('Secret-123', { nonNullable: true });
}

describe('PasswordField', () => {
  it('shows and hides the typed password without touching the value', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement;
    const input = root.querySelector('input')!;
    const toggle = root.querySelector('button')!;

    expect(input.type).toBe('password');
    expect(toggle.type).toBe('button');
    expect(toggle.getAttribute('aria-controls')).toBe('pw');
    expect(toggle.textContent?.trim()).toBe('Show new password');

    toggle.click();
    await fixture.whenStable();
    expect(input.type).toBe('text');
    expect(toggle.textContent?.trim()).toBe('Hide new password');
    expect(input.value).toBe('Secret-123');
    expect(input.autocomplete).toBe('new-password');

    toggle.click();
    await fixture.whenStable();
    expect(input.type).toBe('password');
  });
});
