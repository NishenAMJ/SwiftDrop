import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CustomerProfileService } from '../services/customer-profile.service';
import { CustomerBookingService } from '../services/customer-booking.service';
import { UpdateProfileRequest } from '../models/customer-profile.models';

@Component({
  selector: 'app-customer-account',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './customer-account.component.html',
  styleUrl: './customer-account.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerAccountComponent implements OnInit {
  private readonly profileService = inject(CustomerProfileService);
  private readonly bookingService = inject(CustomerBookingService);
  private readonly fb = inject(FormBuilder);

  readonly profile = this.profileService.profile;
  readonly isEditModalOpen = signal<boolean>(false);
  readonly saving = signal<boolean>(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  // Profile image upload preview
  readonly avatarPreview = signal<string | null>(null);
  readonly imageError = signal<string | null>(null);

  // Total bookings count for stats
  readonly totalBookings = signal<number>(0);

  editForm!: FormGroup;

  ngOnInit(): void {
    this.initForm();
    this.bookingService.getMyBookings().subscribe({
      next: (bookings) => this.totalBookings.set(bookings.length),
      error: () => {},
    });
  }

  initForm(): void {
    const p = this.profile();
    this.avatarPreview.set(p.avatarBase64 || null);

    this.editForm = this.fb.group({
      fullName: [
        p.fullName || '',
        [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
      ],
      // Validates standard email and specifically ensures proper Gmail/domain formatting
      email: [
        p.email || '',
        [
          Validators.required,
          Validators.email,
          Validators.pattern(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/),
        ],
      ],
      // Contact number validator: matches standard Sri Lankan formats (e.g. +94 77 123 4567, 0771234567, +94771234567)
      phoneNumber: [
        p.phoneNumber || '',
        [
          Validators.required,
          Validators.pattern(/^(\+?94\s?7\d{8}|07\d{8}|\+?94\s?\d{2}\s?\d{7}|0\d{9})$/),
        ],
      ],
      streetAddress: [p.streetAddress || '', [Validators.maxLength(255)]],
      city: [p.city || '', [Validators.maxLength(100)]],
      postalCode: [p.postalCode || '', [Validators.maxLength(20)]],
    });
  }

  openEditModal(): void {
    this.initForm();
    this.imageError.set(null);
    this.errorMessage.set(null);
    this.isEditModalOpen.set(true);
  }

  closeEditModal(): void {
    this.isEditModalOpen.set(false);
  }

  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    this.imageError.set(null);

    // Validate image format
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      this.imageError.set('Please select a valid image file (JPG, PNG, or WebP).');
      return;
    }

    // Validate size (max 2 MB for database storage)
    if (file.size > 2 * 1024 * 1024) {
      this.imageError.set('Image size exceeds 2 MB limit. Please select a smaller photo.');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const base64 = reader.result as string;
      this.avatarPreview.set(base64);
    };
    reader.onerror = () => {
      this.imageError.set('Failed to read image file. Please try again.');
    };
    reader.readAsDataURL(file);
  }

  removeAvatar(): void {
    this.avatarPreview.set(null);
  }

  saveProfile(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      this.errorMessage.set('Please correct the validation errors in the form.');
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    const fv = this.editForm.value;
    const request: UpdateProfileRequest = {
      fullName: fv.fullName.trim(),
      email: fv.email.trim(),
      phoneNumber: fv.phoneNumber.trim(),
      streetAddress: fv.streetAddress?.trim() || undefined,
      city: fv.city?.trim() || undefined,
      postalCode: fv.postalCode?.trim() || undefined,
      avatarBase64: this.avatarPreview() || undefined,
    };

    this.profileService.updateProfile(request).subscribe({
      next: () => {
        this.saving.set(false);
        this.isEditModalOpen.set(false);
        this.successMessage.set('Customer profile and picture saved to database successfully!');
        setTimeout(() => this.successMessage.set(null), 4000);
      },
      error: (err) => {
        console.error('Failed to update profile:', err);
        this.saving.set(false);
        this.errorMessage.set('Could not save profile details. Please try again.');
      },
    });
  }

  isFieldInvalid(name: string): boolean {
    const c = this.editForm.get(name);
    return !!(c && c.invalid && (c.dirty || c.touched));
  }
}

