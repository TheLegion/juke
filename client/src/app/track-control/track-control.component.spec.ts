import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import {TrackControlComponent} from './track-control.component';

describe('TrackControlComponent', () => {
    let component: TrackControlComponent;
    let fixture: ComponentFixture<TrackControlComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [TrackControlComponent]
        })
            .compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(TrackControlComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
