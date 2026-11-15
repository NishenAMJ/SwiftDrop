import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Previous / next paging for lists from the API (pages start at 0, shown from 1). */
@Component({
  selector: 'app-pagination',
  template: `
    @if (totalPages() > 1) {
      <nav class="pagination" [attr.aria-label]="label()">
        <button
          class="sd-button sd-button--secondary"
          type="button"
          [disabled]="page() === 0 || disabled()"
          (click)="pageChange.emit(page() - 1)"
        >
          Previous<span class="sd-visually-hidden"> page</span>
        </button>
        <span class="pagination__status sd-tabular" aria-live="polite">
          Page {{ page() + 1 }} of {{ totalPages() }}
        </span>
        <button
          class="sd-button sd-button--secondary"
          type="button"
          [disabled]="page() + 1 >= totalPages() || disabled()"
          (click)="pageChange.emit(page() + 1)"
        >
          Next<span class="sd-visually-hidden"> page</span>
        </button>
      </nav>
    }
  `,
  styles: `
    .pagination {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sd-space-3);
    }

    .pagination__status {
      font-size: 0.875rem;
      color: var(--sd-text-secondary);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly label = input('Pages');
  readonly disabled = input(false);
  readonly pageChange = output<number>();
}
