import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { CancellationEligibility } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';

@Component({
 selector: 'app-cancel-booking', standalone: true, imports: [CommonModule, ConfirmDialog],
 templateUrl: './cancel-booking.component.html', styleUrl: './cancel-booking.component.scss'
})
export class CancelBookingComponent implements OnInit, OnDestroy {
 @Input({required:true}) bookingId!: string;
 @Output() cancelled=new EventEmitter<string>();
 @Output() closed=new EventEmitter<void>();
 private readonly service=inject(CustomerBookingService);
 private readonly destroy$=new Subject<void>();
 private requestKey='';
 readonly eligibility=signal<CancellationEligibility | null>(null);
 readonly step=signal<'details' | 'confirmation' | 'payment'>('details');
 readonly loading=signal(true);
 readonly submitting=signal(false);
 readonly error=signal<string | null>(null);
 readonly paymentMessage=signal('');
 readonly booking=computed(()=>this.eligibility()?.booking ?? null);
 readonly canConfirm=computed(()=>{
  const e=this.eligibility();
  return e?.eligible === true && e.cancellationFee != null && e.cancellationFee > 0 &&
   (e.booking?.paymentMethod === 'COD' || (e.booking?.paymentMethod === 'ONLINE' &&
    e.originalPaidAmount != null && e.refundableAmount != null && e.refundableAmount >= 0));
 });
 readonly title=computed(()=>this.step()==='details'?'Cancel booking':this.step()==='confirmation'?'Confirm cancellation':'Payment processing');
 readonly confirmLabel=computed(()=>this.step()==='details'?'Review cancellation':this.step()==='confirmation'?
  (this.booking()?.paymentMethod==='COD'?'Confirm and continue to payment':'Confirm and request refund'):'Close');
 ngOnInit(): void { this.load(); }
 ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
 load(): void {
  if(this.loading() && this.eligibility()) return;
  this.loading.set(true); this.error.set(null); this.eligibility.set(null);
  this.service.getCancellationEligibility(this.bookingId).pipe(takeUntil(this.destroy$)).subscribe({
   next: result=>{ this.eligibility.set(result); this.loading.set(false); },
   error: ()=>{ this.error.set('Could not verify cancellation eligibility. Please retry.'); this.loading.set(false); }
  });
 }
 advance(): void {
  if(this.loading() || this.submitting()) return;
  if(this.step()==='details') { if(this.eligibility()) this.step.set('confirmation'); return; }
  if(this.step()==='payment') { this.closed.emit(); return; }
  if(!this.canConfirm()) return;
  this.submitting.set(true); this.error.set(null);
  // Keep the same key across uncertain requests and reloads; never create a second charge on retry.
  const storageKey='swiftdrop:cancellation:'+this.bookingId;
  try {
   this.requestKey=localStorage.getItem(storageKey) || crypto.randomUUID();
   localStorage.setItem(storageKey,this.requestKey);
  } catch { this.requestKey ||= crypto.randomUUID(); }
  this.service.initiateCancellation(this.bookingId,this.requestKey).pipe(takeUntil(this.destroy$)).subscribe({
   next: ()=>{
    this.submitting.set(false); this.step.set('payment');
    this.paymentMessage.set(this.booking()?.paymentMethod==='COD'?
     'Your request has been submitted. Waiting for verified payment instructions. Your booking is not yet cancelled.':
     'Your refund request has been submitted. Waiting for verified processing status. Your booking is not yet cancelled.');
   },
   error: ()=>{ this.submitting.set(false); this.error.set('Cancellation could not proceed. Check eligibility again before retrying. No completed cancellation has been confirmed.'); }
  });
 }
 back(): void {
  if(this.submitting()) return;
  if(this.step()==='confirmation') { this.step.set('details'); this.error.set(null); }
  else this.closed.emit();
 }
}
