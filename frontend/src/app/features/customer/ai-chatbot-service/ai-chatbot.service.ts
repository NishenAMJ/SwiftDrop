import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';

interface ChatResponse {
  answer: string;
}

@Injectable({ providedIn: 'root' })
export class AiChatbotService {
  private readonly http = inject(HttpClient);

  getResponse(message: string): Observable<string> {
    return this.http
      .post<ChatResponse>(`/api/v1/ai/chat?message=${encodeURIComponent(message)}`, {})
      .pipe(
        map((response) => response.answer),
        catchError((error: unknown) => {
          if (error instanceof HttpErrorResponse) {
            const answer = typeof error.error?.answer === 'string'
              ? error.error.answer
              : 'The assistant is temporarily unavailable. Please try again.';
            return throwError(() => new Error(answer));
          }
          return throwError(() => new Error('The assistant is temporarily unavailable. Please try again.'));
        }),
      );
  }
}
