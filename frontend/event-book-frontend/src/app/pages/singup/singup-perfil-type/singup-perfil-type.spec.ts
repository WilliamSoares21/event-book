import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SingupPerfilType } from './singup-perfil-type';

describe('SingupPerfilType', () => {
  let component: SingupPerfilType;
  let fixture: ComponentFixture<SingupPerfilType>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SingupPerfilType],
    }).compileComponents();

    fixture = TestBed.createComponent(SingupPerfilType);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
