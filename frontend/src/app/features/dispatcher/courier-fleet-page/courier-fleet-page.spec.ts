import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Page } from '../../../core/auth/auth.models';
import { Courier as ApprovedCourier } from '../../courier/courier.models';
import {
  CourierCategory,
  CourierProfile,
  DeliveryPage,
} from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';
import { DispatcherService } from '../dispatcher.service';
import { CourierFleetPage } from './courier-fleet-page';

const category = { id: 'cat-1', code: 'MOTORCYCLE', name: 'Motorcycle', status: 'ACTIVE' } as CourierCategory;
const nimal = {
  userId: 'user-1',
  fullName: 'Nimal Perera',
  vehicleType: 'MOTORCYCLE',
  vehicleRegistrationNumber: 'WP-1234',
  status: 'APPROVED',
} as ApprovedCourier;
const kamala = { userId: 'user-2', fullName: 'Kamala Silva', status: 'APPROVED' } as ApprovedCourier;
const registered = {
  id: 'courier-1',
  identityUserId: 'user-2',
  fullName: 'Kamala Silva',
  phone: '+94 77 000 0000',
  vehicleNumber: null,
  categoryId: 'cat-1',
  categoryCode: 'MOTORCYCLE',
  categoryName: 'Motorcycle',
  status: 'AVAILABLE',
} as CourierProfile;

function deliveryPage<T>(content: T[]): DeliveryPage<T> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 100,
    number: 0,
    first: true,
    last: true,
    empty: content.length === 0,
  };
}

describe('CourierFleetPage', () => {
  let fixture: ComponentFixture<CourierFleetPage>;
  let delivery: {
    listCategories: ReturnType<typeof vi.fn>;
    listCouriers: ReturnType<typeof vi.fn>;
    createCourier: ReturnType<typeof vi.fn>;
    changeCourierStatus: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    delivery = {
      listCategories: vi.fn().mockReturnValue(of(deliveryPage([category]))),
      listCouriers: vi.fn().mockReturnValue(of(deliveryPage([registered]))),
      createCourier: vi.fn().mockReturnValue(of({ ...registered, id: 'courier-2', fullName: 'Nimal Perera' })),
      changeCourierStatus: vi.fn().mockReturnValue(of({ ...registered, status: 'OFF_DUTY' })),
    };
    const dispatcher = {
      listCouriers: vi
        .fn()
        .mockReturnValue(
          of({ content: [nimal, kamala], page: 0, size: 100, totalElements: 2, totalPages: 1 } satisfies Page<ApprovedCourier>),
        ),
    };
    await TestBed.configureTestingModule({
      imports: [CourierFleetPage],
      providers: [
        { provide: CourierDeliveryService, useValue: delivery },
        { provide: DispatcherService, useValue: dispatcher },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CourierFleetPage);
    fixture.detectChanges();
  });

  it('offers only approved couriers that are not set up yet', () => {
    const component = fixture.componentInstance as any;
    expect(component.unregistered().map((c: ApprovedCourier) => c.userId)).toEqual(['user-1']);
    expect(fixture.nativeElement.textContent).toContain('Kamala Silva');
  });

  it('sets up the picked courier with the category and phone', () => {
    const component = fixture.componentInstance as any;
    component.form.setValue({
      identityUserId: 'user-1',
      phone: ' +94 77 123 4567 ',
      vehicleNumber: '',
      categoryId: 'cat-1',
    });

    component.register();

    expect(delivery.createCourier).toHaveBeenCalledWith({
      identityUserId: 'user-1',
      fullName: 'Nimal Perera',
      phone: '+94 77 123 4567',
      vehicleNumber: null,
      categoryId: 'cat-1',
    });
  });

  it('prefills the vehicle number from the approved application', () => {
    const component = fixture.componentInstance as any;
    component.form.controls.identityUserId.setValue('user-1');
    component.onCourierPicked();
    expect(component.form.controls.vehicleNumber.value).toBe('WP-1234');
  });

  it('changes availability only when the status differs', () => {
    const component = fixture.componentInstance as any;
    component.changeStatus(registered, 'AVAILABLE');
    expect(delivery.changeCourierStatus).not.toHaveBeenCalled();

    component.changeStatus(registered, 'OFF_DUTY');
    expect(delivery.changeCourierStatus).toHaveBeenCalledWith('courier-1', 'OFF_DUTY');
  });
});
