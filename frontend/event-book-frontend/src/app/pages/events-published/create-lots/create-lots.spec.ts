import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CreateLots } from './create-lots';

describe('CreateLots', () => {
  let component: CreateLots;
  let fixture: ComponentFixture<CreateLots>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateLots],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateLots);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
