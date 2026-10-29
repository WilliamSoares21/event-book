import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MenuSideBar } from './menu-side-bar';

describe('MenuSideBar', () => {
  let component: MenuSideBar;
  let fixture: ComponentFixture<MenuSideBar>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MenuSideBar],
    }).compileComponents();

    fixture = TestBed.createComponent(MenuSideBar);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
