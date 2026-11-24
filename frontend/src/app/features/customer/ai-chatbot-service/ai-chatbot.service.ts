import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AiChatbotService {
  getResponse(message: string): Observable<string> {
    const normalizedMessage = message.toLowerCase();
    let response =
      'I can help with parcel tracking, bookings, payments, delivery information, and merchant applications. What would you like to know?';

    if (normalizedMessage.includes('track')) {
      response =
        'To track a parcel, open My Bookings and select the booking you want to follow. You can also use the tracking reference from your receipt.';
    } else if (normalizedMessage.includes('book')) {
      response =
        'You can book a parcel from the Book Parcel page. Enter the pickup and delivery details, parcel weight, and preferred delivery urgency before confirming.';
    } else if (normalizedMessage.includes('payment')) {
      response =
        'Cash on delivery is available. Online payments are currently unavailable. Your payment status is shown on the booking receipt.';
    } else if (normalizedMessage.includes('deliver')) {
      response =
        'Delivery updates appear in your booking history as the parcel moves through pickup, transit, and delivery. The latest status is shown on each booking.';
    } else if (normalizedMessage.includes('merchant')) {
      response =
        'To become a merchant, open Account & roles and choose the merchant application option. You can review your application status there.';
    } else if (normalizedMessage.includes('hello') || normalizedMessage.includes('hi')) {
      response = 'Hello! How can I help with your SwiftDrop delivery today?';
    }

    return of(response).pipe(delay(700));
  }
}
