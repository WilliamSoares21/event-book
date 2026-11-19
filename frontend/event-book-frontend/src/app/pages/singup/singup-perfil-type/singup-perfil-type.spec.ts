import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SingupPerfilTypeComponet } from './singup-perfil-type';

describe('SingupPerfilTypeComponet', () => {
  let component: SingupPerfilTypeComponet;
  let fixture: ComponentFixture<SingupPerfilTypeComponet>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SingupPerfilTypeComponet],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(SingupPerfilTypeComponet);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
