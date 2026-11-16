import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Public home page for signed-out visitors (signed-in users are sent to their own start page by
 * guestGuard). Arctic Frost only behind the hero; the rest uses the plain page background so no
 * text sits on the coloured gradient (frontend/style.md sections 1 and 5).
 */
@Component({
  selector: 'app-landing-page',
  imports: [RouterLink],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingPage {
  protected readonly year = new Date().getFullYear();
}
