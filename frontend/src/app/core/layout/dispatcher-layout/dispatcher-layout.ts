import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Minimal layout for Dispatcher role.
 * It provides a container for child routes via <router-outlet>.
 * If a sidebar is added later, it can be included here.
 */
@Component({
  selector: 'app-dispatcher-layout',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './dispatcher-layout.html',
  styleUrls: ['./dispatcher-layout.scss'],
})
export class DispatcherLayoutComponent {}