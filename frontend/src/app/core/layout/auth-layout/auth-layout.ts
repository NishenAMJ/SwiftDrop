import { ChangeDetectionStrategy, Component, DOCUMENT, DestroyRef, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Frame for login, register and the Google callback: the Arctic Frost background with one opaque
 * card column (frontend/style.md sections 5 and 6). Puts `sd-auth-page` on <body> only while shown.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  templateUrl: './auth-layout.html',
  styleUrl: './auth-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthLayout {
  constructor() {
    const body = inject(DOCUMENT).body;
    body.classList.add('sd-auth-page');
    inject(DestroyRef).onDestroy(() => body.classList.remove('sd-auth-page'));
  }
}
