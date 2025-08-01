import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfilesAssignmentsComponent } from './profiles-assignments.component';

describe('ProfilesAssignmentsComponent', () => {
  let component: ProfilesAssignmentsComponent;
  let fixture: ComponentFixture<ProfilesAssignmentsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfilesAssignmentsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProfilesAssignmentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
