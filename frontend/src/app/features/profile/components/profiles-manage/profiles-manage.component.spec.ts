import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfilesManageComponent } from './profiles-manage.component';

describe('ProfilesManageComponent', () => {
  let component: ProfilesManageComponent;
  let fixture: ComponentFixture<ProfilesManageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfilesManageComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProfilesManageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
