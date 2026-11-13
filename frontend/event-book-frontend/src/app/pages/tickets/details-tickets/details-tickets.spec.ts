import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetailsTickets } from './details-tickets';

describe('DetailsTickets', () => {
  let component: DetailsTickets;
  let fixture: ComponentFixture<DetailsTickets>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetailsTickets],
    }).compileComponents();

    fixture = TestBed.createComponent(DetailsTickets);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
