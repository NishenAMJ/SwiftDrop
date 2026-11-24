import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { CourierCategory, DeliveryPage } from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';
import { CourierCategoriesPage } from './courier-categories-page';

const bike: CourierCategory = {
  id: 'cat-1',
  code: 'MOTORCYCLE',
  name: 'Motorcycle',
  description: 'Small parcels',
  maxWeightKg: 20,
  status: 'ACTIVE',
  createdAt: '2026-11-20T09:00:00Z',
  updatedAt: '2026-11-20T09:00:00Z',
};
const page: DeliveryPage<CourierCategory> = {
  content: [bike],
  totalElements: 1,
  totalPages: 1,
  size: 100,
  number: 0,
  first: true,
  last: true,
  empty: false,
};

describe('CourierCategoriesPage', () => {
  let fixture: ComponentFixture<CourierCategoriesPage>;
  let service: {
    listCategories: ReturnType<typeof vi.fn>;
    createCategory: ReturnType<typeof vi.fn>;
    changeCategoryStatus: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    service = {
      listCategories: vi.fn().mockReturnValue(of(page)),
      createCategory: vi.fn().mockReturnValue(of(bike)),
      changeCategoryStatus: vi.fn().mockReturnValue(of({ ...bike, status: 'INACTIVE' })),
    };
    await TestBed.configureTestingModule({
      imports: [CourierCategoriesPage],
      providers: [{ provide: CourierDeliveryService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(CourierCategoriesPage);
    fixture.detectChanges();
  });

  it('lists categories with their status as text', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Motorcycle');
    expect(text).toContain('MOTORCYCLE');
    expect(text).toContain('Active');
  });

  it('creates a category with a normalised code and no weight limit when blank', () => {
    const component = fixture.componentInstance as any;
    component.form.setValue({ code: ' van ', name: ' Van ', description: '', maxWeightKg: '' });

    component.create();

    expect(service.createCategory).toHaveBeenCalledWith({
      code: 'VAN',
      name: 'Van',
      description: null,
      maxWeightKg: null,
    });
  });

  it('rejects a zero or non-numeric weight', () => {
    const component = fixture.componentInstance as any;
    component.form.setValue({ code: 'VAN', name: 'Van', description: '', maxWeightKg: '0' });

    component.create();

    expect(service.createCategory).not.toHaveBeenCalled();
  });

  it('deactivates an active category', () => {
    (fixture.componentInstance as any).toggle(bike);
    expect(service.changeCategoryStatus).toHaveBeenCalledWith('cat-1', 'INACTIVE');
  });
});
