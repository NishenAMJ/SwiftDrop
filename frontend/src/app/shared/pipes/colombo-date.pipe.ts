import { Pipe, PipeTransform } from '@angular/core';

const FORMATS = {
  dateTime: new Intl.DateTimeFormat('en-LK', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Colombo',
  }),
  date: new Intl.DateTimeFormat('en-LK', { dateStyle: 'medium', timeZone: 'Asia/Colombo' }),
};

/**
 * Formats an ISO timestamp from the API in Sri Lanka time (frontend/style.md section 9), so every
 * user sees the same date whatever their device is set to. Empty values become an empty string.
 */
@Pipe({ name: 'colomboDate' })
export class ColomboDatePipe implements PipeTransform {
  transform(value: string | null | undefined, format: keyof typeof FORMATS = 'dateTime'): string {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : FORMATS[format].format(date);
  }
}
