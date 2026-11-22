import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { DeliveryZoneService, Zone } from '../services/delivery-zone.service';
import { DeliveryZonesComponent } from './delivery-zones.component';

describe('DeliveryZonesComponent', () => {
  const zones: Zone[] = [
    { id: 'z2', code: 'KDY', name: 'Kandy', description: 'Hill country', status: 'INACTIVE' },
    { id: 'z1', code: 'CMB', name: 'Colombo', status: 'ACTIVE' },
  ];

  function setup(load: () => Observable<Zone[]> = () => of(zones)) {
    const api = {
      getZones: vi.fn(load),
      createZone: vi.fn(() => of({})),
      updateZone: vi.fn(() => of({})),
      changeStatus: vi.fn(() => of({})),
    };
    TestBed.configureTestingModule({
      imports: [DeliveryZonesComponent],
      providers: [{ provide: DeliveryZoneService, useValue: api }],
    });
    const fixture = TestBed.createComponent(DeliveryZonesComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.zoneDialog.nativeElement.showModal = vi.fn();
    component.zoneDialog.nativeElement.close = vi.fn();
    return { fixture, component, api };
  }

  it('lists zones alphabetically by name', () => {
    const { component } = setup();
    expect(component.filteredZones().map((z) => z.code)).toEqual(['CMB', 'KDY']);
  });

  it('re-filters by search text and status', () => {
    const { component } = setup();
    component.searchCtrl.setValue('hill');
    expect(component.filteredZones().map((z) => z.code)).toEqual(['KDY']);
    component.searchCtrl.setValue('');
    component.statusCtrl.setValue('ACTIVE');
    expect(component.filteredZones().map((z) => z.code)).toEqual(['CMB']);
  });

  it('stops loading and shows the error when zones cannot be loaded', () => {
    const { component, fixture } = setup(() => throwError(() => ({ status: 503 })));
    fixture.detectChanges();
    expect(component.error()).toBe('Failed to load zones');
    expect(component.loading()).toBe(false);
  });

  it('validates the zone form before saving', () => {
    const { component, api } = setup();
    component.openAddModal();
    component.zoneForm.patchValue({ code: 'TOO-LONG-CODE', name: 'X' });
    component.submitZone();
    component.zoneForm.patchValue({ code: '', name: 'X' });
    component.submitZone();
    expect(api.createZone).not.toHaveBeenCalled();

    component.zoneForm.patchValue({ code: 'GLL', name: 'Galle' });
    component.submitZone();
    expect(api.createZone).toHaveBeenCalledWith(expect.objectContaining({ code: 'GLL', name: 'Galle', status: 'ACTIVE' }));
    expect(api.getZones).toHaveBeenCalledTimes(2);
  });

  it('updates the zone being edited', () => {
    const { component, api } = setup();
    component.openEditModal(zones[1]);
    component.zoneForm.patchValue({ name: 'Colombo Metro' });
    component.submitZone();
    expect(api.updateZone).toHaveBeenCalledWith('z1', expect.objectContaining({ name: 'Colombo Metro' }));
  });

  it('only changes status after the dispatcher confirms', () => {
    const { component, api } = setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    component.toggleStatus(zones[1]);
    expect(api.changeStatus).not.toHaveBeenCalled();
    component.toggleStatus(zones[1]);
    expect(api.changeStatus).toHaveBeenCalledWith('z1', 'INACTIVE');
    expect(confirmSpy).toHaveBeenCalledTimes(2);
  });
});
