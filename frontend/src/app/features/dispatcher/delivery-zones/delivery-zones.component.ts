import { Component, signal, computed, ViewChild, ElementRef } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormControl } from '@angular/forms';
import { DeliveryZoneService, Zone } from '../services/delivery-zone.service';

/**
 * Delivery Zone Management page for Dispatchers.
 * Implements view, search, filter, add, edit and status toggle.
 * UI follows the global style tokens defined in style.md.
 */
@Component({
  selector: 'app-delivery-zones',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './delivery-zones.component.html',
  styleUrls: ['./delivery-zones.component.scss'],
})
export class DeliveryZonesComponent {
  // State signals
  zones = signal<Zone[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  // Search / filter controls
  searchCtrl: FormControl = new FormControl('');
  statusCtrl: FormControl = new FormControl('ALL');
  // Control values as signals, so the computed list below re-runs when the filters change.
  private readonly searchTerm = toSignal(this.searchCtrl.valueChanges, { initialValue: this.searchCtrl.value });
  private readonly statusFilter = toSignal(this.statusCtrl.valueChanges, { initialValue: this.statusCtrl.value });

  // Form for add/edit modal (shared)
  zoneForm: FormGroup;
  isEditMode = false;
  editingZoneId: string | null = null;

  @ViewChild('zoneDialog') zoneDialog!: ElementRef<HTMLDialogElement>;

  // Derived filtered list
  filteredZones = computed(() => {
    const term = this.searchTerm()?.trim().toLowerCase() ?? '';
    const status = this.statusFilter();
    return this.zones()
      .filter(z => {
        const matchesTerm =
          z.code.toLowerCase().includes(term) ||
          z.name.toLowerCase().includes(term) ||
          (z.description?.toLowerCase().includes(term) ?? false);
        const matchesStatus = status === 'ALL' || z.status === status;
        return matchesTerm && matchesStatus;
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  constructor(private fb: FormBuilder, private service: DeliveryZoneService) {
    this.zoneForm = this.fb.group({
      code: ['', [Validators.required, Validators.maxLength(10)]],
      name: ['', [Validators.required, Validators.maxLength(50)]],
      description: [''],
      status: ['ACTIVE'],
    });
    this.loadZones();
  }

  loadZones() {
    this.loading.set(true);
    this.error.set(null);
    this.service.getZones().subscribe({
      next: data => this.zones.set(data),
      error: () => {
        this.error.set('Failed to load zones');
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  openAddModal() {
    this.isEditMode = false;
    this.editingZoneId = null;
    this.zoneForm.reset({ status: 'ACTIVE' });
    this.zoneDialog.nativeElement.showModal();
  }

  openEditModal(zone: Zone) {
    this.isEditMode = true;
    this.editingZoneId = zone.id;
    this.zoneForm.patchValue({
      code: zone.code,
      name: zone.name,
      description: zone.description,
      status: zone.status,
    });
    this.zoneDialog.nativeElement.showModal();
  }

  submitZone() {
    if (this.zoneForm.invalid) return;
    const payload = this.zoneForm.value;
    if (this.isEditMode && this.editingZoneId) {
      this.service.updateZone(this.editingZoneId, payload).subscribe({
        next: () => this.afterSave(),
        error: () => alert('Update failed'),
      });
    } else {
      this.service.createZone(payload).subscribe({
        next: () => this.afterSave(),
        error: () => alert('Create failed'),
      });
    }
  }

  afterSave() {
    this.zoneDialog.nativeElement.close();
    this.loadZones();
  }

  closeDialog(): void {
    this.zoneDialog.nativeElement.close();
  }

  toggleStatus(zone: Zone) {
    const newStatus = zone.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (!confirm(`Are you sure you want ${newStatus === 'ACTIVE' ? 'activate' : 'deactivate'} this zone?`)) return;
    this.service.changeStatus(zone.id, newStatus).subscribe({
      next: () => this.loadZones(),
      error: () => alert('Status change failed'),
    });
  }
}
