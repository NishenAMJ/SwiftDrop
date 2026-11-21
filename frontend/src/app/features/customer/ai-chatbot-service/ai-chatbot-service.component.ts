import { CommonModule, DatePipe } from '@angular/common';
import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AiChatbotService } from './ai-chatbot.service';

interface ChatMessage {
  id: number;
  text: string;
  author: 'user' | 'assistant';
  timestamp: Date;
}

@Component({
  selector: 'app-ai-chatbot-service',
  imports: [CommonModule, DatePipe, FormsModule],
  templateUrl: './ai-chatbot-service.component.html',
  styleUrl: './ai-chatbot-service.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiChatbotServiceComponent implements AfterViewChecked {
  private readonly chatbotService = inject(AiChatbotService);
  private nextMessageId = 1;
  private shouldScroll = false;

  @ViewChild('messageList') private messageList?: ElementRef<HTMLElement>;

  readonly isOpen = signal(false);
  readonly draft = signal('');
  readonly messages = signal<ChatMessage[]>([]);
  readonly isTyping = signal(false);
  readonly validationMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly lastFailedMessage = signal<string | null>(null);
  readonly currentTime = new Date();

  readonly quickActions = [
    'Track My Parcel',
    'Booking a Parcel',
    'Payment Methods',
    'Delivery Information',
    'Become a Merchant',
  ];

  ngAfterViewChecked(): void {
    if (this.shouldScroll && this.messageList) {
      this.messageList.nativeElement.scrollTop = this.messageList.nativeElement.scrollHeight;
      this.shouldScroll = false;
    }
  }

  toggleChat(): void {
    this.isOpen.update((open) => !open);
    if (this.isOpen() && this.messages().length === 0) {
      this.shouldScroll = true;
    }
  }

  closeChat(): void {
    this.isOpen.set(false);
  }

  sendQuickAction(action: string): void {
    this.sendMessage(action);
  }

  sendMessage(message = this.draft()): void {
    const trimmedMessage = message.trim();
    if (!trimmedMessage || this.isTyping()) {
      this.validationMessage.set('Please enter a message before sending.');
      return;
    }

    this.validationMessage.set(null);
    this.errorMessage.set(null);
    this.lastFailedMessage.set(null);
    this.draft.set('');
    this.addMessage(trimmedMessage, 'user');
    this.isTyping.set(true);

    this.chatbotService.getResponse(trimmedMessage).subscribe({
      next: (response) => {
        this.isTyping.set(false);
        this.addMessage(response, 'assistant');
      },
      error: () => {
        this.isTyping.set(false);
        this.errorMessage.set('The assistant is temporarily unavailable. Please try again.');
        this.lastFailedMessage.set(trimmedMessage);
        this.shouldScroll = true;
      },
    });
  }

  retryLastMessage(): void {
    const failedMessage = this.lastFailedMessage();
    if (failedMessage) {
      this.sendMessage(failedMessage);
    }
  }

  onDraftChange(value: string): void {
    this.draft.set(value);
    if (value.trim()) {
      this.validationMessage.set(null);
    }
  }

  private addMessage(text: string, author: ChatMessage['author']): void {
    this.messages.update((messages) => [
      ...messages,
      {
        id: this.nextMessageId++,
        text,
        author,
        timestamp: new Date(),
      },
    ]);
    this.shouldScroll = true;
  }
}
