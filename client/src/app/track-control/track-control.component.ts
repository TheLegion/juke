import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    SimpleChanges
} from '@angular/core';
import {Track} from '../model/track.model';
import {TrackState} from '../model/track-state.enum';
import {interval, Subscription} from 'rxjs';

@Component({
    selector: 'app-track-control',
    templateUrl: './track-control.component.html',
    styleUrls: ['./track-control.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class TrackControlComponent implements OnInit, OnChanges, OnDestroy {

    @Input()
    track: Track;

    @Input()
    volume: number;

    @Input()
    playDuration: number;

    @Output()
    skip: EventEmitter<void> = new EventEmitter();

    @Output()
    togglePlay: EventEmitter<void> = new EventEmitter();

    @Output()
    volumeChange: EventEmitter<number> = new EventEmitter();

    togglePlayIcon: string;

    private sub: Subscription = new Subscription();

    constructor(private detector: ChangeDetectorRef) {
    }

    ngOnInit() {
        this.sub = interval(1000).subscribe(() => {
            if (this.track.state === TrackState.Playing) {
                this.playDuration++;
                this.detector.markForCheck();
            }
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.track) {
            switch (this.track.state) {
                case TrackState.Playing:
                    this.togglePlayIcon = 'pause';
                    break;

                case TrackState.Ready:
                    this.togglePlayIcon = 'play_arrow';
                    break;

                default:
                    break;
            }
            if (changes.track.previousValue) {
                const trackChanged = changes.track.currentValue.id !== changes.track.previousValue.id;
                if (!changes.playDuration && trackChanged) {
                    this.playDuration = 0;
                }
            }
        }
    }

    ngOnDestroy(): void {
        this.sub.unsubscribe();
    }

}
